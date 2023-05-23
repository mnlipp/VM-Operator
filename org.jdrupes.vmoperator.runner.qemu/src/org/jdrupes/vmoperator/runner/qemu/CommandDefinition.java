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

package org.jdrupes.vmoperator.runner.qemu;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A command definition. 
 */
class CommandDefinition {
    public String name;
    public final List<String> command = new ArrayList<>();

    /**
     * Instantiates a new process definition.
     *
     * @param name the name
     * @param jsonData the json data
     */
    public CommandDefinition(String name, JsonNode jsonData) {
        this.name = name;
        for (JsonNode path : jsonData.get("executable")) {
            if (Files.isExecutable(Path.of(path.asText()))) {
                command.add(path.asText());
            }
        }
        if (command.isEmpty()) {
            throw new IllegalArgumentException("No executable found.");
        }
        collect(command, jsonData.get("arguments"));
    }

    private void collect(List<String> result, JsonNode node) {
        if (!node.isArray()) {
            result.add(node.asText());
            return;
        }
        for (var element : node) {
            collect(result, element);
        }
    }

    /**
     * Returns the name.
     *
     * @return the string
     */
    public String name() {
        return name;
    }
}