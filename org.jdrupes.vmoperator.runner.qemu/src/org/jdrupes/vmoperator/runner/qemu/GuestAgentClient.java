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
import java.time.Instant;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;
import org.jdrupes.vmoperator.runner.qemu.Constants.ProcessName;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpCommand;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpGuestGetOsinfo;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpGuestPowerdown;
import org.jdrupes.vmoperator.runner.qemu.events.ConfigureQemu;
import org.jdrupes.vmoperator.runner.qemu.events.GuestAgentCommand;
import org.jdrupes.vmoperator.runner.qemu.events.OsinfoEvent;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Components;
import org.jgrapes.core.Components.Timer;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.Stop;
import org.jgrapes.io.events.ProcessExited;

/**
 * A component that handles the communication with the guest agent.
 * 
 * If the log level for this class is set to fine, the messages 
 * exchanged on the monitor socket are logged.
 */
public class GuestAgentClient extends AgentConnector {

    private boolean connected;
    private Instant powerdownStartedAt;
    private int powerdownTimeout;
    private Timer powerdownTimer;
    private final Queue<QmpCommand> executing = new LinkedList<>();
    private Stop suspendedStop;

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
        logger.fine(() -> "Guest agent connected");
        connected = true;
        rep().fire(new GuestAgentCommand(new QmpGuestGetOsinfo()));
    }

    @Override
    protected void agentDisconnected() {
        logger.fine(() -> "Guest agent disconnected");
        connected = false;
    }

    /**
     * Process agent input.
     *
     * @param line the line
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @Override
    protected void processInput(String line) throws IOException {
        logger.finer(() -> "guest agent(in): " + line);
        try {
            var response = mapper.readValue(line, ObjectNode.class);
            if (response.has("return") || response.has("error")) {
                QmpCommand executed = executing.poll();
                logger.finer(() -> String.format("(Previous \"guest agent(in)\""
                    + " is result from executing %s)", executed));
                if (executed instanceof QmpGuestGetOsinfo) {
                    var osInfo = new OsinfoEvent(response.get("return"));
                    logger.fine(() -> "Guest agent triggers: " + osInfo);
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
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @Handler
    @SuppressWarnings({ "PMD.AvoidSynchronizedStatement",
        "PMD.AvoidDuplicateLiterals" })
    public void onGuestAgentCommand(GuestAgentCommand event)
            throws IOException {
        if (qemuChannel() == null) {
            return;
        }
        var command = event.command();
        logger.fine(() -> "Guest handles: " + event);
        String asText;
        try {
            asText = command.asText();
            logger.finer(() -> "guest agent(out): " + asText);
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
    @Handler(priority = 200)
    @SuppressWarnings("PMD.AvoidSynchronizedStatement")
    public void onStop(Stop event) {
        if (!connected) {
            logger.fine(() -> "No guest agent connection,"
                + " cannot send shutdown command");
            return;
        }

        // We have a connection to the guest agent attempt shutdown.
        powerdownStartedAt = event.associated(Instant.class).orElseGet(() -> {
            var now = Instant.now();
            event.setAssociated(Instant.class, now);
            return now;
        });
        var waitUntil = powerdownStartedAt.plusSeconds(powerdownTimeout);
        if (waitUntil.isBefore(Instant.now())) {
            return;
        }
        event.suspendHandling();
        suspendedStop = event;
        logger.fine(() -> "Attempting shutdown through guest agent,"
            + " waiting for termination until " + waitUntil);
        powerdownTimer = Components.schedule(t -> {
            logger.fine(() -> "Powerdown timeout reached.");
            synchronized (this) {
                powerdownTimer = null;
                if (suspendedStop != null) {
                    suspendedStop.resumeHandling();
                    suspendedStop = null;
                }
            }
        }, waitUntil);
        rep().fire(new GuestAgentCommand(new QmpGuestPowerdown()));
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
                if (powerdownTimer != null) {
                    powerdownTimer
                        .reschedule(powerdownStartedAt.plusSeconds(newTimeout));
                }

            }
        }
    }
}
