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
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpCommand;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpGuestGetOsinfo;
import org.jdrupes.vmoperator.runner.qemu.events.GuestAgentCommand;
import org.jdrupes.vmoperator.runner.qemu.events.OsinfoEvent;
import org.jgrapes.core.Channel;
import org.jgrapes.core.annotation.Handler;

/**
 * A component that handles the communication with the guest agent.
 * 
 * If the log level for this class is set to fine, the messages 
 * exchanged on the monitor socket are logged.
 */
public class GuestAgentClient extends AgentConnector {

    private final Queue<QmpCommand> executing = new LinkedList<>();

    /**
     * Instantiates a new guest agent client.
     *
     * @param componentChannel the component channel
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public GuestAgentClient(Channel componentChannel) throws IOException {
        super(componentChannel);
    }

    /**
     * When the agent has connected, request the OS information.
     */
    @Override
    protected void agentConnected() {
        fire(new GuestAgentCommand(new QmpGuestGetOsinfo()));
    }

    /**
     * Process agent input.
     *
     * @param line the line
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @Override
    protected void processInput(String line) throws IOException {
        logger.fine(() -> "guest agent(in): " + line);
        try {
            var response = mapper.readValue(line, ObjectNode.class);
            if (response.has("return") || response.has("error")) {
                QmpCommand executed = executing.poll();
                logger.fine(() -> String.format("(Previous \"guest agent(in)\""
                    + " is result from executing %s)", executed));
                if (executed instanceof QmpGuestGetOsinfo) {
                    var osInfo = new OsinfoEvent(response.get("return"));
                    rep().fire(osInfo);
                }
            }
        } catch (JsonProcessingException e) {
            throw new IOException(e);
        }
    }

    /**
     * On guest agent command.
     *
     * @param event the event
     * @throws IOException 
     */
    @Handler
    @SuppressWarnings("PMD.AvoidSynchronizedStatement")
    public void onGuestAgentCommand(GuestAgentCommand event)
            throws IOException {
        if (qemuChannel() == null) {
            return;
        }
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
            if (writer().isPresent()) {
                executing.add(command);
                sendCommand(asText);
            }
        }
    }
}
