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

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a VM pool.
 */
@SuppressWarnings({ "PMD.DataClass" })
public class VmPool {

    private String name;
    private List<Grant> permissions = Collections.emptyList();
    private final Set<String> vms
        = Collections.synchronizedSet(new HashSet<>());

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

    @Override
    @SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
    public String toString() {
        StringBuilder builder = new StringBuilder(50);
        builder.append("VmPool [name=").append(name).append(", permissions=")
            .append(permissions).append(", vms=");
        if (vms.size() <= 3) {
            builder.append(vms);
        } else {
            builder.append('[');
            vms.stream().limit(3).map(s -> s + ",").forEach(builder::append);
            builder.append("...]");
        }
        builder.append(']');
        return builder.toString();
    }

    /**
     * A permission grant to a user or role.
     *
     * @param user the user
     * @param role the role
     * @param may the may
     */
    public record Grant(String user, String role, Set<Permission> may) {

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            if (user != null) {
                builder.append("User ").append(user);
            } else {
                builder.append("Role ").append(role);
            }
            builder.append(" may=").append(may).append(']');
            return builder.toString();
        }
    }

    /**
     * Permissions for accessing and manipulating the pool.
     */
    public enum Permission {
        START("start"), STOP("stop"), RESET("reset"),
        ACCESS_CONSOLE("accessConsole");

        @SuppressWarnings("PMD.UseConcurrentHashMap")
        private static Map<String, Permission> reprs = new HashMap<>();

        static {
            for (var value : EnumSet.allOf(Permission.class)) {
                reprs.put(value.repr, value);
            }
        }

        private final String repr;

        Permission(String repr) {
            this.repr = repr;
        }

        /**
         * Create permission from representation in CRD.
         *
         * @param value the value
         * @return the permission
         */
        @SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
        public static Set<Permission> parse(String value) {
            if ("*".equals(value)) {
                return EnumSet.allOf(Permission.class);
            }
            return Set.of(reprs.get(value));
        }

        @Override
        public String toString() {
            return repr;
        }
    }

}
