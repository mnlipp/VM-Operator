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
 * The Class QmpChangeMedium.
 */
public class QmpChangeMedium extends QmpCommand {

    @SuppressWarnings({ "PMD.FieldNamingConventions",
        "PMD.VariableNamingConventions" })
    private static final JsonNode jsonTemplate
        = parseJson("{ \"execute\": \"blockdev-change-medium\",\"arguments\": {"
            + "\"id\": \"\",\"filename\": \"\",\"format\": \"raw\","
            + "\"read-only-mode\": \"read-only\" } }");
    private final String driveId;
    private final String file;

    /**
     * Instantiates a new sets the current ram.
     *
     * @param driveId the drive id
     */
    public QmpChangeMedium(String driveId, String file) {
        this.driveId = driveId;
        this.file = file;
    }

    /**
     * To Json.
     *
     * @return the json node
     */
    @Override
    public JsonNode toJson() {
        var cmd = jsonTemplate.deepCopy();
        ((ObjectNode) cmd.get("arguments")).put("id", driveId);
        ((ObjectNode) cmd.get("arguments")).put("filename", file);
        return cmd;
    }

    /**
     * To string.
     *
     * @return the string
     */
    @Override
    public String toString() {
        return "QmpRemoveMedium(" + driveId + ")";
    }

}
