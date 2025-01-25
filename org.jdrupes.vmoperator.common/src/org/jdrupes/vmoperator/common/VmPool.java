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

package org.jdrupes.vmoperator.common;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jdrupes.vmoperator.common.VmDefinition.Grant;
import org.jdrupes.vmoperator.common.VmDefinition.Permission;
import org.jdrupes.vmoperator.util.DataPath;

/**
 * Represents a VM pool.
 */
@SuppressWarnings({ "PMD.DataClass" })
public class VmPool {

    private String name;
    private String retention;
    private boolean defined;
    private List<Grant> permissions = Collections.emptyList();
    private final Set<String> vms
        = Collections.synchronizedSet(new HashSet<>());

    /**
     * Instantiates a new vm pool.
     *
     * @param name the name
     */
    public VmPool(String name) {
        this.name = name;
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Sets the name.
     *
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Checks if is defined.
     *
     * @return the result
     */
    public boolean isDefined() {
        return defined;
    }

    /**
     * Sets if is.
     *
     * @param defined the defined to set
     */
    public void setDefined(boolean defined) {
        this.defined = defined;
    }

    /**
     * Gets the retention.
     *
     * @return the retention
     */
    public String retention() {
        return retention;
    }

    /**
     * Sets the retention.
     *
     * @param retention the retention to set
     */
    public void setRetention(String retention) {
        this.retention = retention;
    }

    /**
     * Permissions granted for a VM from the pool.
     *
     * @return the permissions
     */
    public List<Grant> permissions() {
        return permissions;
    }

    /**
     * Sets the permissions.
     *
     * @param permissions the permissions to set
     */
    public void setPermissions(List<Grant> permissions) {
        this.permissions = permissions;
    }

    /**
     * Returns the VM names.
     *
     * @return the vms
     */
    public Set<String> vms() {
        return vms;
    }

    /**
     * To string.
     *
     * @return the string
     */
    @Override
    @SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
    public String toString() {
        StringBuilder builder = new StringBuilder(50);
        builder.append("VmPool [name=").append(name).append(", permissions=")
            .append(permissions).append(", vms=");
        if (vms.size() <= 3) {
            builder.append(vms);
        } else {
            builder.append('[').append(vms.stream().limit(3).map(s -> s + ",")
                .collect(Collectors.joining())).append("...]");
        }
        builder.append(']');
        return builder.toString();
    }

    /**
     * Collect all permissions for the given user with the given roles.
     *
     * @param user the user
     * @param roles the roles
     * @return the sets the
     */
    public Set<Permission> permissionsFor(String user,
            Collection<String> roles) {
        return permissions.stream()
            .filter(g -> DataPath.get(g, "user").map(u -> u.equals(user))
                .orElse(false)
                || DataPath.get(g, "role").map(roles::contains).orElse(false))
            .map(g -> DataPath.<Set<Permission>> get(g, "may")
                .orElse(Collections.emptySet()).stream())
            .flatMap(Function.identity()).collect(Collectors.toSet());
    }

    /**
     * Return the instant until which an assignment should be retained.
     *
     * @param lastUsed the last used
     * @return the instant
     */
    public Instant retainUntil(Instant lastUsed) {
        if (retention.startsWith("P")) {
            return lastUsed.plus(Duration.parse(retention));
        }
        return Instant.parse(retention);
    }
}
