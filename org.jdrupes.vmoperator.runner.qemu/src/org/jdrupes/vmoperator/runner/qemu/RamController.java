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

import java.math.BigInteger;
import java.util.Optional;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpSetBalloon;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorCommand;
import org.jdrupes.vmoperator.runner.qemu.events.RunnerConfigurationUpdate;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;

/**
 * The Class RamController.
 */
public class RamController extends Component {

    private BigInteger currentRam;

    /**
     * Instantiates a new CPU controller.
     *
     * @param componentChannel the component channel
     */
    @SuppressWarnings("PMD.AssignmentToNonFinalStatic")
    public RamController(Channel componentChannel) {
        super(componentChannel);
    }

    /**
     * On configure qemu.
     *
     * @param event the event
     */
    @Handler
    public void onConfigureQemu(RunnerConfigurationUpdate event) {
        Optional.ofNullable(event.configuration().vm.currentRam)
            .ifPresent(cr -> {
                if (currentRam != null && currentRam.equals(cr)) {
                    return;
                }
                currentRam = cr;
                fire(new MonitorCommand(new QmpSetBalloon(cr)));
            });
    }

}
