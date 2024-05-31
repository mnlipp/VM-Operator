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

import java.util.Optional;
import org.jdrupes.vmoperator.common.VmDefinitionModel;
import org.jgrapes.core.Event;

/**
 * Gets the current display secret and optionally updates it.
 */
@SuppressWarnings("PMD.DataClass")
public class GetDisplayPassword extends Event<String> {

    private final VmDefinitionModel vmDef;

    /**
     * Instantiates a new returns the display secret.
     *
     * @param vmDef the vm name
     */
    public GetDisplayPassword(VmDefinitionModel vmDef) {
        this.vmDef = vmDef;
    }

    /**
     * Gets the vm definition.
     *
     * @return the vm definition
     */
    public VmDefinitionModel vmDefinition() {
        return vmDef;
    }

    /**
     * Return the password. May only be called when the event is completed.
     *
     * @return the optional
     */
    public Optional<String> password() {
        if (!isDone()) {
            throw new IllegalStateException("Event is not done.");
        }
        return currentResults().stream().findFirst();
    }
}
