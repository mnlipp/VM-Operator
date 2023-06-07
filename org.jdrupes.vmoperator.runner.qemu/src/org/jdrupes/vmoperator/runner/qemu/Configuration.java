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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jdrupes.vmoperator.util.Dto;

/**
 * The configuration information from the configuration file.
 */
class Configuration implements Dto {
    @SuppressWarnings("PMD.FieldNamingConventions")
    protected final Logger logger = Logger.getLogger(getClass().getName());

    public static final Object BOOT_MODE_UEFI = "uefi";
    public static final Object BOOT_MODE_SECURE = "secure";

    public String dataDir;
    public String runtimeDir;
    public String template;
    public boolean updateTemplate;
    public Path swtpmSocket;
    public Path monitorSocket;
    public Path firmwareRom;
    public Path firmwareFlash;
    @SuppressWarnings("PMD.ShortVariable")
    public Vm vm;

    /**
     * Subsection "vm".
     */
    @SuppressWarnings({ "PMD.ShortClassName", "PMD.TooManyFields" })
    public static class Vm implements Dto {
        public String name;
        public String uuid;
        public boolean useTpm;
        public String bootMode = "uefi";
        public String maximumRam;
        public String currentRam;
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
        public int powerdownTimeout = 60;
        public Network[] network = { new Network() };
        public Drive[] drives;
        public Spice spice;
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
            runtimeDir = System.getenv("XDG_RUNTIME_DIR");
            if (runtimeDir == null) {
                runtimeDir = "/tmp";
                if (System.getenv("USER") != null) {
                    runtimeDir += "/" + System.getenv("USER");
                }
            }
            runtimeDir += "/vmrunner/" + vm.name;
            swtpmSocket = Path.of(runtimeDir, "swtpm-sock");
            monitorSocket = Path.of(runtimeDir, "monitor.sock");
        }
        Path runtimePath = Path.of(runtimeDir);
        if (!Files.exists(runtimePath)) {
            runtimePath.toFile().mkdirs();
        }
        if (!Files.isDirectory(runtimePath) || !Files.isWritable(runtimePath)) {
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
            dataDir = System.getenv("XDG_DATA_HOME");
            if (dataDir == null) {
                dataDir = ".";
                if (System.getenv("HOME") != null) {
                    dataDir = System.getenv("HOME") + "/.local/share";
                }
            }
            dataDir += "/vmrunner/" + vm.name;
        }
        Path dataPath = Path.of(dataDir);
        if (!Files.exists(dataPath)) {
            dataPath.toFile().mkdirs();
        }
        if (!Files.isDirectory(dataPath) || !Files.isWritable(dataPath)) {
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
        Path uuidPath = Path.of(dataDir, "uuid.txt");
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