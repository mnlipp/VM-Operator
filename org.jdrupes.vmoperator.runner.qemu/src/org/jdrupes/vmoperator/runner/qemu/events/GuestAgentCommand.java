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

package org.jdrupes.vmoperator.runner.qemu.events;

import org.jdrupes.vmoperator.runner.qemu.commands.QmpCommand;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Components;
import org.jgrapes.core.Event;

/**
 * An {@link Event} that causes some component to send a QMP
 * command to the guest agent process.
 */
public class GuestAgentCommand extends Event<Void> {

    private final QmpCommand command;

    /**
     * Instantiates a new exec qmp command.
     *
     * @param command the command
     */
    public GuestAgentCommand(QmpCommand command) {
        this.command = command;
    }

    /**
     * Gets the command.
     *
     * @return the command
     */
    public QmpCommand command() {
        return command;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(Components.objectName(this))
            .append(" [").append(command);
        if (channels() != null) {
            builder.append(", channels=").append(Channel.toString(channels()));
        }
        builder.append(']');
        return builder.toString();
    }
}
