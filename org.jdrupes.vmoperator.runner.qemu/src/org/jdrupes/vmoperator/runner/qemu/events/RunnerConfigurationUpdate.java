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

import org.jdrupes.vmoperator.runner.qemu.Configuration;
import org.jdrupes.vmoperator.runner.qemu.events.RunnerStateChange.State;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Event;

/**
 * An {@link Event} that notifies controllers about an updated 
 * configuration. Controllers should adapt the resource that they
 * manage to the new configuration. If the adaption cannot be
 * made by the handler alone, it should call {@link Event#suspendHandling()}
 * on the event and only {@link Event#resumeHandling() resume handling}
 * when the adaption has completed.
 */
public class RunnerConfigurationUpdate extends Event<Void> {

    private final Configuration configuration;
    private final State state;

    /**
     * Instantiates a new configuration event.
     *
     * @param channels the channels
     */
    public RunnerConfigurationUpdate(Configuration configuration, State state,
            Channel... channels) {
        super(channels);
        this.state = state;
        this.configuration = configuration;
    }

    /**
     * Returns the configuration.
     *
     * @return the configuration
     */
    public Configuration configuration() {
        return configuration;
    }

    /**
     * Returns the runner's state when the event was fired.
     *
     * @return the state
     */
    public State state() {
        return state;
    }
}
