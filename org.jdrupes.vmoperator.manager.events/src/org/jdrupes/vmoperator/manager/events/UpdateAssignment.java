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

import org.jdrupes.vmoperator.common.VmPool;
import org.jgrapes.core.Event;

/**
 * Note the assignment to a user in the VM status.
 */
@SuppressWarnings("PMD.DataClass")
public class UpdateAssignment extends Event<Boolean> {

    private final VmPool fromPool;
    private final String toUser;

    /**
     * Instantiates a new event.
     *
     * @param fromPool the pool from which the VM was assigned
     * @param toUser the to user
     */
    public UpdateAssignment(VmPool fromPool, String toUser) {
        this.fromPool = fromPool;
        this.toUser = toUser;
    }

    /**
     * Gets the pool from which the VM was assigned.
     *
     * @return the pool
     */
    public VmPool fromPool() {
        return fromPool;
    }

    /**
     * Gets the user to whom the VM was assigned.
     *
     * @return the to user
     */
    public String toUser() {
        return toUser;
    }
}
