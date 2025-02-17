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

import io.kubernetes.client.util.Strings;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents internally used dynamic data associated with a
 * {@link VmDefinition}.
 */
public class VmExtraData {

    @SuppressWarnings("PMD.FieldNamingConventions")
    private static final Logger logger
        = Logger.getLogger(VmExtraData.class.getName());

    private final VmDefinition vmDef;
    private String nodeName = "";
    private List<String> nodeAddresses = Collections.emptyList();
    private long resetCount;

    /**
     * Initializes a new instance.
     *
     * @param vmDef the VM definition
     */
    public VmExtraData(VmDefinition vmDef) {
        this.vmDef = vmDef;
        vmDef.extra(this);
    }

    /**
     * Sets the node info.
     *
     * @param name the name
     * @param addresses the addresses
     * @return the VM extra data
     */
    public VmExtraData nodeInfo(String name, List<String> addresses) {
        nodeName = name;
        nodeAddresses = addresses;
        return this;
    }

    /**
     * Return the node name.
     *
     * @return the string
     */
    public String nodeName() {
        return nodeName;
    }

    /**
     * Sets the reset count.
     *
     * @param resetCount the reset count
     * @return the vm extra data
     */
    public VmExtraData resetCount(long resetCount) {
        this.resetCount = resetCount;
        return this;
    }

    /**
     * Returns the reset count.
     *
     * @return the long
     */
    public long resetCount() {
        return resetCount;
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
            logger
                .severe(() -> "Failed to find display IP for " + vmDef.name());
            return null;
        }
        var port = vmDef.<Number> fromVm("display", "spice", "port")
            .map(Number::longValue);
        if (port.isEmpty()) {
            logger
                .severe(() -> "No port defined for display of " + vmDef.name());
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
        vmDef.<String> fromVm("display", "spice", "proxyUrl")
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
        Optional<String> server = vmDef.fromVm("display", "spice", "server");
        if (server.isPresent()) {
            var srv = server.get();
            try {
                var addr = InetAddress.getByName(srv);
                logger.fine(() -> "Using IP address from CRD for "
                    + vmDef.metadata().getName() + ": " + addr);
                return Optional.of(addr);
            } catch (UnknownHostException e) {
                logger.log(Level.SEVERE, e, () -> "Invalid server address "
                    + srv + ": " + e.getMessage());
                return Optional.empty();
            }
        }
        var addrs = nodeAddresses.stream().map(a -> {
            try {
                return InetAddress.getByName(a);
            } catch (UnknownHostException e) {
                logger.warning(() -> "Invalid IP address: " + a);
                return null;
            }
        }).filter(Objects::nonNull).toList();
        logger.fine(
            () -> "Known IP addresses for " + vmDef.name() + ": " + addrs);
        return addrs.stream()
            .filter(a -> preferredIpVersion.isAssignableFrom(a.getClass()))
            .findFirst().or(() -> addrs.stream().findFirst());
    }

}
