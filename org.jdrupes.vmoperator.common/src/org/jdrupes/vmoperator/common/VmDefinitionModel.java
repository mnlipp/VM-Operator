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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jdrupes.vmoperator.util.GsonPtr;

/**
 * Represents a VM definition.
 */
@SuppressWarnings("PMD.DataClass")
public class VmDefinitionModel extends K8sDynamicModel {

    /**
     * Permissions for accessing and manipulating the VM.
     */
    public enum Permission {
        START, STOP, ACCESS_CONSOLE;

        private static Map<String, Permission> reprs = Map.of("start", START,
            "stop", STOP, "accessConsole", ACCESS_CONSOLE);

        /**
         * Create permission from representation in CRD.
         *
         * @param value the value
         * @return the permission
         */
        public static Permission parse(String value) {
            return reprs.get(value);
        }
    }

    /**
     * Instantiates a new model from the JSON representation.
     *
     * @param delegate the gson instance to use for extracting structured data
     * @param json the JSON
     */
    public VmDefinitionModel(Gson delegate, JsonObject json) {
        super(delegate, json);
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
        return GsonPtr.to(data())
            .getAsListOf(JsonObject.class, "spec", "permissions")
            .stream().filter(p -> GsonPtr.to(p).getAsString("user")
                .map(u -> u.equals(user)).orElse(false)
                || GsonPtr.to(p).getAsString("role").map(roles::contains)
                    .orElse(false))
            .map(p -> GsonPtr.to(p).getAsListOf(JsonPrimitive.class, "may")
                .stream())
            .flatMap(Function.identity()).map(p -> p.getAsString())
            .map(Permission::parse).collect(Collectors.toSet());
    }

}