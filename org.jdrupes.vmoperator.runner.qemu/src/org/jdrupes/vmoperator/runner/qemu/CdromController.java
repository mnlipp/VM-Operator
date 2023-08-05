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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.logging.Level;
import org.jdrupes.vmoperator.runner.qemu.events.ChangeMediumCommand;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorCommand.Command;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorCommandCompleted;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;

// TODO: Auto-generated Javadoc
/**
 * The Class CdromController.
 */
public class CdromController extends Component {

    private static ObjectMapper mapper;
    private static JsonNode openTray;
    private static JsonNode removeMedium;
    private static JsonNode changeMedium;
    private final QemuMonitor monitor;

    /**
     * Instantiates a new cdrom controller.
     *
     * @param componentChannel the component channel
     * @param monitor the monitor
     */
    @SuppressWarnings("PMD.AssignmentToNonFinalStatic")
    public CdromController(Channel componentChannel, QemuMonitor monitor) {
        super(componentChannel);
        if (mapper == null) {
            mapper = new ObjectMapper();
            try {
                openTray = mapper.readValue("{ \"execute\": "
                    + "\"blockdev-open-tray\",\"arguments\": {"
                    + "\"id\": \"\" } }", JsonNode.class);
                removeMedium = mapper.readValue("{ \"execute\": "
                    + "\"blockdev-remove-medium\",\"arguments\": {"
                    + "\"id\": \"\" } }", JsonNode.class);
                changeMedium = mapper.readValue("{ \"execute\": "
                    + "\"blockdev-change-medium\",\"arguments\": {"
                    + "\"id\": \"\",\"filename\": \"\","
                    + "\"format\": \"raw\",\"read-only-mode\": "
                    + "\"read-only\" } }", JsonNode.class);
            } catch (IOException e) {
                logger.log(Level.SEVERE, e,
                    () -> "Cannot initialize class: " + e.getMessage());
            }
        }
        this.monitor = monitor;
    }

    /**
     * On monitor command.
     *
     * @param event the event
     */
    @Handler
    @SuppressWarnings("PMD.AvoidDuplicateLiterals")
    public void onChangeMediumCommand(ChangeMediumCommand event) {
        if (event.command() != Command.CHANGE_MEDIUM) {
            return;
        }
        if (event.file() == null || event.file().isEmpty()) {
            var msg = openTray.deepCopy();
            ((ObjectNode) msg.get("arguments")).put("id", event.id());
            monitor.sendToMonitor(msg);
            msg = removeMedium.deepCopy();
            ((ObjectNode) msg.get("arguments")).put("id", event.id());
            monitor.sendToMonitor(msg);
            fire(new MonitorCommandCompleted(event.command(), null));
            return;
        }
        var msg = changeMedium.deepCopy();
        ((ObjectNode) msg.get("arguments")).put("id", event.id());
        ((ObjectNode) msg.get("arguments")).put("filename", event.file());
        monitor.sendToMonitor(msg);
        fire(new MonitorCommandCompleted(event.command(), null));
    }

}
