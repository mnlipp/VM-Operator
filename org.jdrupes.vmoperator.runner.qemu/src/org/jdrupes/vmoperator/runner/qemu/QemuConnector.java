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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.UnixDomainSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
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
import org.jgrapes.util.events.FileChanged;
import org.jgrapes.util.events.WatchFile;

/**
 * A component that handles the communication with QEMU over a socket.
 * 
 * Derived classes should log the messages exchanged on the socket
 * if the log level is set to fine.
 */
public abstract class QemuConnector extends Component {

    @SuppressWarnings("PMD.FieldNamingConventions")
    protected static final ObjectMapper mapper = new ObjectMapper();

    private EventPipeline rep;
    private Path socketPath;
    private SocketIOChannel qemuChannel;

    /**
     * Instantiates a new QEMU connector.
     *
     * @param componentChannel the component channel
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public QemuConnector(Channel componentChannel) throws IOException {
        super(componentChannel);
    }

    /**
     * As the initial configuration of this component depends on the 
     * configuration of the {@link Runner}, it doesn't have a handler 
     * for the {@link ConfigurationUpdate} event. The values are 
     * forwarded from the {@link Runner} instead.
     *
     * @param socketPath the socket path
     */
    /* default */ void configure(Path socketPath) {
        this.socketPath = socketPath;
        logger.fine(() -> getClass().getSimpleName()
            + " configured with socketPath=" + socketPath);
    }

    /**
     * Note the runner's event processor and delete the socket.
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
     * Return the runner's event pipeline.
     *
     * @return the event pipeline
     */
    protected EventPipeline rep() {
        return rep;
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
                    .setAssociated(getClass(), this));
        }
    }

    /**
     * Check if this is from opening the agent socket and if true,
     * save the socket in the context and associate the channel with
     * the context.
     *
     * @param event the event
     * @param channel the channel
     */
    @SuppressWarnings("resource")
    @Handler
    public void onClientConnected(ClientConnected event,
            SocketIOChannel channel) {
        event.openEvent().associated(getClass()).ifPresent(qm -> {
            qemuChannel = channel;
            channel.setAssociated(getClass(), this);
            channel.setAssociated(Writer.class, new ByteBufferWriter(
                channel).nativeCharset());
            channel.setAssociated(LineCollector.class,
                new LineCollector()
                    .consumer(line -> {
                        try {
                            processInput(line);
                        } catch (IOException e) {
                            throw new UndeclaredThrowableException(e);
                        }
                    }));
            socketConnected();
        });
    }

    /**
     * Return the QEMU channel if the connection has been established.
     *
     * @return the socket IO channel
     */
    protected Optional<SocketIOChannel> qemuChannel() {
        return Optional.ofNullable(qemuChannel);
    }

    /**
     * Return the {@link Writer} for the connection if the connection
     * has been established.
     *
     * @return the optional
     */
    protected Optional<Writer> writer() {
        return qemuChannel().flatMap(c -> c.associated(Writer.class));
    }

    /**
     * Send the given command to QEMU. A newline is appended to the
     * command automatically.
     *
     * @param command the command
     * @return true, if successful
     * @throws IOException Signals that an I/O exception has occurred.
     */
    protected boolean sendCommand(String command) throws IOException {
        if (writer().isEmpty()) {
            return false;
        }
        writer().get().append(command).append('\n').flush();
        return true;
    }

    /**
     * Called when the connector has been connected to the socket.
     */
    @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
    protected void socketConnected() {
        // Default is to do nothing.
    }

    /**
     * Called when a connection attempt fails.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler
    public void onConnectError(ConnectError event, SocketIOChannel channel) {
        event.event().associated(getClass()).ifPresent(qm -> {
            rep.fire(new Stop());
        });
    }

    /**
     * Handle data from the socket connection.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler
    public void onInput(Input<?> event, SocketIOChannel channel) {
        if (channel.associated(getClass()).isEmpty()) {
            return;
        }
        channel.associated(LineCollector.class).ifPresent(collector -> {
            collector.feed(event);
        });
    }

    /**
     * Process agent input.
     *
     * @param line the line
     * @throws IOException Signals that an I/O exception has occurred.
     */
    protected abstract void processInput(String line) throws IOException;

    /**
     * On closed.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler
    public void onClosed(Closed<?> event, SocketIOChannel channel) {
        channel.associated(getClass()).ifPresent(qm -> {
            qemuChannel = null;
        });
    }
}
