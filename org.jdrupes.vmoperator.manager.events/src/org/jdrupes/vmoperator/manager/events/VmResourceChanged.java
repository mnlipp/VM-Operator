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

import org.jdrupes.vmoperator.common.K8sObserver;
import org.jdrupes.vmoperator.common.VmDefinition;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Components;
import org.jgrapes.core.Event;

/**
 * Indicates a change in a VM "resource". Note that the resource
 * combines the VM CR's metadata (mostly immutable), the VM CR's
 * "spec" part, the VM CR's "status" subresource and state information
 * from the pod. Consumers that are only interested in "spec" changes
 * should check {@link #specChanged()} before processing the event any
 * further. 
 */
@SuppressWarnings("PMD.DataClass")
public class VmResourceChanged extends Event<Void> {

    private final K8sObserver.ResponseType type;
    private final VmDefinition vmDefinition;
    private final boolean specChanged;
    private final boolean podChanged;

    /**
     * Instantiates a new VM changed event.
     *
     * @param type the type
     * @param vmDefinition the VM definition
     * @param specChanged the spec part changed
     */
    public VmResourceChanged(K8sObserver.ResponseType type,
            VmDefinition vmDefinition, boolean specChanged,
            boolean podChanged) {
        this.type = type;
        this.vmDefinition = vmDefinition;
        this.specChanged = specChanged;
        this.podChanged = podChanged;
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
     * Return the VM definition.
     *
     * @return the VM definition
     */
    public VmDefinition vmDefinition() {
        return vmDefinition;
    }

    /**
     * Indicates if the "spec" part changed.
     */
    public boolean specChanged() {
        return specChanged;
    }

    /**
     * Indicates if the pod status changed.
     */
    public boolean podChanged() {
        return podChanged;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(Components.objectName(this)).append(" [")
            .append(vmDefinition.name()).append(' ').append(type);
        if (channels() != null) {
            builder.append(", channels=").append(Channel.toString(channels()));
        }
        builder.append(']');
        return builder.toString();
    }
}
