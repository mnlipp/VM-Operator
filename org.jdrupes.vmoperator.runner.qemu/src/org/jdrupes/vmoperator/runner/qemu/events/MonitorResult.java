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

package org.jdrupes.vmoperator.runner.qemu.events;

import com.fasterxml.jackson.databind.JsonNode;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpAddCpu;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpCommand;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpDelCpu;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpQueryHotpluggableCpus;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Components;
import org.jgrapes.core.Event;

/**
 * Signals the reception of a result from the monitor.
 */
public class MonitorResult extends Event<Void> {

    private final QmpCommand executed;
    private final JsonNode response;

    /**
     * Create event from data.
     *
     * @param command the command
     * @param response the response
     * @return the monitor result
     */
    public static MonitorResult from(QmpCommand command, JsonNode response) {
        if (command instanceof QmpQueryHotpluggableCpus) {
            return new HotpluggableCpuResult(command, response);
        }
        if (command instanceof QmpAddCpu) {
            return new CpuAdded(command, response);
        }
        if (command instanceof QmpDelCpu) {
            return new CpuDeleted(command, response);
        }
        return new MonitorResult(command, response);
    }

    /**
     * Instantiates a new monitor result.
     *
     * @param executed the executed
     * @param response the response
     */
    protected MonitorResult(QmpCommand command, JsonNode response) {
        this.executed = command;
        this.response = response;
    }

    /**
     * Returns the executed executed.
     *
     * @return the executed
     */
    public QmpCommand executed() {
        return executed;
    }

    /**
     * Returns true if executed has been executed successfully.
     *
     * @return true, if successful
     */
    public boolean successful() {
        return response.has("return");
    }

    /**
     * Returns the values that come with the response.
     *
     * @return the json node
     */
    public JsonNode values() {
        if (response.has("return")) {
            return response.get("return");
        }
        if (response.has("error")) {
            return response.get("error");
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(Components.objectName(this))
            .append(" [").append(executed).append(", ").append(successful());
        if (channels() != null) {
            builder.append(", channels=");
            builder.append(Channel.toString(channels()));
        }
        builder.append(']');
        return builder.toString();
    }
}
