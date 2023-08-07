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

package org.jdrupes.vmoperator.runner.qemu.events;

import com.fasterxml.jackson.databind.JsonNode;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpCommand;

/**
 * A {@link MonitorResult} that indicates that a CPU has been deleted.
 */
public class CpuDeleted extends MonitorResult {

    /**
     * Instantiates a new cpu deleted.
     *
     * @param command the command
     * @param response the response
     */
    public CpuDeleted(QmpCommand command, JsonNode response) {
        super(command, response);
    }

}
