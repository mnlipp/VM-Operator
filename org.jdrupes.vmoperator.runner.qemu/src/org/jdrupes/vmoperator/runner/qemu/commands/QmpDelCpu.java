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

package org.jdrupes.vmoperator.runner.qemu.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The Class QmpDelCpu.
 */
public class QmpDelCpu extends QmpCommand {

    @SuppressWarnings({ "PMD.FieldNamingConventions",
        "PMD.VariableNamingConventions" })
    private static final JsonNode jsonTemplate
        = parseJson("{ \"execute\": \"device_del\", "
            + "\"arguments\": " + "{ \"id\": 0 } }");
    private final String cpuId;

    /**
     * Instantiates a new sets the current ram.
     *
     * @param size the size
     */
    public QmpDelCpu(String cpuId) {
        this.cpuId = cpuId;
    }

    /**
     * To Json.
     *
     * @return the json node
     */
    @Override
    public JsonNode toJson() {
        var cmd = jsonTemplate.deepCopy();
        ((ObjectNode) cmd.get("arguments")).put("id", cpuId);
        return cmd;
    }

    /**
     * To string.
     *
     * @return the string
     */
    @Override
    public String toString() {
        return "QmpDelCpu(" + cpuId + ")";
    }

}
