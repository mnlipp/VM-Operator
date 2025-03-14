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

import org.jdrupes.vmoperator.common.VmDefinition;
import org.jgrapes.core.Event;

/**
 * Gets the current display secret and optionally updates it.
 */
@SuppressWarnings("PMD.DataClass")
public class GetDisplaySecret extends Event<String> {

    private final VmDefinition vmDef;
    private final String user;

    /**
     * Instantiates a new request for the display secret.
     * After handling the event, a result of `null` means that
     * no secret is needed. No result means that the console
     * is not accessible.
     * 
     * @param vmDef the vm name
     * @param user the requesting user
     */
    public GetDisplaySecret(VmDefinition vmDef, String user) {
        this.vmDef = vmDef;
        this.user = user;
    }

    /**
     * Gets the VM definition.
     *
     * @return the VM definition
     */
    public VmDefinition vmDefinition() {
        return vmDef;
    }

    /**
     * Return the id of the user who has requested the password.
     *
     * @return the string
     */
    public String user() {
        return user;
    }

    /**
     * Returns `true` if a password is available. May only be called
     * when the event is completed. Note that the password returned
     * by {@link #secret()} may be `null`, indicating that no password
     * is needed.
     *
     * @return true, if successful
     */
    public boolean secretAvailable() {
        if (!isDone()) {
            throw new IllegalStateException("Event is not done.");
        }
        return !currentResults().isEmpty();
    }

    /**
     * Return the secret. May only be called when the event has been
     * completed with a valid result (see {@link #secretAvailable()}).
     *
     * @return the password. A value of `null` means that no password
     * is required.
     */
    public String secret() {
        if (!isDone() || currentResults().isEmpty()) {
            throw new IllegalStateException("Event is not done.");
        }
        return currentResults().get(0);
    }
}
