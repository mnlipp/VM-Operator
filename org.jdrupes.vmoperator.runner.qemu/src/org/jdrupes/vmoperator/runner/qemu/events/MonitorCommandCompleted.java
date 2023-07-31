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

import org.jdrupes.vmoperator.runner.qemu.events.MonitorCommand.Command;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Components;
import org.jgrapes.core.Event;

/**
 * Signals the completion of a monitor command.
 */
public class MonitorCommandCompleted extends Event<Void> {

    private final Command command;
    private final Object result;

    /**
     * Instantiates a new monitor command.
     *
     * @param command the command
     * @param arguments the arguments
     */
    public MonitorCommandCompleted(Command command, Object result) {
        super();
        this.command = command;
        this.result = result;
    }

    /**
     * Gets the command.
     *
     * @return the command
     */
    public Command command() {
        return command;
    }

    /**
     * Gets the result.
     *
     * @return the arguments
     */
    public Object result() {
        return result;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(Components.objectName(this))
            .append(" [").append(command);
        if (channels() != null) {
            builder.append(", channels=");
            builder.append(Channel.toString(channels()));
        }
        builder.append(']');
        return builder.toString();
    }
}
