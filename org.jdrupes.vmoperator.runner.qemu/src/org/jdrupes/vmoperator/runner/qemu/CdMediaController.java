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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpChangeMedium;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpOpenTray;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpRemoveMedium;
import org.jdrupes.vmoperator.runner.qemu.events.ConfigureQemu;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorCommand;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorResult;
import org.jdrupes.vmoperator.runner.qemu.events.RunnerStateChange.State;
import org.jdrupes.vmoperator.runner.qemu.events.TrayMovedEvent;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;

/**
 * The Class CdMediaController.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class CdMediaController extends Component {

    /**
     * The Enum TrayState.
     */
    public enum TrayState {
        OPEN, CLOSED
    }

    private final Map<String, TrayState> trayState = new ConcurrentHashMap<>();
    private final Map<String, String> current = new ConcurrentHashMap<>();
    private final Map<String, String> pending = new ConcurrentHashMap<>();

    /**
     * Instantiates a new cdrom controller.
     *
     * @param componentChannel the component channel
     * @param monitor the monitor
     */
    @SuppressWarnings("PMD.AssignmentToNonFinalStatic")
    public CdMediaController(Channel componentChannel) {
        super(componentChannel);
    }

    /**
     * On configure qemu.
     *
     * @param event the event
     */
    @Handler
    @SuppressWarnings({ "PMD.AvoidLiteralsInIfCondition",
        "PMD.AvoidInstantiatingObjectsInLoops" })
    public void onConfigureQemu(ConfigureQemu event) {

        int cdCounter = 0;
        var drives = event.configuration().vm.drives;
        for (int i = 0; i < drives.length; i++) {
            if (!"ide-cd".equals(drives[i].type)) {
                continue;
            }
            var driveId = "cd" + cdCounter++;
            var newFile = Optional.ofNullable(drives[i].file).orElse("");
            if (event.state() == State.STARTING) {
                current.put(driveId, newFile);
                continue;
            }
            if (!Objects.equals(current.get(driveId), newFile)) {
                pending.put(driveId, newFile);
                if (trayState.computeIfAbsent(driveId,
                    k -> TrayState.CLOSED) == TrayState.CLOSED) {
                    fire(new MonitorCommand(new QmpOpenTray(driveId)));
                    continue;
                }
                changeMedium(driveId);
            }
        }

    }

    private void changeMedium(String driveId) {
        current.put(driveId, pending.get(driveId));
        if (pending.get(driveId).isEmpty()) {
            fire(new MonitorCommand(new QmpRemoveMedium(driveId)));
        } else {
            fire(new MonitorCommand(
                new QmpChangeMedium(driveId, pending.get(driveId))));
        }
    }

    /**
     * On monitor event.
     *
     * @param event the event
     */
    @Handler
    public void onTrayMovedEvent(TrayMovedEvent event) {
        trayState.put(event.driveId(), event.state());
        if (event.state() == TrayState.OPEN
            && pending.containsKey(event.driveId())) {
            changeMedium(event.driveId());
        }
    }

    /**
     * On monitor result.
     *
     * @param result the result
     */
    @Handler
    public void onMonitorResult(MonitorResult result) {
        if (result.executed() instanceof QmpOpenTray) {
//            if (!result.executed().equals(changeMedium.get("execute").asText())
//                    && !result.executed()
//                        .equals(removeMedium.get("execute").asText())) {
//                    return;
//                }
//                String drive = result.arguments().get("id").asText();
//                String newFile = pending.get(drive);
//                if (newFile == null) {
//                    return;
//                }
//                if (result.successful()) {
//                    fire(new MonitorCommandCompleted(CHANGE_MEDIUM, drive, newFile));
//                    pending.remove(drive);
//                }
        }
    }
}
