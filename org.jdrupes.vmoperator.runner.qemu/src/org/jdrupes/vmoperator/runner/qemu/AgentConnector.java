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
import org.jdrupes.vmoperator.runner.qemu.events.VserportChangeEvent;
import org.jgrapes.core.Channel;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.util.events.ConfigurationUpdate;

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
     * As the initial configuration of this component depends on the 
     * configuration of the {@link Runner}, it doesn't have a handler 
     * for the {@link ConfigurationUpdate} event. The values are 
     * forwarded from the {@link Runner} instead.
     *
     * @param channelId the channel id
     * @param socketPath the socket path
     */
    /* default */ void configure(String channelId, Path socketPath) {
        super.configure(socketPath);
        this.channelId = channelId;
        logger.fine(() -> getClass().getSimpleName() + " configured with"
            + " channelId=" + channelId);
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
