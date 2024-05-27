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
 * A {@link QmpCommand} that plugs a CPU into an unused slot.
 */
public class QmpAddCpu extends QmpCommand {

    private final JsonNode unused;
    private final String cpuId;

    /**
     * Instantiates a new command.
     *
     * @param unused description of an unused cpu slot
     * @param cpuId the cpu id
     */
    public QmpAddCpu(JsonNode unused, String cpuId) {
        super();
        this.unused = unused.deepCopy();
        this.cpuId = cpuId;
    }

    @Override
    public JsonNode toJson() {
        ObjectNode cmd = mapper.createObjectNode();
        cmd.put("execute", "device_add");
        ObjectNode args = mapper.createObjectNode();
        cmd.set("arguments", args);
        args.setAll((ObjectNode) unused.get("props"));
        args.set("driver", unused.get("type"));
        args.put("id", cpuId);
        return cmd;
    }

    @Override
    public String toString() {
        return "QmpAddCpu(" + unused.get("type") + ", " + cpuId + ")";
    }

}
