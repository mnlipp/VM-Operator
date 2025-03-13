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
import org.jdrupes.vmoperator.runner.qemu.Constants.ProcessName;
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
import org.jgrapes.io.events.ProcessExited;
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
    private boolean monitorReady;

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
        rep().fire(new MonitorCommand(new QmpCapabilities()));
    }

    @Override
    protected void processInput(String line)
            throws IOException {
        logger.fine(() -> "monitor(in): " + line);
        try {
            var response = mapper.readValue(line, ObjectNode.class);
            if (response.has("QMP")) {
                monitorReady = true;
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
    public void onClosed(Closed<?> event, SocketIOChannel channel) {
        channel.associated(this, getClass()).ifPresent(qm -> {
            super.onClosed(event, channel);
            logger.finer(() -> "QMP socket closed.");
            monitorReady = false;
        });
    }

    /**
     * On monitor command.
     *
     * @param event the event
     * @throws IOException 
     */
    @Handler
    @SuppressWarnings({ "PMD.AvoidSynchronizedStatement",
        "PMD.AvoidDuplicateLiterals" })
    public void onMonitorCommand(MonitorCommand event) throws IOException {
        // Check prerequisites
        if (!monitorReady && !(event.command() instanceof QmpCapabilities)) {
            logger.severe(() -> "Premature monitor command (not ready): "
                + event.command());
            rep().fire(new Stop());
            return;
        }

        // Send the command
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
        if (!monitorReady) {
            logger.fine(() -> "No QMP connection,"
                + " cannot send powerdown command");
            return;
        }

        // We have a connection to Qemu, attempt ACPI shutdown.
        event.suspendHandling();
        suspendedStop = event;

        // Attempt powerdown command. If not confirmed, assume
        // "hanging" qemu process.
        powerdownTimer = Components.schedule(t -> {
            logger.fine(() -> "QMP powerdown command not confirmed");
            synchronized (this) {
                powerdownTimer = null;
                if (suspendedStop != null) {
                    suspendedStop.resumeHandling();
                    suspendedStop = null;
                }
            }
        }, Duration.ofSeconds(5));
        logger.fine(() -> "Attempting QMP powerdown.");
        powerdownStartedAt = Instant.now();
        fire(new MonitorCommand(new QmpPowerdown()));
    }

    /**
     * When the powerdown event is confirmed, wait for termination
     * or timeout. Termination is detected by the qemu process exiting
     * (see {@link #onProcessExited(ProcessExited)}).
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
            var waitUntil = powerdownStartedAt.plusSeconds(powerdownTimeout);
            logger.fine(() -> "QMP powerdown confirmed, waiting for"
                + " termination until " + waitUntil);
            powerdownTimer = Components.schedule(t -> {
                logger.fine(() -> "Powerdown timeout reached.");
                synchronized (this) {
                    if (suspendedStop != null) {
                        suspendedStop.resumeHandling();
                        suspendedStop = null;
                    }
                }
            }, waitUntil);
            powerdownConfirmed = true;
        }
    }

    /**
     * On process exited.
     *
     * @param event the event
     */
    @Handler
    @SuppressWarnings("PMD.AvoidSynchronizedStatement")
    public void onProcessExited(ProcessExited event) {
        if (!event.startedBy().associated(CommandDefinition.class)
            .map(cd -> ProcessName.QEMU.equals(cd.name())).orElse(false)) {
            return;
        }
        synchronized (this) {
            if (powerdownTimer != null) {
                powerdownTimer.cancel();
            }
            if (suspendedStop != null) {
                suspendedStop.resumeHandling();
                suspendedStop = null;
            }
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
