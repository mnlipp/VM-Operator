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

import io.kubernetes.client.openapi.models.V1Secret;
import org.jdrupes.vmoperator.common.K8sObserver.ResponseType;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Components;
import org.jgrapes.core.Event;

/**
 * Indicates that a display secret has changed. 
 */
@SuppressWarnings("PMD.DataClass")
public class DisplayPasswordChanged extends Event<Void> {

    private final ResponseType type;
    private final V1Secret secret;

    /**
     * Initializes a new display secret changed event.
     *
     * @param type the type
     * @param secret the secret
     */
    public DisplayPasswordChanged(ResponseType type, V1Secret secret) {
        this.type = type;
        this.secret = secret;
    }

    /**
     * Returns the type.
     *
     * @return the type
     */
    public ResponseType type() {
        return type;
    }

    /**
     * Gets the secret.
     *
     * @return the secret
     */
    public V1Secret secret() {
        return secret;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(Components.objectName(this)).append(" [")
            .append(secret.getMetadata().getName()).append(' ').append(type);
        if (channels() != null) {
            builder.append(", channels=").append(Channel.toString(channels()));
        }
        builder.append(']');
        return builder.toString();
    }
}
