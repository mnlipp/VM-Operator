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

package org.jdrupes.vmoperator.manager.events;

import org.jgrapes.core.Channel;
import org.jgrapes.core.Event;

/**
 * Modifies a VM.
 */
@SuppressWarnings("PMD.DataClass")
public class ModifyVm extends Event<Void> {

    private final String name;
    private final String path;
    private final Object value;

    /**
     * Instantiates a new modify vm event.
     *
     * @param channels the channels
     * @param name the name
     */
    public ModifyVm(String name, String path, Object value,
            Channel... channels) {
        super(channels);
        this.name = name;
        this.path = path;
        this.value = value;
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Gets the path.
     *
     * @return the path
     */
    public String path() {
        return path;
    }

    /**
     * Gets the value.
     *
     * @return the value
     */
    public Object value() {
        return value;
    }

}
