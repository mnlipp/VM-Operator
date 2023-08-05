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

import java.util.Arrays;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Components;
import org.jgrapes.core.Event;

/**
 * A command to be executed by the monitor.
 */
public class MonitorCommand extends Event<Void> {

    /**
     * The available commands.
     */
    public enum Command {
        CONTINUE, SET_CURRENT_CPUS, SET_CURRENT_RAM, CHANGE_MEDIUM
    }

    private final Command command;
    private final Object[] arguments;

    /**
     * Instantiates a new monitor command.
     *
     * @param command the command
     * @param arguments the arguments
     */
    public MonitorCommand(Command command, Object... arguments) {
        super();
        this.command = command;
        this.arguments = Arrays.copyOf(arguments, arguments.length);
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
     * Gets the arguments.
     *
     * @return the arguments
     */
    public Object[] arguments() {
        return Arrays.copyOf(arguments, arguments.length);
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
