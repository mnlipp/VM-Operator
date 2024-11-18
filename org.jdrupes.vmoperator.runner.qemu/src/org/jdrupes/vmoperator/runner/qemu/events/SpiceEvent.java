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
 * Signals a connection from a client.
 */
public class SpiceEvent extends MonitorEvent {

    /**
     * Instantiates a new tray moved.
     *
     * @param kind the kind
     * @param data the data
     */
    public SpiceEvent(Kind kind, JsonNode data) {
        super(kind, data);
    }

    /**
     * Returns the client's host.
     *
     * @return the client's host address
     */
    public String clientHost() {
        return data().get("client").get("host").asText();
    }

    /**
     * Returns the client's port.
     *
     * @return the client's port number
     */
    public long clientPort() {
        return data().get("client").get("port").asLong();
    }
}
