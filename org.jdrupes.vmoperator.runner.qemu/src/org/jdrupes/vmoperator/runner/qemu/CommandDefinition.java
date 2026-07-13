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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A command definition. 
 */
/* default */ class CommandDefinition {
    /* default */ String name;
    /* default */ final List<String> command = new ArrayList<>();
    /* default */ @SuppressWarnings("PMD.UseConcurrentHashMap")
    final Map<String, String> environment = new HashMap<>();

    /**
     * Instantiates a new process definition.
     *
     * @param name the name
     * @param jsonData the json data
     */
    /* default */ CommandDefinition(String name, JsonNode jsonData) {
        this.name = name;
        for (JsonNode path : jsonData.get("executable")) {
            if (Files.isExecutable(Path.of(path.asText()))) {
                command.add(path.asText());
                break;
            }
        }
        if (command.isEmpty()) {
            throw new IllegalArgumentException("No executable found.");
        }
        assembleCommand(command, jsonData.get("arguments"));
        collectEnvironment(jsonData);
    }

    private void assembleCommand(List<String> result, JsonNode node) {
        if (!node.isArray()) {
            result.add(node.asText());
            return;
        }
        for (var element : node) {
            assembleCommand(result, element);
        }
    }

    private void collectEnvironment(JsonNode jsonData) {
        if (jsonData.has("environment")) {
            if (!jsonData.get("environment").isArray()) {
                throw new IllegalArgumentException(
                    "environment must be an array");
            }
            var envArray = (ArrayNode) jsonData.get("environment");
            for (var element : envArray) {
                var envEntry = (ObjectNode) element;
                environment.put(envEntry.get("name").asText(),
                    envEntry.get("value").asText());
            }
        }
    }

    /**
     * Returns the name.
     *
     * @return the string
     */
    /* default */ String name() {
        return name;
    }

    @Override
    public String toString() {
        return "Command " + name + ": " + command;
    }
}