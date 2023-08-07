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

// TODO: Auto-generated Javadoc
/**
 * A {@link QmpCommand} that triggers a media change in a CDROM drive.
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
     * Instantiates a new qmp change medium.
     *
     * @param driveId the drive id
     * @param file the file
     */
    public QmpChangeMedium(String driveId, String file) {
        this.driveId = driveId;
        this.file = file;
    }

    @Override
    public JsonNode toJson() {
        var cmd = jsonTemplate.deepCopy();
        ((ObjectNode) cmd.get("arguments")).put("id", driveId);
        ((ObjectNode) cmd.get("arguments")).put("filename", file);
        return cmd;
    }

    @Override
    public String toString() {
        return "QmpChangeMedium(" + driveId + ", " + file + ")";
    }

}
