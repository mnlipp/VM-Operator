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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorCommand;
import static org.jdrupes.vmoperator.runner.qemu.events.MonitorCommand.Command.SET_CURRENT_CPUS;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorCommandCompleted;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorResult;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;

/**
 * The Class CpuController.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class CpuController extends Component {

    private static ObjectMapper mapper;
    private static JsonNode queryHotpluggableCpus;

    private final QemuMonitor monitor;
    private Integer desiredCpus;

    /**
     * Instantiates a new CPU controller.
     *
     * @param componentChannel the component channel
     * @param monitor the monitor
     */
    @SuppressWarnings("PMD.AssignmentToNonFinalStatic")
    public CpuController(Channel componentChannel, QemuMonitor monitor) {
        super(componentChannel);
        if (mapper == null) {
            mapper = new ObjectMapper();
            try {
                queryHotpluggableCpus = mapper.readValue(
                    "{\"execute\":\"query-hotpluggable-cpus\",\"arguments\":{}}",
                    JsonNode.class);
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
        if (event.command() != SET_CURRENT_CPUS) {
            return;
        }
        desiredCpus = (Integer) event.arguments()[0];
        monitor.sendToMonitor(queryHotpluggableCpus);
    }

    /**
     * On monitor result.
     *
     * @param result the result
     */
    @Handler
    public void onMonitorResult(MonitorResult result) {
        if (!result.executed()
            .equals(queryHotpluggableCpus.get("execute").asText())
            || desiredCpus == null) {
            return;
        }

        // Sort
        List<ObjectNode> used = new ArrayList<>();
        List<ObjectNode> unused = new ArrayList<>();
        for (var itr = result.returned().iterator(); itr.hasNext();) {
            ObjectNode cpu = (ObjectNode) itr.next();
            if (cpu.has("qom-path")) {
                used.add(cpu);
            } else {
                unused.add(cpu);
            }
        }

        // Process
        int diff = used.size() - desiredCpus;
        diff = addCpus(used, unused, diff);
        diff = deleteCpus(used, diff);
        fire(new MonitorCommandCompleted(SET_CURRENT_CPUS, desiredCpus + diff));
        desiredCpus = null;
    }

    private int addCpus(List<ObjectNode> used, List<ObjectNode> unused,
            int diff) {
        Set<String> usedIds = new HashSet<>();
        for (var cpu : used) {
            String qomPath = cpu.get("qom-path").asText();
            if (qomPath.startsWith("/machine/peripheral/cpu-")) {
                usedIds
                    .add(qomPath.substring(qomPath.lastIndexOf('/') + 1));
            }
        }
        int nextId = 1;
        while (diff < 0 && !unused.isEmpty()) {
            ObjectNode cmd = mapper.createObjectNode();
            cmd.put("execute", "device_add");
            ObjectNode args = mapper.createObjectNode();
            cmd.set("arguments", args);
            args.setAll((ObjectNode) (unused.get(0).get("props").deepCopy()));
            args.set("driver", unused.get(0).get("type"));
            String id;
            do {
                id = "cpu-" + nextId++;
            } while (usedIds.contains(id));
            args.put("id", id);
            monitor.sendToMonitor(cmd);
            unused.remove(0);
            diff += 1;
        }
        return diff;
    }

    private int deleteCpus(List<ObjectNode> used, int diff) {
        while (diff > 0 && !used.isEmpty()) {
            ObjectNode cpu = used.remove(0);
            String qomPath = cpu.get("qom-path").asText();
            if (!qomPath.startsWith("/machine/peripheral/cpu-")) {
                continue;
            }
            String id = qomPath.substring(qomPath.lastIndexOf('/') + 1);
            ObjectNode cmd = mapper.createObjectNode();
            cmd.put("execute", "device_del");
            ObjectNode args = mapper.createObjectNode();
            cmd.set("arguments", args);
            args.put("id", id);
            monitor.sendToMonitor(cmd);
            diff -= 1;
        }
        return diff;
    }
}
