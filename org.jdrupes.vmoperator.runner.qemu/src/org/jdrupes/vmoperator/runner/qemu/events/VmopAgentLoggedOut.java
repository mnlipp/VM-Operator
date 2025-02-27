/*
 * VM-Operator
 * Copyright (C) 2025 Michael N. Lipp
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

package org.jdrupes.vmoperator.runner.qemu.events;

import org.jgrapes.core.Event;

/**
 * Signals that the logout command has been processes by the
 * VM operator agent.
 */
public class VmopAgentLoggedOut extends Event<Void> {

    private final VmopAgentLogOut triggering;

    /**
     * Instantiates a new vmop agent logged out.
     *
     * @param triggeringEvent the triggering event
     */
    public VmopAgentLoggedOut(VmopAgentLogOut triggeringEvent) {
        this.triggering = triggeringEvent;
    }

    /**
     * Gets the triggering event.
     *
     * @return the triggering
     */
    public VmopAgentLogOut triggering() {
        return triggering;
    }

}
