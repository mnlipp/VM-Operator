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

package org.jdrupes.vmoperator.runner.qemu.events;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Signals the processing of the {@link QmpShutdown} event.
 */
public class ShutdownEvent extends MonitorEvent {

    /**
     * Instantiates a new shutdown event.
     *
     * @param kind the kind
     * @param data the data
     */
    public ShutdownEvent(Kind kind, JsonNode data) {
        super(kind, data);
    }

    /**
     * returns if this is initiated by the guest.
     *
     * @return the value
     */
    public boolean byGuest() {
        return data().get("guest").asBoolean();
    }

}
