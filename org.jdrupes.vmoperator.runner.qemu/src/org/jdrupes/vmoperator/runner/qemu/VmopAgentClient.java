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
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.jdrupes.vmoperator.runner.qemu.events.VmopAgentConnected;
import org.jdrupes.vmoperator.runner.qemu.events.VmopAgentLogIn;
import org.jdrupes.vmoperator.runner.qemu.events.VmopAgentLogOut;
import org.jdrupes.vmoperator.runner.qemu.events.VmopAgentLoggedIn;
import org.jdrupes.vmoperator.runner.qemu.events.VmopAgentLoggedOut;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Event;
import org.jgrapes.core.annotation.Handler;

/**
 * A component that handles the communication over the vmop agent
 * socket.
 * 
 * If the log level for this class is set to fine, the messages 
 * exchanged on the socket are logged.
 */
public class VmopAgentClient extends AgentConnector {

    private final Deque<Event<?>> executing = new ConcurrentLinkedDeque<>();

    /**
     * Instantiates a new VM operator agent client.
     *
     * @param componentChannel the component channel
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public VmopAgentClient(Channel componentChannel) throws IOException {
        super(componentChannel);
    }

    /**
     * On vmop agent login.
     *
     * @param event the event
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @Handler
    public void onVmopAgentLogIn(VmopAgentLogIn event) throws IOException {
        if (writer().isPresent()) {
            logger.fine(() -> "Vmop agent handles:" + event);
            executing.add(event);
            logger.finer(() -> "vmop agent(out): login " + event.user());
            sendCommand("login " + event.user());
        } else {
            logger
                .warning(() -> "No vmop agent connection for sending " + event);
        }
    }

    /**
     * On vmop agent logout.
     *
     * @param event the event
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @Handler
    public void onVmopAgentLogout(VmopAgentLogOut event) throws IOException {
        if (writer().isPresent()) {
            logger.fine(() -> "Vmop agent handles:" + event);
            executing.add(event);
            logger.finer(() -> "vmop agent(out): logout");
            sendCommand("logout");
        }
    }

    @Override
    @SuppressWarnings({ "PMD.UnnecessaryReturn",
        "PMD.AvoidLiteralsInIfCondition" })
    protected void processInput(String line) throws IOException {
        logger.finer(() -> "vmop agent(in): " + line);

        // Check validity
        if (line.isEmpty() || !Character.isDigit(line.charAt(0))) {
            logger.warning(() -> "Illegal vmop agent response: " + line);
            return;
        }

        // Check positive responses
        if (line.startsWith("220 ")) {
            var evt = new VmopAgentConnected();
            logger.fine(() -> "Vmop agent triggers " + evt);
            rep().fire(evt);
            return;
        }
        if (line.startsWith("201 ")) {
            Event<?> cmd = executing.pop();
            if (cmd instanceof VmopAgentLogIn login) {
                var evt = new VmopAgentLoggedIn(login);
                logger.fine(() -> "Vmop agent triggers " + evt);
                rep().fire(evt);
            } else {
                logger.severe(() -> "Response " + line
                    + " does not match executing command " + cmd);
            }
            return;
        }
        if (line.startsWith("202 ")) {
            Event<?> cmd = executing.pop();
            if (cmd instanceof VmopAgentLogOut logout) {
                var evt = new VmopAgentLoggedOut(logout);
                logger.fine(() -> "Vmop agent triggers " + evt);
                rep().fire(evt);
            } else {
                logger.severe(() -> "Response " + line
                    + "does not match executing command " + cmd);
            }
            return;
        }

        // Ignore unhandled continuations
        if (line.charAt(0) == '1') {
            return;
        }

        // Error
        logger.warning(() -> "Error response from vmop agent: " + line);
        executing.pop();
    }

}
