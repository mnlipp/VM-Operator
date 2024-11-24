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

package org.jdrupes.vmoperator.manager.events;

import org.jdrupes.vmoperator.common.VmPool;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Components;
import org.jgrapes.core.Event;

/**
 * Indicates a change in a pool configuration. 
 */
@SuppressWarnings("PMD.DataClass")
public class VmPoolChanged extends Event<Void> {

    private final VmPool vmPool;
    private final boolean deleted;

    /**
     * Instantiates a new VM changed event.
     *
     * @param pool the pool
     * @param deleted true, if the pool was deleted
     */
    public VmPoolChanged(VmPool pool, boolean deleted) {
        vmPool = pool;
        this.deleted = deleted;
    }

    /**
     * Instantiates a new VM changed event for an existing pool.
     *
     * @param pool the pool
     */
    public VmPoolChanged(VmPool pool) {
        this(pool, false);
    }

    /**
     * Returns the VM pool.
     *
     * @return the vm pool
     */
    public VmPool vmPool() {
        return vmPool;
    }

    /**
     * Pool has been deleted.
     *
     * @return true, if successful
     */
    public boolean deleted() {
        return deleted;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(30);
        builder.append(Components.objectName(this))
            .append(" [");
        if (deleted) {
            builder.append("Deleted: ");
        }
        builder.append(vmPool);
        if (channels() != null) {
            builder.append(", channels=").append(Channel.toString(channels()));
        }
        builder.append(']');
        return builder.toString();
    }
}
