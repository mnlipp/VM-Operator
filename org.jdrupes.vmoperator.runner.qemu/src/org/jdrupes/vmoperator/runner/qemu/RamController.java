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
import java.math.BigInteger;
import java.util.logging.Level;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorCommand;
import static org.jdrupes.vmoperator.runner.qemu.events.MonitorCommand.Command.SET_CURRENT_RAM;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorCommandCompleted;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;

/**
 * The Class CpuController.
 */
public class RamController extends Component {

    private static ObjectMapper mapper;
    private static JsonNode setBalloon;
    private final QemuMonitor monitor;

    /**
     * Instantiates a new CPU controller.
     *
     * @param componentChannel the component channel
     * @param monitor the monitor
     */
    @SuppressWarnings("PMD.AssignmentToNonFinalStatic")
    public RamController(Channel componentChannel, QemuMonitor monitor) {
        super(componentChannel);
        if (mapper == null) {
            mapper = new ObjectMapper();
            try {
                setBalloon = mapper.readValue("{ \"execute\": \"balloon\", "
                    + "\"arguments\": " + "{ \"value\": 0 } }", JsonNode.class);
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
    public void onMonitorCommand(MonitorCommand event) {
        if (event.command() != SET_CURRENT_RAM) {
            return;
        }
        var msg = setBalloon.deepCopy();
        ((ObjectNode) msg.get("arguments")).put("value",
            (BigInteger) event.arguments()[0]);
        monitor.sendToMonitor(msg);
        fire(new MonitorCommandCompleted(event.command(), null));
    }

}
