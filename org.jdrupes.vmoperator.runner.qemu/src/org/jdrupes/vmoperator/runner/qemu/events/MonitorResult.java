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
import org.jgrapes.core.Event;

/**
 * Signals the reception of a result from the monitor.
 */
public class MonitorResult extends Event<Void> {

    private final String executed;
    private final JsonNode returned;

    /**
     * Instantiates a new monitor result.
     *
     * @param executed the command executed
     * @param response the response
     */
    public MonitorResult(String executed, JsonNode response) {
        this.executed = executed;
        this.returned = response.get("return");
    }

    /**
     * Return the executed command.
     *
     * @return the string
     */
    public String executed() {
        return executed;
    }

    /**
     * Return the values returned.
     */
    public JsonNode returned() {
        return returned;
    }
}
