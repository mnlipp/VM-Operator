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

import org.jdrupes.vmoperator.common.K8sDynamicModel;
import org.jdrupes.vmoperator.common.K8sObserver;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Components;
import org.jgrapes.core.Event;

/**
 * Indicates a change in a VM definition. Note that the definition
 * consists of the metadata (mostly immutable), the "spec" and the 
 * "status" parts. Consumers that are only interested in "spec" 
 * changes should check {@link #specChanged()} before processing 
 * the event any further. 
 */
@SuppressWarnings("PMD.DataClass")
public class VmDefChanged extends Event<Void> {

    private final K8sObserver.ResponseType type;
    private final boolean specChanged;
    private final K8sDynamicModel vmDef;

    /**
     * Instantiates a new VM changed event.
     *
     * @param type the type
     * @param specChanged the spec part changed
     * @param vmDefinition the VM definition
     */
    public VmDefChanged(K8sObserver.ResponseType type, boolean specChanged,
            K8sDynamicModel vmDefinition) {
        this.type = type;
        this.specChanged = specChanged;
        this.vmDef = vmDefinition;
    }

    /**
     * Returns the type.
     *
     * @return the type
     */
    public K8sObserver.ResponseType type() {
        return type;
    }

    /**
     * Indicates if the "spec" part changed.
     */
    public boolean specChanged() {
        return specChanged;
    }

    /**
     * Returns the object.
     *
     * @return the object.
     */
    public K8sDynamicModel vmDefinition() {
        return vmDef;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(Components.objectName(this)).append(" [")
            .append(vmDef.getMetadata().getName()).append(' ').append(type);
        if (channels() != null) {
            builder.append(", channels=").append(Channel.toString(channels()));
        }
        builder.append(']');
        return builder.toString();
    }
}
