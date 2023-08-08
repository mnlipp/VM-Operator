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

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpAddCpu;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpDelCpu;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpQueryHotpluggableCpus;
import org.jdrupes.vmoperator.runner.qemu.events.CpuAdded;
import org.jdrupes.vmoperator.runner.qemu.events.CpuDeleted;
import org.jdrupes.vmoperator.runner.qemu.events.HotpluggableCpuStatus;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorCommand;
import org.jdrupes.vmoperator.runner.qemu.events.RunnerConfigurationUpdate;
import org.jdrupes.vmoperator.runner.qemu.events.RunnerStateChange.State;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;

/**
 * The Class CpuController.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class CpuController extends Component {

    private Integer currentCpus;
    private Integer desiredCpus;
    private RunnerConfigurationUpdate suspendedConfigure;

    /**
     * Instantiates a new CPU controller.
     *
     * @param componentChannel the component channel
     */
    public CpuController(Channel componentChannel) {
        super(componentChannel);
    }

    /**
     * On configure qemu.
     *
     * @param event the event
     */
    @Handler
    public void onConfigureQemu(RunnerConfigurationUpdate event) {
        if (event.state() == State.TERMINATING) {
            return;
        }
        Optional.ofNullable(event.configuration().vm.currentCpus)
            .ifPresent(cpus -> {
                if (desiredCpus != null && desiredCpus.equals(cpus)) {
                    return;
                }
                event.suspendHandling();
                suspendedConfigure = event;
                desiredCpus = cpus;
                fire(new MonitorCommand(new QmpQueryHotpluggableCpus()));
            });
    }

    /**
     * On monitor result.
     *
     * @param result the result
     */
    @Handler
    public void onHotpluggableCpuStatus(HotpluggableCpuStatus result) {
        // Sort
        List<ObjectNode> used = new ArrayList<>();
        List<ObjectNode> unused = new ArrayList<>();
        for (var itr = result.values().iterator(); itr.hasNext();) {
            ObjectNode cpu = (ObjectNode) itr.next();
            if (cpu.has("qom-path")) {
                used.add(cpu);
            } else {
                unused.add(cpu);
            }
        }
        currentCpus = used.size();
        if (desiredCpus == null) {
            return;
        }
        // Process
        int diff = used.size() - desiredCpus;
        diff = addCpus(used, unused, diff);
        deleteCpus(used, diff);
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
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
            String id;
            do {
                id = "cpu-" + nextId++;
            } while (usedIds.contains(id));
            fire(new MonitorCommand(new QmpAddCpu(unused.get(0), id)));
            unused.remove(0);
            diff += 1;
        }
        return diff;
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private int deleteCpus(List<ObjectNode> used, int diff) {
        while (diff > 0 && !used.isEmpty()) {
            ObjectNode cpu = used.remove(0);
            String qomPath = cpu.get("qom-path").asText();
            if (!qomPath.startsWith("/machine/peripheral/cpu-")) {
                continue;
            }
            String id = qomPath.substring(qomPath.lastIndexOf('/') + 1);
            fire(new MonitorCommand(new QmpDelCpu(id)));
            diff -= 1;
        }
        return diff;
    }

    /**
     * On cpu added.
     *
     * @param event the event
     */
    @Handler
    public void onCpuAdded(CpuAdded event) {
        currentCpus += 1;
        checkCpus();
    }

    /**
     * On cpu deleted.
     *
     * @param event the event
     */
    @Handler
    public void onCpuDeleted(CpuDeleted event) {
        currentCpus -= 1;
        checkCpus();
    }

    private void checkCpus() {
        if (suspendedConfigure != null && desiredCpus != null
            && currentCpus == desiredCpus.intValue()) {
            suspendedConfigure.resumeHandling();
            suspendedConfigure = null;
        }
    }
}
