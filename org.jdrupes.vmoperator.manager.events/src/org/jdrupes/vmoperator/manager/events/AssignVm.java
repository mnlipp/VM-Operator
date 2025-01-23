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

package org.jdrupes.vmoperator.manager.events;

import org.jdrupes.vmoperator.manager.events.GetVms.VmData;
import org.jgrapes.core.Event;

/**
 * Assign a VM from a pool to a user.
 */
@SuppressWarnings("PMD.DataClass")
public class AssignVm extends Event<VmData> {

    private final String fromPool;
    private final String toUser;

    /**
     * Instantiates a new event.
     *
     * @param fromPool the from pool
     * @param toUser the to user
     */
    public AssignVm(String fromPool, String toUser) {
        this.fromPool = fromPool;
        this.toUser = toUser;
    }

    /**
     * Gets the pool to assign from.
     *
     * @return the pool
     */
    public String fromPool() {
        return fromPool;
    }

    /**
     * Gets the user to assign to.
     *
     * @return the to user
     */
    public String toUser() {
        return toUser;
    }
}
