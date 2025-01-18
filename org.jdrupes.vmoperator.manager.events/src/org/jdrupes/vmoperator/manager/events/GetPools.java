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
import org.jdrupes.vmoperator.common.VmPool;
import org.jgrapes.core.Event;

/**
 * Gets the known pools' definitions.
 */
@SuppressWarnings("PMD.DataClass")
public class GetPools extends Event<List<VmPool>> {

    private String user;
    private List<String> roles = Collections.emptyList();

    /**
     * Return only {@link VmPool}s that are accessible by
     * the given user or roles.
     *
     * @param user the user
     * @param roles the roles
     * @return the event 
     */
    public GetPools accessibleFor(String user, List<String> roles) {
        this.user = user;
        this.roles = roles;
        return this;
    }

    /**
     * Returns the user filter criterion, if set.
     *
     * @return the optional
     */
    public Optional<String> forUser() {
        return Optional.ofNullable(user);
    }

    /**
     * Returns the roles criterion.
     *
     * @return the list
     */
    public List<String> forRoles() {
        return roles;
    }
}
