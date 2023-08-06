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
import org.jgrapes.core.Event;

/**
 * The Class RunnerStateChange.
 */
public class RunnerStateChange extends Event<Void> {

    /**
     * The state.
     */
    public enum State {
        INITIALIZING, STARTING, RUNNING, TERMINATING
    }

    private final State state;

    /**
     * Instantiates a new runner state change.
     *
     * @param channels the channels
     */
    public RunnerStateChange(State state, Channel... channels) {
        super(channels);
        this.state = state;
    }

    /**
     * Returns the new state.
     *
     * @return the state
     */
    public State state() {
        return state;
    }
}
