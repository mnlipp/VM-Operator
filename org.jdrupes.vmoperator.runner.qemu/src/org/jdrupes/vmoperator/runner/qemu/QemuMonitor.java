/*
 * VM-Operator
 * Copyright (C) 2023 Michael N. Lipp
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.jdrupes.vmoperator.runner.qemu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpCapabilities;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpCommand;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpPowerdown;
import org.jdrupes.vmoperator.runner.qemu.events.ConfigureQemu;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorCommand;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorEvent;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorReady;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorResult;
import org.jdrupes.vmoperator.runner.qemu.events.PowerdownEvent;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Components;
import org.jgrapes.core.Components.Timer;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.Stop;
import org.jgrapes.io.events.Closed;
import org.jgrapes.net.SocketIOChannel;
import org.jgrapes.util.events.ConfigurationUpdate;

/**
 * A component that handles the communication over the Qemu monitor
 * socket.
 * 
 * If the log level for this class is set to fine, the messages 
 * exchanged on the monitor socket are logged.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class QemuMonitor extends QemuConnector {

    private int powerdownTimeout;
    private final Queue<QmpCommand> executing = new LinkedList<>();
    private Instant powerdownStartedAt;
    private Stop suspendedStop;
    private Timer powerdownTimer;
    private boolean powerdownConfirmed;

    /**
     * Instantiates a new QEMU monitor.
     *
     * @param componentChannel the component channel
     * @param configDir the config dir
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @SuppressWarnings({ "PMD.AssignmentToNonFinalStatic",
        "PMD.ConstructorCallsOverridableMethod" })
    public QemuMonitor(Channel componentChannel, Path configDir)
            throws IOException {
        super(componentChannel);
        attach(new RamController(channel()));
        attach(new CpuController(channel()));
        attach(new DisplayController(channel(), configDir));
        attach(new CdMediaController(channel()));
    }

    /**
     * As the initial configuration of this component depends on the 
     * configuration of the {@link Runner}, it doesn't have a handler 
     * for the {@link ConfigurationUpdate} event. The values are 
     * forwarded from the {@link Runner} instead.
     *
     * @param socketPath the socket path
     * @param powerdownTimeout 
     */
    /* default */ void configure(Path socketPath, int powerdownTimeout) {
        super.configure(socketPath);
        this.powerdownTimeout = powerdownTimeout;
    }

    /**
     * When the socket is connected, send the capabilities command.
     */
    @Override
    protected void socketConnected() {
        fire(new MonitorCommand(new QmpCapabilities()));
    }

    @Override
    protected void processInput(String line)
            throws IOException {
        logger.fine(() -> "monitor(in): " + line);
        try {
            var response = mapper.readValue(line, ObjectNode.class);
            if (response.has("QMP")) {
                rep().fire(new MonitorReady());
                return;
            }
            if (response.has("return") || response.has("error")) {
                QmpCommand executed = executing.poll();
                logger.fine(
                    () -> String.format("(Previous \"monitor(in)\" is result "
                        + "from executing %s)", executed));
                rep().fire(MonitorResult.from(executed, response));
                return;
            }
            if (response.has("event")) {
                MonitorEvent.from(response).ifPresent(rep()::fire);
            }
        } catch (JsonProcessingException e) {
            throw new IOException(e);
        }
    }

    /**
     * On closed.
     *
     * @param event the event
     */
    @Handler
    @SuppressWarnings({ "PMD.AvoidSynchronizedStatement",
        "PMD.AvoidDuplicateLiterals" })
    public void onClosed(Closed<?> event, SocketIOChannel channel) {
        super.onClosed(event, channel);
        channel.associated(QemuMonitor.class).ifPresent(qm -> {
            synchronized (this) {
                if (powerdownTimer != null) {
                    powerdownTimer.cancel();
                }
                if (suspendedStop != null) {
                    suspendedStop.resumeHandling();
                    suspendedStop = null;
                }
            }
        });
    }

    /**
     * On monitor command.
     *
     * @param event the event
     * @throws IOException 
     */
    @Handler
    @SuppressWarnings({ "PMD.AvoidLiteralsInIfCondition",
        "PMD.AvoidSynchronizedStatement" })
    public void onExecQmpCommand(MonitorCommand event) throws IOException {
        var command = event.command();
        logger.fine(() -> "monitor(out): " + command.toString());
        String asText;
        try {
            asText = command.asText();
        } catch (JsonProcessingException e) {
            logger.log(Level.SEVERE, e,
                () -> "Cannot serialize Json: " + e.getMessage());
            return;
        }
        synchronized (executing) {
            if (writer().isPresent()) {
                executing.add(command);
                sendCommand(asText);
            }
        }
    }

    /**
     * Shutdown the VM.
     *
     * @param event the event
     */
    @Handler(priority = 100)
    @SuppressWarnings("PMD.AvoidSynchronizedStatement")
    public void onStop(Stop event) {
        if (qemuChannel() != null) {
            // We have a connection to Qemu, attempt ACPI shutdown.
            event.suspendHandling();
            suspendedStop = event;

            // Attempt powerdown command. If not confirmed, assume
            // "hanging" qemu process.
            powerdownTimer = Components.schedule(t -> {
                // Powerdown not confirmed
                logger.fine(() -> "QMP powerdown command has not effect.");
                synchronized (this) {
                    powerdownTimer = null;
                    if (suspendedStop != null) {
                        suspendedStop.resumeHandling();
                        suspendedStop = null;
                    }
                }
            }, Duration.ofSeconds(1));
            logger.fine(() -> "Attempting QMP powerdown.");
            powerdownStartedAt = Instant.now();
            fire(new MonitorCommand(new QmpPowerdown()));
        }
    }

    /**
     * On powerdown event.
     *
     * @param event the event
     */
    @Handler
    @SuppressWarnings("PMD.AvoidSynchronizedStatement")
    public void onPowerdownEvent(PowerdownEvent event) {
        synchronized (this) {
            // Cancel confirmation timeout
            if (powerdownTimer != null) {
                powerdownTimer.cancel();
            }

            // (Re-)schedule timer as fallback
            logger.fine(() -> "QMP powerdown confirmed, waiting...");
            powerdownTimer = Components.schedule(t -> {
                logger.fine(() -> "Powerdown timeout reached.");
                synchronized (this) {
                    if (suspendedStop != null) {
                        suspendedStop.resumeHandling();
                        suspendedStop = null;
                    }
                }
            }, powerdownStartedAt.plusSeconds(powerdownTimeout));
            powerdownConfirmed = true;
        }
    }

    /**
     * On configure qemu.
     *
     * @param event the event
     */
    @Handler
    @SuppressWarnings("PMD.AvoidSynchronizedStatement")
    public void onConfigureQemu(ConfigureQemu event) {
        int newTimeout = event.configuration().vm.powerdownTimeout;
        if (powerdownTimeout != newTimeout) {
            powerdownTimeout = newTimeout;
            synchronized (this) {
                if (powerdownTimer != null && powerdownConfirmed) {
                    powerdownTimer
                        .reschedule(powerdownStartedAt.plusSeconds(newTimeout));
                }

            }
        }
    }

}
