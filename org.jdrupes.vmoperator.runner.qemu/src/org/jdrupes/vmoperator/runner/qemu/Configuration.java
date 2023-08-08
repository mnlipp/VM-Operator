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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
public class Configuration implements Dto {
    @SuppressWarnings("PMD.FieldNamingConventions")
    protected final Logger logger = Logger.getLogger(getClass().getName());

    @SuppressWarnings({ "PMD.UseConcurrentHashMap",
        "PMD.FieldNamingConventions", "PMD.VariableNamingConventions" })
    private static final Map<String, BigInteger> unitMap = new HashMap<>();
    @SuppressWarnings({ "PMD.FieldNamingConventions",
        "PMD.VariableNamingConventions" })
    private static final Pattern memorySize
        = Pattern.compile("^\\s*(\\d+(\\.\\d+)?)\\s*([A-Za-z]*)\\s*");

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

    /** The data dir. */
    public Path dataDir;

    /** The runtime dir. */
    public Path runtimeDir;

    /** The template. */
    public String template;

    /** The update template. */
    public boolean updateTemplate;

    /** The swtpm socket. */
    public Path swtpmSocket;

    /** The monitor socket. */
    public Path monitorSocket;

    /** The firmware rom. */
    public Path firmwareRom;

    /** The firmware vars. */
    public Path firmwareVars;

    /** The vm. */
    @SuppressWarnings("PMD.ShortVariable")
    public Vm vm;

    /**
     * Parses a memory size specification.
     *
     * @param amount the amount
     * @return the big integer
     */
    @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
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
        var unit = BigInteger.ONE;
        if (matcher.group(3) != null) {
            unit = unitMap.get(matcher.group(3));
            if (unit == null) {
                throw new NumberFormatException("Illegal unit \""
                    + matcher.group(3) + "\" in \"" + amount.toString() + "\"");
            }
        }
        var number = matcher.group(1);
        return new BigDecimal(number).multiply(new BigDecimal(unit))
            .toBigInteger();
    }

    /**
     * Subsection "vm".
     */
    @SuppressWarnings({ "PMD.ShortClassName", "PMD.TooManyFields",
        "PMD.DataClass" })
    public static class Vm implements Dto {

        /** The name. */
        public String name;

        /** The uuid. */
        public String uuid;

        /** The use tpm. */
        public boolean useTpm;

        /** The boot menu. */
        public boolean bootMenu;

        /** The firmware. */
        public String firmware = "uefi";

        /** The maximum ram. */
        public BigInteger maximumRam;

        /** The current ram. */
        public BigInteger currentRam;

        /** The cpu model. */
        public String cpuModel = "host";

        /** The maximum cpus. */
        public int maximumCpus = 1;

        /** The current cpus. */
        public int currentCpus = 1;

        /** The cpu sockets. */
        public int cpuSockets;

        /** The dies per socket. */
        public int diesPerSocket;

        /** The cores per die. */
        public int coresPerDie;

        /** The threads per core. */
        public int threadsPerCore;

        /** The accelerator. */
        public String accelerator = "kvm";

        /** The rtc base. */
        public String rtcBase = "utc";

        /** The rtc clock. */
        public String rtcClock = "rt";

        /** The powerdown timeout. */
        public int powerdownTimeout = 900;

        /** The network. */
        public Network[] network = { new Network() };

        /** The drives. */
        public Drive[] drives = new Drive[0];

        /** The display. */
        public Display display;

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

        /** The type. */
        public String type = "tap";

        /** The bridge. */
        public String bridge;

        /** The device. */
        public String device = "virtio-net";

        /** The mac. */
        public String mac;

        /** The net. */
        public String net;
    }

    /**
     * Subsection "drive".
     */
    public static class Drive implements Dto {

        /** The type. */
        public String type;

        /** The bootindex. */
        public Integer bootindex;

        /** The device. */
        public String device;

        /** The file. */
        public String file;

        /** The resource. */
        public String resource;
    }

    /**
     * The Class Display.
     */
    public static class Display implements Dto {
        public Spice spice;
    }

    /**
     * Subsection "spice".
     */
    public static class Spice implements Dto {

        /** The port. */
        public int port = 5900;

        /** The ticket. */
        public String ticket;

        /** The streaming video. */
        public String streamingVideo;

        /** The usb redirects. */
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

        checkDrives();

        return true;
    }

    @SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
    private void checkDrives() {
        for (Drive drive : vm.drives) {
            if (drive.file != null || drive.device != null
                || "ide-cd".equals(drive.type)) {
                continue;
            }
            if (drive.resource == null) {
                logger.severe(
                    () -> "Drive configuration is missing its resource.");

            }
            if (Files.isRegularFile(Path.of(drive.resource))) {
                drive.file = drive.resource;
            } else {
                drive.device = drive.resource;
            }
        }
    }

    @SuppressWarnings("PMD.AvoidDeeplyNestedIfStmts")
    private boolean checkRuntimeDir() {
        // Runtime directory (sockets etc.)
        if (runtimeDir == null) {
            var appDir = FsdUtils.runtimeDir(Runner.APP_NAME);
            if (!Files.exists(appDir) && appDir.toFile().mkdirs()) {
                try {
                    // When appDir is derived from XDG_RUNTIME_DIR
                    // the latter should already have these permissions,
                    // but let's be on the safe side.
                    Files.setPosixFilePermissions(appDir,
                        Set.of(PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE));
                } catch (IOException e) {
                    logger.warning(() -> String.format(
                        "Cannot set permissions rwx------ on \"%s\".",
                        runtimeDir));
                }
            }
            runtimeDir = FsdUtils.runtimeDir(Runner.APP_NAME).resolve(vm.name);
            runtimeDir.toFile().mkdir();
            swtpmSocket = runtimeDir.resolve("swtpm-sock");
            monitorSocket = runtimeDir.resolve("monitor.sock");
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