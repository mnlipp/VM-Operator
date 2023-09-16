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
import java.math.BigInteger;

/**
 * Signals a change of the balloon.
 */
public class BalloonChangeEvent extends MonitorEvent {

    /**
     * Instantiates a new tray moved.
     *
     * @param kind the kind
     * @param data the data
     */
    public BalloonChangeEvent(Kind kind, JsonNode data) {
        super(kind, data);
    }

    /**
     * Returns the actual value.
     *
     * @return the actual value
     */
    public BigInteger size() {
        return new BigInteger(data().get("actual").asText());
    }
}
