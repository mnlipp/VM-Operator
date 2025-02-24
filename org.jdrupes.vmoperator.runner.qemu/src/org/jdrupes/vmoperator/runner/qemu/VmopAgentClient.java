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
import org.jgrapes.core.Channel;

/**
 * A component that handles the communication over the vmop agent
 * socket.
 * 
 * If the log level for this class is set to fine, the messages 
 * exchanged on the socket are logged.
 */
public class VmopAgentClient extends AgentConnector {

    /**
     * Instantiates a new VM operator agent client.
     *
     * @param componentChannel the component channel
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public VmopAgentClient(Channel componentChannel) throws IOException {
        super(componentChannel);
    }

    @Override
    protected void processInput(String line) throws IOException {
        // TODO Auto-generated method stub
    }

}
