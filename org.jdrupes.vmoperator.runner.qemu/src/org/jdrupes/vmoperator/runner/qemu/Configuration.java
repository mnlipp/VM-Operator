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

package org.jdrupes.vmoperator.runner.qemu;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.jdrupes.vmoperator.util.Dto;
import org.jdrupes.vmoperator.util.FsdUtils;

/**
 * The configuration information from the configuration file.
 */
@SuppressWarnings("PMD.ExcessivePublicCount")
class Configuration implements Dto {
    @SuppressWarnings("PMD.FieldNamingConventions")
    protected final Logger logger = Logger.getLogger(getClass().getName());

    @SuppressWarnings({ "PMD.UseConcurrentHashMap",
        "PMD.FieldNamingConventions", "PMD.VariableNamingConventions" })
    private static final Map<String, BigInteger> unitMap = new HashMap<>();
    @SuppressWarnings({ "PMD.FieldNamingConventions",
        "PMD.VariableNamingConventions" })
    private static final Pattern memorySize
        = Pattern.compile("\\s*(\\d+)\\s*([^\\s]*)");

    static {
        // SI units and common abbreviations
        BigInteger factor = BigInteger.ONE;
        unitMap.put("", factor);
        BigInteger scale = BigInteger.valueOf(1000);
        for (var unit : List.of("B", "kB", "MB", "GB", "TB", "PB", "EB")) {
            unitMap.put(unit, factor);
            factor = factor.multiply(scale);
        }
        // Binary units
        factor = BigInteger.valueOf(1024);
        scale = BigInteger.valueOf(1024);
        for (var unit : List.of("KiB", "MiB", "GiB", "TiB", "PiB", "EiB")) {
            unitMap.put(unit, factor);
            unitMap.put(unit.substring(0, 2), factor);
            factor = factor.multiply(scale);
        }
    }

    public Path dataDir;
    public Path runtimeDir;
    public String template;
    public boolean updateTemplate;
    public Path swtpmSocket;
    public Path monitorSocket;
    public Path firmwareRom;
    public Path firmwareVars;
    @SuppressWarnings("PMD.ShortVariable")
    public Vm vm;

    /**
     * Parses a memory size specification.
     *
     * @param amount the amount
     * @return the big integer
     */
    public static BigInteger parseMemory(Object amount) {
        if (amount == null) {
            return (BigInteger) amount;
        }
        if (amount instanceof BigInteger number) {
            return number;
        }
        if (amount instanceof Number number) {
            return BigInteger.valueOf(number.longValue());
        }
        var matcher = memorySize.matcher(amount.toString());
        if (!matcher.matches()) {
            throw new NumberFormatException(amount.toString());
        }
        var unit = unitMap.get(matcher.group(2));
        if (unit == null) {
            throw new NumberFormatException(amount.toString());
        }
        var number = matcher.group(1);
        return new BigInteger(number).multiply(unit);
    }

    /**
     * Subsection "vm".
     */
    @SuppressWarnings({ "PMD.ShortClassName", "PMD.TooManyFields",
        "PMD.DataClass" })
    public static class Vm implements Dto {
        public String name;
        public String uuid;
        public boolean useTpm;
        public boolean bootMenu;
        public String firmware = "uefi";
        public BigInteger maximumRam;
        public BigInteger currentRam;
        public String cpuModel = "host";
        public int maximumCpus = 1;
        public int currentCpus = 1;
        public int cpuSockets;
        public int diesPerSocket;
        public int coresPerDie;
        public int threadsPerCore;
        public String accelerator = "kvm";
        public String rtcBase = "utc";
        public String rtcClock = "rt";
        public int powerdownTimeout = 900;
        public Network[] network = { new Network() };
        public Drive[] drives;
        public Spice spice;

        /**
         * Convert value from JSON parser.
         *
         * @param value the new maximum ram
         */
        public void setMaximumRam(String value) {
            maximumRam = parseMemory(value);
        }

        /**
         * Convert value from JSON parser.
         *
         * @param value the new current ram
         */
        public void setCurrentRam(String value) {
            currentRam = parseMemory(value);
        }
    }

    /**
     * Subsection "network".
     */
    public static class Network implements Dto {
        public String type = "tap";
        public String bridge = "br0";
        public String device = "virtio-net";
        public String mac;
        public String net;
    }

    /**
     * Subsection "drive".
     */
    public static class Drive implements Dto {
        public String type;
        public Integer bootindex;
        public String device;
        public String file;
    }

    /**
     * Subsection "spice".
     */
    public static class Spice implements Dto {
        public int port = 5900;
        public int usbRedirects = 2;
    }

    /**
     * Check configuration.
     *
     * @return true, if successful
     */
    public boolean check() {
        if (vm == null || vm.name == null) {
            logger.severe(() -> "Configuration is missing mandatory entries.");
            return false;
        }
        if (!checkRuntimeDir() || !checkDataDir() || !checkUuid()) {
            return false;
        }

        // Adjust max cpus if necessary
        if (vm.currentCpus > vm.maximumCpus) {
            vm.maximumCpus = vm.currentCpus;
        }

        return true;
    }

    @SuppressWarnings("PMD.AvoidDeeplyNestedIfStmts")
    private boolean checkRuntimeDir() {
        // Runtime directory (sockets)
        if (runtimeDir == null) {
            runtimeDir = FsdUtils.runtimeDir(Runner.APP_NAME).resolve(vm.name);
            swtpmSocket = runtimeDir.resolve("swtpm-sock");
            monitorSocket = runtimeDir.resolve("monitor.sock");
        }
        if (!Files.exists(runtimeDir)) {
            runtimeDir.toFile().mkdirs();
        }
        if (!Files.isDirectory(runtimeDir) || !Files.isWritable(runtimeDir)) {
            logger.severe(() -> String.format(
                "Configured runtime directory \"%s\""
                    + " does not exist or isn't writable.",
                runtimeDir));
            return false;
        }
        return true;
    }

    @SuppressWarnings("PMD.AvoidDeeplyNestedIfStmts")
    private boolean checkDataDir() {
        // Data directory
        if (dataDir == null) {
            dataDir = FsdUtils.dataHome(Runner.APP_NAME).resolve(vm.name);
        }
        if (!Files.exists(dataDir)) {
            dataDir.toFile().mkdirs();
        }
        if (!Files.isDirectory(dataDir) || !Files.isWritable(dataDir)) {
            logger.severe(() -> String.format(
                "Configured data directory \"%s\""
                    + " does not exist or isn't writable.",
                dataDir));
            return false;
        }
        return true;
    }

    private boolean checkUuid() {
        // Explicitly configured uuid takes precedence.
        if (vm.uuid != null) {
            return true;
        }

        // Try to read stored uuid.
        Path uuidPath = dataDir.resolve("uuid.txt");
        if (Files.isReadable(uuidPath)) {
            try {
                var stored
                    = Files.lines(uuidPath, StandardCharsets.UTF_8).findFirst();
                if (stored.isPresent()) {
                    vm.uuid = stored.get();
                    return true;
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, e,
                    () -> "Stored uuid cannot be read: " + e.getMessage());
            }
        }

        // Generate new uuid
        vm.uuid = UUID.randomUUID().toString();
        try {
            Files.writeString(uuidPath, vm.uuid + "\n");
        } catch (IOException e) {
            logger.log(Level.WARNING, e,
                () -> "Cannot store uuid: " + e.getMessage());
        }

        return true;
    }
}