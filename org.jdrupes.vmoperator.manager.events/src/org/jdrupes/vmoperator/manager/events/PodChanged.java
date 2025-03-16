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

import io.kubernetes.client.openapi.models.V1Pod;
import org.jdrupes.vmoperator.common.K8sObserver;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Components;
import org.jgrapes.core.Event;

/**
 * Indicates a change in a pod that runs a VM.
 */
public class PodChanged extends Event<Void> {

    private final V1Pod pod;
    private final K8sObserver.ResponseType type;

    /**
     * Instantiates a new VM changed event.
     *
     * @param pod the pod
     * @param type the type
     */
    public PodChanged(V1Pod pod, K8sObserver.ResponseType type) {
        this.pod = pod;
        this.type = type;
    }

    /**
     * Gets the pod.
     *
     * @return the pod
     */
    public V1Pod pod() {
        return pod;
    }

    /**
     * Returns the type.
     *
     * @return the type
     */
    public K8sObserver.ResponseType type() {
        return type;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(Components.objectName(this)).append(" [")
            .append(pod.getMetadata().getName()).append(' ').append(type);
        if (channels() != null) {
            builder.append(", channels=").append(Channel.toString(channels()));
        }
        builder.append(']');
        return builder.toString();
    }
}
