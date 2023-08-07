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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Class QmpCommand.
 */
public abstract class QmpCommand {

    @SuppressWarnings({ "PMD.FieldNamingConventions",
        "PMD.VariableNamingConventions" })
    protected static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Parses the json.
     *
     * @param json the json
     * @return the json node
     */
    protected static JsonNode parseJson(String json) {
        try {
            return mapper.readValue(json, JsonNode.class);
        } catch (IOException e) {
            Logger.getLogger(QmpCommand.class.getName()).log(Level.SEVERE, e,
                () -> "Cannot initialize class: " + e.getMessage());
            return null;
        }
    }

    /**
     * Returns the Json to be sent to the Qemu process.
     *
     * @return the json node
     */
    public abstract JsonNode toJson();
}
