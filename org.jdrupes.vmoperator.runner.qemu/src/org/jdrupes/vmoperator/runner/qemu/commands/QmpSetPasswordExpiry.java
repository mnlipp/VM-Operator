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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * A {@link QmpCommand} that sets the password expiry.
 */
public class QmpSetPasswordExpiry extends QmpCommand {

    private final String protocol;
    private final String expiry;

    /**
     * Instantiates a new command.
     *
     * @param protocol the protocol
     * @param expiry the expiry time
     */
    public QmpSetPasswordExpiry(String protocol, String expiry) {
        this.protocol = protocol;
        this.expiry = expiry;
    }

    @Override
    public JsonNode toJson() {
        ObjectNode cmd = mapper.createObjectNode();
        cmd.put("execute", "expire_password");
        ObjectNode args = mapper.createObjectNode();
        cmd.set("arguments", args);
        args.set("protocol", new TextNode(protocol));
        args.set("time", new TextNode(expiry));
        return cmd;
    }

    @Override
    public String toString() {
        try {
            var json = toJson();
            return mapper.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            return "(no string representation)";
        }
    }

}
