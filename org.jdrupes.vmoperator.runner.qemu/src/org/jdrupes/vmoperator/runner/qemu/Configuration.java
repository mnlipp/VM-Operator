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
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.jdrupes.vmoperator.common.Constants.APP_NAME;
import org.jdrupes.vmoperator.common.Convertions;
import org.jdrupes.vmoperator.util.Dto;
import org.jdrupes.vmoperator.util.FsdUtils;

/**
 * The configuration information from the configuration file.
 */
@SuppressWarnings({ "PMD.ExcessivePublicCount", "PMD.TooManyFields" })
public class Configuration implements Dto {
    private static final String CI_INSTANCE_ID = "instance-id";

    @SuppressWarnings("PMD.FieldNamingConventions")
    protected final Logger logger = Logger.getLogger(getClass().getName());

    /** Configuration timestamp. */
    public Instant asOf;

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

    /** The display password. */
    public boolean hasDisplayPassword;

    /** Optional cloud-init data. */
    public CloudInit cloudInit;

    /** If guest shutdown changes CRD .vm.state to "Stopped". */
    public boolean guestShutdownStops;

    /** Increments of the reset counter trigger a reset of the VM. */
    public Integer resetCounter;

    /** The vm. */
    @SuppressWarnings("PMD.ShortVariable")
    public Vm vm;

    /**
     * Subsection "cloud-init".
     */
    public static class CloudInit implements Dto {

        /** The meta data. */
        @SuppressWarnings("PMD.UseConcurrentHashMap")
        public Map<String, Object> metaData;

        /** The user data. */
        @SuppressWarnings("PMD.UseConcurrentHashMap")
        public Map<String, Object> userData;

        /** The network config. */
        @SuppressWarnings("PMD.UseConcurrentHashMap")
        public Map<String, Object> networkConfig;
    }

    /**
     * Subsection "vm".
     */
    @SuppressWarnings({ "PMD.ShortClassName", "PMD.TooManyFields",
        "PMD.DataClass", "PMD.AvoidDuplicateLiterals" })
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
        public int sockets;

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
            maximumRam = Convertions.parseMemory(value);
        }

        /**
         * Convert value from JSON parser.
         *
         * @param value the new current ram
         */
        public void setCurrentRam(String value) {
            currentRam = Convertions.parseMemory(value);
        }
    }

    /**
     * Subsection "network".
     */
    @SuppressWarnings("PMD.DataClass")
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
    @SuppressWarnings("PMD.DataClass")
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

        /** The number of outputs. */
        public int outputs = 1;

        /** The logged in user. */
        public String loggedInUser;

        /** The spice. */
        public Spice spice;
    }

    /**
     * Subsection "spice".
     */
    @SuppressWarnings("PMD.DataClass")
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
        checkCloudInit();

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
            var appDir = FsdUtils.runtimeDir(APP_NAME.replace("-", ""));
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
            runtimeDir = FsdUtils.runtimeDir(APP_NAME.replace("-", ""))
                .resolve(vm.name);
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
            dataDir
                = FsdUtils.dataHome(APP_NAME.replace("-", "")).resolve(vm.name);
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

    private void checkCloudInit() {
        if (cloudInit == null) {
            return;
        }

        // Provide default for instance-id
        if (cloudInit.metaData == null) {
            cloudInit.metaData = new HashMap<>();
        }
        if (!cloudInit.metaData.containsKey(CI_INSTANCE_ID)) {
            cloudInit.metaData.put(CI_INSTANCE_ID, "v" + asOf.getEpochSecond());
        }
    }
}