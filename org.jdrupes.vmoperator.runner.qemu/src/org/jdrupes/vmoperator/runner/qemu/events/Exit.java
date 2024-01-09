/*
 * VM-Operator
 * Copyright (C) 2024 Michael N. Lipp
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

import org.jgrapes.core.events.Stop;

/**
 * Like {@link Stop}, but sets an exit status.
 */
@SuppressWarnings("PMD.ShortClassName")
public class Exit extends Stop {

    private final int exitStatus;

    /**
     * Instantiates a new exit.
     *
     * @param exitStatus the exit status
     */
    public Exit(int exitStatus) {
        this.exitStatus = exitStatus;
    }

    public int exitStatus() {
        return exitStatus;
    }
}
