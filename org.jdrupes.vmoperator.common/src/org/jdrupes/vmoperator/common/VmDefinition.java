/*
 * VM-Operator
 * Copyright (C) 2025 Michael N. Lipp
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1Condition;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jdrupes.vmoperator.common.Constants.Status;
import org.jdrupes.vmoperator.common.Constants.Status.Condition;
import org.jdrupes.vmoperator.common.Constants.Status.Condition.Reason;
import org.jdrupes.vmoperator.util.DataPath;

/**
 * Represents a VM definition.
 */
@SuppressWarnings({ "PMD.DataClass", "PMD.TooManyMethods",
    "PMD.CouplingBetweenObjects" })
public class VmDefinition extends K8sDynamicModel {

    @SuppressWarnings({ "PMD.FieldNamingConventions", "unused" })
    private static final Logger logger
        = Logger.getLogger(VmDefinition.class.getName());
    @SuppressWarnings("PMD.FieldNamingConventions")
    private static final Gson gson = new JSON().getGson();
    @SuppressWarnings("PMD.FieldNamingConventions")
    private static final ObjectMapper objectMapper
        = new ObjectMapper().registerModule(new JavaTimeModule());

    private final Model model;
    private VmExtraData extraData;

    /**
     * The VM state from the VM definition.
     */
    public enum RequestedVmState {
        STOPPED, RUNNING
    }

    /**
     * Permissions for accessing and manipulating the VM.
     */
    public enum Permission {
        START("start"), STOP("stop"), RESET("reset"),
        ACCESS_CONSOLE("accessConsole"), TAKE_CONSOLE("takeConsole");

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

        /**
         * To string.
         *
         * @return the string
         */
        @Override
        public String toString() {
            return repr;
        }
    }

    /**
     * Permissions granted to a user or role.
     *
     * @param user the user
     * @param role the role
     * @param may the may
     */
    public record Grant(String user, String role, Set<Permission> may) {

        /**
         * To string.
         *
         * @return the string
         */
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
     * The assignment information.
     *
     * @param pool the pool
     * @param user the user
     * @param lastUsed the last used
     */
    public record Assignment(String pool, String user, Instant lastUsed) {
    }

    /**
     * Instantiates a new vm definition.
     *
     * @param delegate the delegate
     * @param json the json
     */
    public VmDefinition(Gson delegate, JsonObject json) {
        super(delegate, json);
        model = gson.fromJson(json, Model.class);
    }

    /**
     * Gets the spec.
     *
     * @return the spec
     */
    public Map<String, Object> spec() {
        return model.getSpec();
    }

    /**
     * Get a value from the spec using {@link DataPath#get}.
     *
     * @param <T> the generic type
     * @param selectors the selectors
     * @return the value, if found
     */
    public <T> Optional<T> fromSpec(Object... selectors) {
        return DataPath.get(spec(), selectors);
    }

    /**
     * The pools that this VM belongs to.
     *
     * @return the list
     */
    public List<String> pools() {
        return this.<List<String>> fromSpec("pools")
            .orElse(Collections.emptyList());
    }

    /**
     * Get a value from the `spec().get("vm")` using {@link DataPath#get}.
     *
     * @param <T> the generic type
     * @param selectors the selectors
     * @return the value, if found
     */
    public <T> Optional<T> fromVm(Object... selectors) {
        return DataPath.get(spec(), "vm")
            .flatMap(vm -> DataPath.get(vm, selectors));
    }

    /**
     * Gets the status.
     *
     * @return the status
     */
    public Map<String, Object> status() {
        return model.getStatus();
    }

    /**
     * Get a value from the status using {@link DataPath#get}.
     *
     * @param <T> the generic type
     * @param selectors the selectors
     * @return the value, if found
     */
    public <T> Optional<T> fromStatus(Object... selectors) {
        return DataPath.get(status(), selectors);
    }

    /**
     * The assignment information.
     *
     * @return the optional
     */
    public Optional<Assignment> assignment() {
        return this.<Map<String, Object>> fromStatus(Status.ASSIGNMENT)
            .filter(m -> !m.isEmpty()).map(a -> new Assignment(
                a.get("pool").toString(), a.get("user").toString(),
                Instant.parse(a.get("lastUsed").toString())));
    }

    /**
     * Return a condition from the status.
     *
     * @param name the condition's name
     * @return the status, if the condition is defined
     */
    public Optional<V1Condition> condition(String name) {
        return this.<List<Map<String, Object>>> fromStatus("conditions")
            .orElse(Collections.emptyList()).stream()
            .filter(cond -> DataPath.get(cond, "type")
                .map(name::equals).orElse(false))
            .findFirst()
            .map(cond -> objectMapper.convertValue(cond, V1Condition.class));
    }

    /**
     * Return a condition's status.
     *
     * @param name the condition's name
     * @return the status, if the condition is defined
     */
    public Optional<Boolean> conditionStatus(String name) {
        return this.<List<Map<String, Object>>> fromStatus("conditions")
            .orElse(Collections.emptyList()).stream()
            .filter(cond -> DataPath.get(cond, "type")
                .map(name::equals).orElse(false))
            .findFirst().map(cond -> DataPath.get(cond, "status")
                .map("True"::equals).orElse(false));
    }

    /**
     * Return true if the console is in use.
     *
     * @return true, if successful
     */
    public boolean consoleConnected() {
        return conditionStatus("ConsoleConnected").orElse(false);
    }

    /**
     * Return the last known console user.
     *
     * @return the optional
     */
    public Optional<String> consoleUser() {
        return this.<String> fromStatus(Status.CONSOLE_USER);
    }

    /**
     * Set extra data (unknown to kubernetes).
     * @return the VM definition
     */
    /* default */ VmDefinition extra(VmExtraData extraData) {
        this.extraData = extraData;
        return this;
    }

    /**
     * Return the extra data.
     *
     * @return the data
     */
    public Optional<VmExtraData> extra() {
        return Optional.ofNullable(extraData);
    }

    /**
     * Returns the definition's name.
     *
     * @return the string
     */
    public String name() {
        return metadata().getName();
    }

    /**
     * Returns the definition's namespace.
     *
     * @return the string
     */
    public String namespace() {
        return metadata().getNamespace();
    }

    /**
     * Return the requested VM state.
     *
     * @return the string
     */
    public RequestedVmState vmState() {
        return fromVm("state")
            .map(s -> "Running".equals(s) ? RequestedVmState.RUNNING
                : RequestedVmState.STOPPED)
            .orElse(RequestedVmState.STOPPED);
    }

    /**
     * Collect all permissions for the given user with the given roles.
     * If permission "takeConsole" is granted, the result will also
     * contain "accessConsole" to simplify checks.
     *
     * @param user the user
     * @param roles the roles
     * @return the sets the
     */
    public Set<Permission> permissionsFor(String user,
            Collection<String> roles) {
        var result = this.<List<Map<String, Object>>> fromSpec("permissions")
            .orElse(Collections.emptyList()).stream()
            .filter(p -> DataPath.get(p, "user").map(u -> u.equals(user))
                .orElse(false)
                || DataPath.get(p, "role").map(roles::contains).orElse(false))
            .map(p -> DataPath.<List<String>> get(p, "may")
                .orElse(Collections.emptyList()).stream())
            .flatMap(Function.identity())
            .map(Permission::parse).map(Set::stream)
            .flatMap(Function.identity())
            .collect(Collectors.toCollection(HashSet::new));

        // Take console implies access console, simplify checks
        if (result.contains(Permission.TAKE_CONSOLE)) {
            result.add(Permission.ACCESS_CONSOLE);
        }
        return result;
    }

    /**
     * Check if the console is accessible. Always returns `true` if 
     * the VM is running and the permissions allow taking over the
     * console. Else, returns `true` if
     * 
     *   * the permissions allow access to the console and
     * 
     *   * the VM is running and
     * 
     *   * the console is currently unused or used by the given user and
     *     
     *   * if user login is requested, the given user is logged in.
     *
     * @param user the user
     * @param permissions the permissions
     * @return true, if successful
     */
    @SuppressWarnings("PMD.SimplifyBooleanReturns")
    public boolean consoleAccessible(String user, Set<Permission> permissions) {
        // Basic checks
        if (!conditionStatus(Condition.RUNNING).orElse(false)) {
            return false;
        }
        if (permissions.contains(Permission.TAKE_CONSOLE)) {
            return true;
        }
        if (!permissions.contains(Permission.ACCESS_CONSOLE)) {
            return false;
        }

        // If the console is in use by another user, deny access
        if (conditionStatus(Condition.CONSOLE_CONNECTED).orElse(false)
            && !consoleUser().map(cu -> cu.equals(user)).orElse(false)) {
            return false;
        }

        // If no login is requested, allow access, else check if user matches
        if (condition(Condition.USER_LOGGED_IN).map(V1Condition::getReason)
            .map(r -> Reason.NOT_REQUESTED.equals(r)).orElse(false)) {
            return true;
        }
        return user.equals(status().get(Status.LOGGED_IN_USER));
    }

    /**
     * Get the display password serial.
     *
     * @return the optional
     */
    public Optional<Long> displayPasswordSerial() {
        return this.<Number> fromStatus(Status.DISPLAY_PASSWORD_SERIAL)
            .map(Number::longValue);
    }

    /**
     * Hash code.
     *
     * @return the int
     */
    @Override
    public int hashCode() {
        return Objects.hash(metadata().getNamespace(), metadata().getName());
    }

    /**
     * Equals.
     *
     * @param obj the obj
     * @return true, if successful
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        VmDefinition other = (VmDefinition) obj;
        return Objects.equals(metadata().getNamespace(),
            other.metadata().getNamespace())
            && Objects.equals(metadata().getName(), other.metadata().getName());
    }

    /**
     * The Class Model.
     */
    public static class Model {

        private Map<String, Object> spec;
        private Map<String, Object> status;

        /**
         * Gets the spec.
         *
         * @return the spec
         */
        public Map<String, Object> getSpec() {
            return spec;
        }

        /**
         * Sets the spec.
         *
         * @param spec the spec to set
         */
        public void setSpec(Map<String, Object> spec) {
            this.spec = spec;
        }

        /**
         * Gets the status.
         *
         * @return the status
         */
        public Map<String, Object> getStatus() {
            return status;
        }

        /**
         * Sets the status.
         *
         * @param status the status to set
         */
        public void setStatus(Map<String, Object> status) {
            this.status = status;
        }

    }

}
