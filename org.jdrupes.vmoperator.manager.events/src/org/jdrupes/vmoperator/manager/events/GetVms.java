/*
 * VM-Operator
 * Copyright (C) 2024 Michael N. Lipp
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jdrupes.vmoperator.common.VmDefinition;
import org.jgrapes.core.Event;

/**
 * Gets the known VMs' definitions and channels.
 */
@SuppressWarnings("PMD.DataClass")
public class GetVms extends Event<List<GetVms.VmData>> {

    private String name;
    private String user;
    private List<String> roles = Collections.emptyList();

    /**
     * Return only the VMs with the given name.
     *
     * @param name the name
     * @return the returns the vms
     */
    public GetVms withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Return only {@link VmDefinition}s that are accessible by
     * the given user or roles.
     *
     * @param user the user
     * @param roles the roles
     * @return the event
     */
    public GetVms accessibleFor(String user, List<String> roles) {
        this.user = user;
        this.roles = roles;
        return this;
    }

    /**
     * Returns the name filter criterion, if set.
     *
     * @return the optional
     */
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    /**
     * Returns the user filter criterion, if set.
     *
     * @return the optional
     */
    public Optional<String> user() {
        return Optional.ofNullable(user);
    }

    /**
     * Returns the roles criterion.
     *
     * @return the list
     */
    public List<String> roles() {
        return roles;
    }

    /**
     * Return tuple.
     *
     * @param definition the definition
     * @param channel the channel
     */
    public record VmData(VmDefinition definition, VmChannel channel) {
    }
}
