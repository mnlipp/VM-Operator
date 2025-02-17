/*
 * VM-Operator
 * Copyright (C) 2025 Michael N. Lipp
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
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpCommand;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpGuestGetOsinfo;
import org.jdrupes.vmoperator.runner.qemu.events.GuestAgentCommand;
import org.jdrupes.vmoperator.runner.qemu.events.OsinfoEvent;
import org.jdrupes.vmoperator.runner.qemu.events.VserportChangeEvent;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
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

/**
 * A component that handles the communication over the guest agent
 * socket.
 * 
 * If the log level for this class is set to fine, the messages 
 * exchanged on the monitor socket are logged.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class GuestAgentClient extends Component {

    private static ObjectMapper mapper = new ObjectMapper();

    private EventPipeline rep;
    private Path socketPath;
    private SocketIOChannel gaChannel;
    private final Queue<QmpCommand> executing = new LinkedList<>();

    /**
     * Instantiates a new guest agent client.
     *
     * @param componentChannel the component channel
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @SuppressWarnings({ "PMD.AssignmentToNonFinalStatic",
        "PMD.ConstructorCallsOverridableMethod" })
    public GuestAgentClient(Channel componentChannel) throws IOException {
        super(componentChannel);
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
    /* default */ void configure(Path socketPath) {
        this.socketPath = socketPath;
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
    }

    /**
     * When the virtual serial port "channel0" has been opened,
     * establish the connection by opening the socket.
     *
     * @param event the event
     */
    @Handler
    public void onVserportChanged(VserportChangeEvent event) {
        if ("channel0".equals(event.id()) && event.isOpen()) {
            fire(new OpenSocketConnection(
                UnixDomainSocketAddress.of(socketPath))
                    .setAssociated(GuestAgentClient.class, this));
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
    @SuppressWarnings("resource")
    @Handler
    public void onClientConnected(ClientConnected event,
            SocketIOChannel channel) {
        event.openEvent().associated(GuestAgentClient.class).ifPresent(qm -> {
            gaChannel = channel;
            channel.setAssociated(GuestAgentClient.class, this);
            channel.setAssociated(Writer.class, new ByteBufferWriter(
                channel).nativeCharset());
            channel.setAssociated(LineCollector.class,
                new LineCollector()
                    .consumer(line -> {
                        try {
                            processGuestAgentInput(line);
                        } catch (IOException e) {
                            throw new UndeclaredThrowableException(e);
                        }
                    }));
            fire(new GuestAgentCommand(new QmpGuestGetOsinfo()));
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
        event.event().associated(GuestAgentClient.class).ifPresent(qm -> {
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
        if (channel.associated(GuestAgentClient.class).isEmpty()) {
            return;
        }
        channel.associated(LineCollector.class).ifPresent(collector -> {
            collector.feed(event);
        });
    }

    private void processGuestAgentInput(String line)
            throws IOException {
        logger.fine(() -> "guest agent(in): " + line);
        try {
            var response = mapper.readValue(line, ObjectNode.class);
            if (response.has("return") || response.has("error")) {
                QmpCommand executed = executing.poll();
                logger.fine(
                    () -> String.format("(Previous \"guest agent(in)\" is "
                        + "result from executing %s)", executed));
                if (executed instanceof QmpGuestGetOsinfo) {
                    rep.fire(new OsinfoEvent(response.get("return")));
                }
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
        channel.associated(QemuMonitor.class).ifPresent(qm -> {
            gaChannel = null;
        });
    }

    /**
     * On guest agent command.
     *
     * @param event the event
     */
    @Handler
    @SuppressWarnings({ "PMD.AvoidLiteralsInIfCondition",
        "PMD.AvoidSynchronizedStatement" })
    public void onGuestAgentCommand(GuestAgentCommand event) {
        var command = event.command();
        logger.fine(() -> "guest agent(out): " + command.toString());
        String asText;
        try {
            asText = command.asText();
        } catch (JsonProcessingException e) {
            logger.log(Level.SEVERE, e,
                () -> "Cannot serialize Json: " + e.getMessage());
            return;
        }
        synchronized (executing) {
            gaChannel.associated(Writer.class).ifPresent(writer -> {
                try {
                    executing.add(command);
                    writer.append(asText).append('\n').flush();
                } catch (IOException e) {
                    // Cannot happen, but...
                    logger.log(Level.WARNING, e, e::getMessage);
                }
            });
        }
    }
}
