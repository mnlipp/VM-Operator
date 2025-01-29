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
import io.kubernetes.client.openapi.models.V1Condition;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Strings;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jdrupes.vmoperator.util.DataPath;

/**
 * Represents a VM definition.
 */
@SuppressWarnings({ "PMD.DataClass", "PMD.TooManyMethods" })
public class VmDefinition {

    @SuppressWarnings("PMD.FieldNamingConventions")
    private static final Logger logger
        = Logger.getLogger(VmDefinition.class.getName());
    @SuppressWarnings("PMD.FieldNamingConventions")
    private static final ObjectMapper objectMapper
        = new ObjectMapper().registerModule(new JavaTimeModule());

    private String kind;
    private String apiVersion;
    private V1ObjectMeta metadata;
    private Map<String, Object> spec;
    private Map<String, Object> status;
    private final Map<String, Object> extra = new ConcurrentHashMap<>();

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
     * Gets the kind.
     *
     * @return the kind
     */
    public String getKind() {
        return kind;
    }

    /**
     * Sets the kind.
     *
     * @param kind the kind to set
     */
    public void setKind(String kind) {
        this.kind = kind;
    }

    /**
     * Gets the api version.
     *
     * @return the apiVersion
     */
    public String getApiVersion() {
        return apiVersion;
    }

    /**
     * Sets the api version.
     *
     * @param apiVersion the apiVersion to set
     */
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    /**
     * Gets the metadata.
     *
     * @return the metadata
     */
    public V1ObjectMeta getMetadata() {
        return metadata;
    }

    /**
     * Gets the metadata.
     *
     * @return the metadata
     */
    public V1ObjectMeta metadata() {
        return metadata;
    }

    /**
     * Sets the metadata.
     *
     * @param metadata the metadata to set
     */
    public void setMetadata(V1ObjectMeta metadata) {
        this.metadata = metadata;
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
     * Gets the spec.
     *
     * @return the spec
     */
    public Map<String, Object> getSpec() {
        return spec;
    }

    /**
     * Gets the spec.
     *
     * @return the spec
     */
    public Map<String, Object> spec() {
        return spec;
    }

    /**
     * Get a value from the spec using {@link DataPath#get}.
     *
     * @param <T> the generic type
     * @param selectors the selectors
     * @return the value, if found
     */
    public <T> Optional<T> fromSpec(Object... selectors) {
        return DataPath.get(spec, selectors);
    }

    /**
     * Get a value from the `spec().get("vm")` using {@link DataPath#get}.
     *
     * @param <T> the generic type
     * @param selectors the selectors
     * @return the value, if found
     */
    public <T> Optional<T> fromVm(Object... selectors) {
        return DataPath.get(spec, "vm")
            .flatMap(vm -> DataPath.get(vm, selectors));
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
     * Gets the status.
     *
     * @return the status
     */
    public Map<String, Object> status() {
        return status;
    }

    /**
     * Get a value from the status using {@link DataPath#get}.
     *
     * @param <T> the generic type
     * @param selectors the selectors
     * @return the value, if found
     */
    public <T> Optional<T> fromStatus(Object... selectors) {
        return DataPath.get(status, selectors);
    }

    /**
     * Sets the status.
     *
     * @param status the status to set
     */
    public void setStatus(Map<String, Object> status) {
        this.status = status;
    }

    /**
     * The pool that the VM was taken from.
     *
     * @return the optional
     */
    public Optional<String> assignedFrom() {
        return fromStatus("assignment", "pool");
    }

    /**
     * The user that the VM was assigned to.
     *
     * @return the optional
     */
    public Optional<String> assignedTo() {
        return fromStatus("assignment", "user");
    }

    /**
     * Last usage of assigned VM.
     *
     * @return the optional
     */
    public Optional<Instant> assignmentLastUsed() {
        return this.<String> fromStatus("assignment", "lastUsed")
            .map(Instant::parse);
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
        return this.<String> fromStatus("consoleUser");
    }

    /**
     * Set extra data (locally used, unknown to kubernetes).
     *
     * @param property the property
     * @param value the value
     * @return the VM definition
     */
    public VmDefinition extra(String property, Object value) {
        extra.put(property, value);
        return this;
    }

    /**
     * Return extra data.
     *
     * @param <T> the generic type
     * @param property the property
     * @return the object
     */
    @SuppressWarnings("unchecked")
    public <T> T extra(String property) {
        return (T) extra.get(property);
    }

    /**
     * Returns the definition's name.
     *
     * @return the string
     */
    public String name() {
        return metadata.getName();
    }

    /**
     * Returns the definition's namespace.
     *
     * @return the string
     */
    public String namespace() {
        return metadata.getNamespace();
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
     * Check if the console is accessible. Returns true if the console is
     * currently unused, used by the given user or if the permissions
     * allow taking over the console. 
     *
     * @param user the user
     * @param permissions the permissions
     * @return true, if successful
     */
    public boolean consoleAccessible(String user, Set<Permission> permissions) {
        return !conditionStatus("ConsoleConnected").orElse(true)
            || consoleUser().map(cu -> cu.equals(user)).orElse(true)
            || permissions.contains(VmDefinition.Permission.TAKE_CONSOLE);
    }

    /**
     * Get the display password serial.
     *
     * @return the optional
     */
    public Optional<Long> displayPasswordSerial() {
        return this.<Number> fromStatus("displayPasswordSerial")
            .map(Number::longValue);
    }

    /**
     * Create a connection file.
     *
     * @param password the password
     * @param preferredIpVersion the preferred IP version
     * @param deleteConnectionFile the delete connection file
     * @return the string
     */
    public String connectionFile(String password,
            Class<?> preferredIpVersion, boolean deleteConnectionFile) {
        var addr = displayIp(preferredIpVersion);
        if (addr.isEmpty()) {
            logger.severe(() -> "Failed to find display IP for " + name());
            return null;
        }
        var port = this.<Number> fromVm("display", "spice", "port")
            .map(Number::longValue);
        if (port.isEmpty()) {
            logger.severe(() -> "No port defined for display of " + name());
            return null;
        }
        StringBuffer data = new StringBuffer(100)
            .append("[virt-viewer]\ntype=spice\nhost=")
            .append(addr.get().getHostAddress()).append("\nport=")
            .append(port.get().toString())
            .append('\n');
        if (password != null) {
            data.append("password=").append(password).append('\n');
        }
        this.<String> fromVm("display", "spice", "proxyUrl")
            .ifPresent(u -> {
                if (!Strings.isNullOrEmpty(u)) {
                    data.append("proxy=").append(u).append('\n');
                }
            });
        if (deleteConnectionFile) {
            data.append("delete-this-file=1\n");
        }
        return data.toString();
    }

    private Optional<InetAddress> displayIp(Class<?> preferredIpVersion) {
        Optional<String> server = fromVm("display", "spice", "server");
        if (server.isPresent()) {
            var srv = server.get();
            try {
                var addr = InetAddress.getByName(srv);
                logger.fine(() -> "Using IP address from CRD for "
                    + getMetadata().getName() + ": " + addr);
                return Optional.of(addr);
            } catch (UnknownHostException e) {
                logger.log(Level.SEVERE, e, () -> "Invalid server address "
                    + srv + ": " + e.getMessage());
                return Optional.empty();
            }
        }
        var addrs = Optional.<List<String>> ofNullable(
            extra("nodeAddresses")).orElse(Collections.emptyList()).stream()
            .map(a -> {
                try {
                    return InetAddress.getByName(a);
                } catch (UnknownHostException e) {
                    logger.warning(() -> "Invalid IP address: " + a);
                    return null;
                }
            }).filter(a -> a != null).toList();
        logger.fine(() -> "Known IP addresses for " + name() + ": " + addrs);
        return addrs.stream()
            .filter(a -> preferredIpVersion.isAssignableFrom(a.getClass()))
            .findFirst().or(() -> addrs.stream().findFirst());
    }

    /**
     * Hash code.
     *
     * @return the int
     */
    @Override
    public int hashCode() {
        return Objects.hash(metadata.getNamespace(), metadata.getName());
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
        return Objects.equals(metadata.getNamespace(),
            other.metadata.getNamespace())
            && Objects.equals(metadata.getName(), other.metadata.getName());
    }

}
