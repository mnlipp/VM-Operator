/*
 * JGrapes Event Driven Framework
 * Copyright (C) 2018 Michael N. Lipp
 * 
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU General Public License as published by 
 * the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
 * for more details.
 * 
 * You should have received a copy of the GNU General Public License along 
 * with this program; if not, see <http://www.gnu.org/licenses/>.
 */

package org.jdrupes.vmoperator.manager;

import io.kubernetes.client.openapi.models.V1ObjectMeta;

import org.jgrapes.core.Channel;
import org.jgrapes.core.Components;
import org.jgrapes.core.Event;

/**
 * Indicates a change in a VM definition.
 */
public class VmChangedEvent extends Event<Void> {

    /**
     * The type of change.
     */
    public enum Type {
        ADDED, MODIFIED, DELETED
    }

    private final Type type;
    private final V1ObjectMeta metadata;

    /**
     * Instantiates a new VM changed event.
     *
     * @param type the type
     * @param metadata the metadata
     */
    public VmChangedEvent(Type type, V1ObjectMeta metadata) {
        this.type = type;
        this.metadata = metadata;
    }

    /**
     * Returns the type.
     *
     * @return the type
     */
    public Type type() {
        return type;
    }

    /**
     * Returns the metadata.
     *
     * @return the metadata
     */
    public V1ObjectMeta metadata() {
        return metadata;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(Components.objectName(this)).append(" [").append(type)
            .append(' ').append(metadata.getName());
        if (channels() != null) {
            builder.append(", channels=");
            builder.append(Channel.toString(channels()));
        }
        builder.append(']');
        return builder.toString();
    }
}
