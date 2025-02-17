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

package org.jdrupes.vmoperator.runner.qemu.events;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Signals a virtual serial port's open state change.
 */
public class VserportChangeEvent extends MonitorEvent {

    /**
     * Initializes a new instance.
     *
     * @param kind the kind
     * @param data the data
     */
    public VserportChangeEvent(Kind kind, JsonNode data) {
        super(kind, data);
    }

    /**
     * Return the channel's id.
     *
     * @return the string
     */
    @SuppressWarnings("PMD.ShortMethodName")
    public String id() {
        return data().get("id").asText();
    }

    /**
     * Returns the open state of the port.
     *
     * @return true, if is open
     */
    public boolean isOpen() {
        return Boolean.parseBoolean(data().get("open").asText());
    }
}
