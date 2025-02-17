/*
 * VM-Operator
 * Copyright (C) 2025 Michael N. Lipp
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
 * A {@link QmpCommand} that requests the guest info.
 */
public class QmpGuestInfo extends QmpCommand {

    @Override
    public JsonNode toJson() {
        ObjectNode cmd = mapper.createObjectNode();
        cmd.put("execute", "guest-info");
        return cmd;
    }

    @Override
    public String toString() {
        return "QmpGuestInfo()";
    }

}
