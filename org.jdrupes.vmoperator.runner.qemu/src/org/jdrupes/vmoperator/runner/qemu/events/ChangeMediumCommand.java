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

/**
 * The Class ChangeMediumCommand.
 */
public class ChangeMediumCommand extends MonitorCommand {

    /**
     * Instantiates a new change medium command.
     *
     * @param id the id
     * @param file the file path
     */
    public ChangeMediumCommand(String id, String file) {
        super(Command.CHANGE_MEDIUM, id, file);
    }

    /**
     * Gets the id.
     *
     * @return the id
     */
    @SuppressWarnings("PMD.ShortMethodName")
    public String id() {
        return (String) arguments()[0];
    }

    /**
     * Gets the file.
     *
     * @return the file
     */
    public String file() {
        return (String) arguments()[1];
    }
}
