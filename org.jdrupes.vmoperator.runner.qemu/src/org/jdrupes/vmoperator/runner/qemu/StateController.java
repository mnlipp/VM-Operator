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

package org.jdrupes.vmoperator.runner.qemu;

/**
 * The context.
 */
/* default */ class StateController {

    private final Runner runner;

    /**
     * The state.
     */
    enum State {
        INITIALIZING, STARTING, RUNNING, TERMINATING
    }

    private State state = State.INITIALIZING;

    /**
     * Instantiates a new state controller.
     *
     * @param runner the runner
     */
    public StateController(Runner runner) {
        this.runner = runner;
    }

    /**
     * Sets the state.
     *
     * @param state the new state
     */
    public void set(State state) {
        this.state = state;
    }

    /**
     * Returns the state.
     *
     * @return the state
     */
    public State get() {
        return state;
    }

    @Override
    public String toString() {
        return "StateController [state=" + state + "]";
    }
}
