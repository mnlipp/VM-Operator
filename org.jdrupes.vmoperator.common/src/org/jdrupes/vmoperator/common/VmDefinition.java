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

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jdrupes.vmoperator.util.DataPath;

/**
 * Represents a VM definition.
 */
@SuppressWarnings({ "PMD.DataClass" })
public class VmDefinition {

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
     * Sets the status.
     *
     * @param status the status to set
     */
    public void setStatus(Map<String, Object> status) {
        this.status = status;
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
     * Return the requested VM state
     *
     * @return the string
     */
    public RequestedVmState vmState() {
        // TODO
        return DataPath.get(this, "spec", "vm", "state")
            .map(s -> "Running".equals(s) ? RequestedVmState.RUNNING
                : RequestedVmState.STOPPED)
            .orElse(RequestedVmState.STOPPED);
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
        return DataPath
            .<List<Map<String, Object>>> get(this, "spec", "permissions")
            .orElse(Collections.emptyList()).stream()
            .filter(p -> DataPath.get(p, "user").map(u -> u.equals(user))
                .orElse(false)
                || DataPath.get(p, "role").map(roles::contains).orElse(false))
            .map(p -> DataPath.<List<String>> get(p, "may")
                .orElse(Collections.emptyList()).stream())
            .flatMap(Function.identity())
            .map(Permission::parse).map(Set::stream)
            .flatMap(Function.identity()).collect(Collectors.toSet());
    }

    /**
     * Get the display password serial.
     *
     * @return the optional
     */
    public Optional<Long> displayPasswordSerial() {
        return DataPath.<Number> get(status(), "displayPasswordSerial")
            .map(Number::longValue);
    }
}
