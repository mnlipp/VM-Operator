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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.UnixDomainSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpCapabilities;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpCommand;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpPowerdown;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorCommand;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorEvent;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorReady;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorResult;
import org.jdrupes.vmoperator.runner.qemu.events.PowerdownEvent;
import org.jdrupes.vmoperator.runner.qemu.events.RunnerConfigurationUpdate;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.Components;
import org.jgrapes.core.Components.Timer;
import org.jgrapes.core.EventPipeline;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.Start;
import org.jgrapes.core.events.Stop;
import org.jgrapes.io.events.Closed;
import org.jgrapes.io.events.ConnectError;
import org.jgrapes.io.events.Input;
import org.jgrapes.io.events.OpenSocketConnection;
import org.jgrapes.io.util.ByteBufferWriter;
import org.jgrapes.io.util.LineCollector;
import org.jgrapes.net.SocketIOChannel;
import org.jgrapes.net.events.ClientConnected;
import org.jgrapes.util.events.ConfigurationUpdate;
import org.jgrapes.util.events.FileChanged;
import org.jgrapes.util.events.WatchFile;

/**
 * A component that handles the communication over the Qemu monitor
 * socket.
 * 
 * If the log level for this class is set to fine, the messages 
 * exchanged on the monitor socket are logged.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class QemuMonitor extends Component {

    private static ObjectMapper mapper = new ObjectMapper();

    private EventPipeline rep;
    private Path socketPath;
    private int powerdownTimeout;
    private SocketIOChannel monitorChannel;
    private final Queue<QmpCommand> executing = new LinkedList<>();
    private Instant powerdownStartedAt;
    private Stop suspendedStop;
    private Timer powerdownTimer;
    private boolean powerdownConfirmed;

    /**
     * Instantiates a new qemu monitor.
     *
     * @param componentChannel the component channel
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @SuppressWarnings("PMD.AssignmentToNonFinalStatic")
    public QemuMonitor(Channel componentChannel) throws IOException {
        super(componentChannel);
        attach(new RamController(channel()));
        attach(new CpuController(channel()));
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
        this.socketPath = socketPath;
        this.powerdownTimeout = powerdownTimeout;
    }

    /**
     * Handle the start event.
     *
     * @param event the event
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @Handler
    public void onStart(Start event) throws IOException {
        rep = event.associated(EventPipeline.class).get();
        if (socketPath == null) {
            return;
        }
        Files.deleteIfExists(socketPath);
        fire(new WatchFile(socketPath));
    }

    /**
     * Watch for the creation of the swtpm socket and start the
     * qemu process if it has been created.
     *
     * @param event the event
     */
    @Handler
    public void onFileChanged(FileChanged event) {
        if (event.change() == FileChanged.Kind.CREATED
            && event.path().equals(socketPath)) {
            // qemu running, open socket
            fire(new OpenSocketConnection(
                UnixDomainSocketAddress.of(socketPath))
                    .setAssociated(QemuMonitor.class, this));
        }
    }

    /**
     * Check if this is from opening the monitor socket and if true,
     * save the socket in the context and associate the channel with
     * the context. Then send the initial message to the socket.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler
    public void onClientConnected(ClientConnected event,
            SocketIOChannel channel) {
        event.openEvent().associated(QemuMonitor.class).ifPresent(qm -> {
            monitorChannel = channel;
            channel.setAssociated(QemuMonitor.class, this);
            channel.setAssociated(Writer.class, new ByteBufferWriter(
                channel).nativeCharset());
            channel.setAssociated(LineCollector.class,
                new LineCollector()
                    .consumer(line -> {
                        try {
                            processMonitorInput(line);
                        } catch (IOException e) {
                            throw new UndeclaredThrowableException(e);
                        }
                    }));
            fire(new MonitorCommand(new QmpCapabilities()));
        });
    }

    /**
     * Called when a connection attempt fails.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler
    public void onConnectError(ConnectError event, SocketIOChannel channel) {
        event.event().associated(QemuMonitor.class).ifPresent(qm -> {
            rep.fire(new Stop());
        });
    }

    /**
     * Handle data from qemu monitor connection.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler
    public void onInput(Input<?> event, SocketIOChannel channel) {
        if (channel.associated(QemuMonitor.class).isEmpty()) {
            return;
        }
        channel.associated(LineCollector.class).ifPresent(collector -> {
            collector.feed(event);
        });
    }

    private void processMonitorInput(String line)
            throws IOException {
        logger.fine(() -> "monitor(in): " + line);
        try {
            var response = mapper.readValue(line, ObjectNode.class);
            if (response.has("QMP")) {
                rep.fire(new MonitorReady());
                return;
            }
            if (response.has("return") || response.has("error")) {
                QmpCommand executed = executing.poll();
                logger.fine(
                    () -> String.format("(Previous \"monitor(in)\" is result "
                        + "from executing %s)", executed));
                rep.fire(MonitorResult.from(executed, response));
                return;
            }
            if (response.has("event")) {
                MonitorEvent.from(response).ifPresent(rep::fire);
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
        channel.associated(QemuMonitor.class).ifPresent(qm -> {
            monitorChannel = null;
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
     */
    @Handler
    public void onExecQmpCommand(MonitorCommand event) {
        var command = event.command();
        String asText;
        try {
            asText = mapper.writeValueAsString(command.toJson());
        } catch (JsonProcessingException e) {
            logger.log(Level.SEVERE, e,
                () -> "Cannot serialize Json: " + e.getMessage());
            return;
        }
        logger.fine(() -> "monitor(out): " + asText);
        synchronized (executing) {
            monitorChannel.associated(Writer.class).ifPresent(writer -> {
                try {
                    executing.add(command);
                    writer.append(asText).append('\n').flush();
                } catch (IOException e) {
                    // Cannot happen, but...
                    logger.log(Level.WARNING, e, () -> e.getMessage());
                }
            });
        }
    }

    /**
     * Shutdown the VM.
     *
     * @param event the event
     */
    @Handler(priority = 100)
    public void onStop(Stop event) {
        if (monitorChannel != null) {
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
    public void onConfigureQemu(RunnerConfigurationUpdate event) {
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
