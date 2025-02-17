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

import org.jgrapes.core.Event;

/**
 * Note the assignment to a user in the VM status.
 */
@SuppressWarnings("PMD.DataClass")
public class UpdateAssignment extends Event<Boolean> {

    private final String usedPool;
    private final String toUser;

    /**
     * Instantiates a new event.
     *
     * @param usedPool the used pool
     * @param toUser the to user
     */
    public UpdateAssignment(String usedPool, String toUser) {
        this.usedPool = usedPool;
        this.toUser = toUser;
    }

    /**
     * Gets the pool to assign from.
     *
     * @return the pool
     */
    public String usedPool() {
        return usedPool;
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
