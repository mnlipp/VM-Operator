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

package org.jdrupes.vmoperator.manager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.jgrapes.core.Channel;

/**
 * A channel manager for abstract monitors.
 *
 * @param <C> the generic type
 */
public class ChannelManager<C extends Channel> {

    @SuppressWarnings({ "PMD.FieldNamingConventions",
        "PMD.VariableNamingConventions" })
    private final Map<String, C> channels = new ConcurrentHashMap<>();
    private final Function<String, C> supplier;

    /**
     * Instantiates a new channel manager.
     *
     * @param supplier the supplier that reates new channels
     */
    public ChannelManager(Function<String, C> supplier) {
        this.supplier = supplier;
    }

    /**
     * Returns the {@link Channel} for the given name, creating it if it
     * down't exist yet.
     *
     * @param name the name
     * @return the channel used for events related to the specified object
     */
    public C channel(String name) {
        return channels.computeIfAbsent(name, supplier);
    }

    /**
     * Removes the channel with the given name.
     *
     * @param name the name
     */
    public void remove(String name) {
        channels.remove(name);
    }

}
