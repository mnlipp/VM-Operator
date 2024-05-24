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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpSetDisplayPassword;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpSetPasswordExpiry;
import org.jdrupes.vmoperator.runner.qemu.events.ConfigureQemu;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorCommand;
import org.jdrupes.vmoperator.runner.qemu.events.RunnerStateChange.State;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.util.events.FileChanged;
import org.jgrapes.util.events.WatchFile;

/**
 * The Class DisplayController.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class DisplayController extends Component {

    public static final String DISPLAY_PASSWORD_FILE = "display-password";
    public static final String PASSWORD_EXPIRY_FILE = "password-expiry";
    private String currentPassword;
    private String protocol;
    private final Path configDir;

    /**
     * Instantiates a new Display controller.
     *
     * @param componentChannel the component channel
     * @param configDir 
     */
    @SuppressWarnings({ "PMD.AssignmentToNonFinalStatic",
        "PMD.ConstructorCallsOverridableMethod" })
    public DisplayController(Channel componentChannel, Path configDir) {
        super(componentChannel);
        this.configDir = configDir;
        fire(new WatchFile(configDir.resolve(DISPLAY_PASSWORD_FILE)));
    }

    /**
     * On configure qemu.
     *
     * @param event the event
     */
    @Handler
    public void onConfigureQemu(ConfigureQemu event) {
        if (event.state() == State.TERMINATING) {
            return;
        }
        protocol
            = event.configuration().vm.display.spice != null ? "spice" : null;
        updatePassword();
    }

    /**
     * Watch for changes of the password file.
     *
     * @param event the event
     */
    @Handler
    @SuppressWarnings("PMD.EmptyCatchBlock")
    public void onFileChanged(FileChanged event) {
        if (event.path().equals(configDir.resolve(DISPLAY_PASSWORD_FILE))) {
            updatePassword();
        }
    }

    @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
    private void updatePassword() {
        if (protocol == null) {
            return;
        }
        if (setDisplayPassword()) {
            setPasswordExpiry();
        }
    }

    private boolean setDisplayPassword() {
        String password;
        Path dpPath = configDir.resolve(DISPLAY_PASSWORD_FILE);
        if (dpPath.toFile().canRead()) {
            logger.finer(() -> "Found display password");
            try {
                password = Files.readString(dpPath);
            } catch (IOException e) {
                logger.log(Level.WARNING, e, () -> "Cannot read display"
                    + " password: " + e.getMessage());
                return false;
            }
        } else {
            logger.finer(() -> "No display password");
            return false;
        }

        if (Objects.equals(this.currentPassword, password)) {
            return false;
        }
        logger.fine(() -> "Updating display password");
        fire(new MonitorCommand(new QmpSetDisplayPassword(protocol, password)));
        return true;
    }

    private void setPasswordExpiry() {
        Path pePath = configDir.resolve(PASSWORD_EXPIRY_FILE);
        if (!pePath.toFile().canRead()) {
            return;
        }
        logger.finer(() -> "Found expiry time");
        String expiry;
        try {
            expiry = Files.readString(pePath);
        } catch (IOException e) {
            logger.log(Level.WARNING, e, () -> "Cannot read expiry"
                + " time: " + e.getMessage());
            return;
        }
        logger.fine(() -> "Updating expiry time");
        fire(new MonitorCommand(new QmpSetPasswordExpiry(protocol, expiry)));
    }

}
