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
 * A {@link QmpCommand} that causes a CDROM drive's tray to be opened.
 */
public class QmpOpenTray extends QmpCommand {

    @SuppressWarnings({ "PMD.FieldNamingConventions",
        "PMD.VariableNamingConventions" })
    private static final JsonNode jsonTemplate
        = parseJson("{ \"execute\": \"blockdev-open-tray\",\"arguments\": {"
            + "\"id\": \"\" } }");
    private final String driveId;

    /**
     * Instantiates a new qmp open tray.
     *
     * @param driveId the drive id
     */
    public QmpOpenTray(String driveId) {
        this.driveId = driveId;
    }

    @Override
    public JsonNode toJson() {
        var cmd = jsonTemplate.deepCopy();
        ((ObjectNode) cmd.get("arguments")).put("id", driveId);
        return cmd;
    }

    @Override
    public String toString() {
        return "QmpOpenTray(" + driveId + ")";
    }

}
