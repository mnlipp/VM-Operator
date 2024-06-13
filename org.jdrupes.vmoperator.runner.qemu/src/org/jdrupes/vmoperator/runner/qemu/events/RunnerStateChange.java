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

import org.jgrapes.core.Channel;
import org.jgrapes.core.Components;
import org.jgrapes.core.Event;

/**
 * The Class RunnerStateChange.
 */
@SuppressWarnings("PMD.DataClass")
public class RunnerStateChange extends Event<Void> {

    /**
     * The state.
     */
    public enum RunState {
        INITIALIZING, STARTING, RUNNING, TERMINATING, STOPPED
    }

    private final RunState state;
    private final String reason;
    private final String message;
    private final boolean failed;

    /**
     * Instantiates a new runner state change.
     *
     * @param state the state
     * @param reason the reason
     * @param message the message
     * @param channels the channels
     */
    public RunnerStateChange(RunState state, String reason, String message,
            Channel... channels) {
        this(state, reason, message, false, channels);
    }

    /**
     * Instantiates a new runner state change.
     *
     * @param state the state
     * @param reason the reason
     * @param message the message
     * @param failed the failed
     * @param channels the channels
     */
    public RunnerStateChange(RunState state, String reason, String message,
            boolean failed, Channel... channels) {
        super(channels);
        this.state = state;
        this.reason = reason;
        this.failed = failed;
        this.message = message;
    }

    /**
     * Returns the new state.
     *
     * @return the state
     */
    public RunState runState() {
        return state;
    }

    /**
     * Gets the reason.
     *
     * @return the reason
     */
    public String reason() {
        return reason;
    }

    /**
     * Gets the message.
     *
     * @return the message
     */
    public String message() {
        return message;
    }

    /**
     * Checks if is failed.
     *
     * @return the failed
     */
    public boolean failed() {
        return failed;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(50);
        builder.append(Components.objectName(this))
            .append(" [").append(state).append(": ").append(reason);
        if (failed) {
            builder.append(" (failed)");
        }
        if (channels() != null) {
            builder.append(", channels=").append(Channel.toString(channels()));
        }
        builder.append(']');
        return builder.toString();
    }

}
