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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.jdrupes.vmoperator.runner.qemu.events.VserportChangeEvent;
import org.jgrapes.core.Channel;
import org.jgrapes.core.annotation.Handler;

/**
 * A component that handles the communication with an agent
 * running in the VM.
 * 
 * If the log level for this class is set to fine, the messages 
 * exchanged on the socket are logged.
 */
public abstract class AgentConnector extends QemuConnector {

    protected String channelId;

    /**
     * Instantiates a new agent connector.
     *
     * @param componentChannel the component channel
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public AgentConnector(Channel componentChannel) throws IOException {
        super(componentChannel);
    }

    /**
     * Extracts the channel id and the socket path from the QEMU
     * command line.
     *
     * @param command the command
     * @param chardev the chardev
     */
    @SuppressWarnings("PMD.CognitiveComplexity")
    protected void configureConnection(List<String> command, String chardev) {
        Path socketPath = null;
        for (var arg : command) {
            if (arg.startsWith("virtserialport,")
                && arg.contains("chardev=" + chardev)) {
                for (var prop : arg.split(",")) {
                    if (prop.startsWith("id=")) {
                        channelId = prop.substring(3);
                    }
                }
            }
            if (arg.startsWith("socket,")
                && arg.contains("id=" + chardev)) {
                for (var prop : arg.split(",")) {
                    if (prop.startsWith("path=")) {
                        socketPath = Path.of(prop.substring(5));
                    }
                }
            }
        }
        if (channelId == null || socketPath == null) {
            logger.warning(() -> "Definition of chardev " + chardev
                + " missing in runner template.");
            return;
        }
        logger.fine(() -> getClass().getSimpleName() + " configured with"
            + " channelId=" + channelId);
        super.configure(socketPath);
    }

    /**
     * When the virtual serial port with the configured channel id has
     * been opened call {@link #agentConnected()}.
     *
     * @param event the event
     */
    @Handler
    public void onVserportChanged(VserportChangeEvent event) {
        if (event.id().equals(channelId) && event.isOpen()) {
            agentConnected();
        }
    }

    /**
     * Called when the agent in the VM opens the connection. The
     * default implementation does nothing.
     */
    @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
    protected void agentConnected() {
        // Default is to do nothing.
    }
}
