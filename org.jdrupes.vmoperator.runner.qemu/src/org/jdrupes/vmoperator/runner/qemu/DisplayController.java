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
import java.util.Optional;
import java.util.logging.Level;
import static org.jdrupes.vmoperator.common.Constants.DATA_DISPLAY_LOGIN;
import static org.jdrupes.vmoperator.common.Constants.DATA_DISPLAY_PASSWORD;
import static org.jdrupes.vmoperator.common.Constants.DATA_DISPLAY_USER;
import static org.jdrupes.vmoperator.common.Constants.DATA_PASSWORD_EXPIRY;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpSetDisplayPassword;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpSetPasswordExpiry;
import org.jdrupes.vmoperator.runner.qemu.events.ConfigureQemu;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorCommand;
import org.jdrupes.vmoperator.runner.qemu.events.RunnerStateChange.RunState;
import org.jdrupes.vmoperator.runner.qemu.events.VmopAgentConnected;
import org.jdrupes.vmoperator.runner.qemu.events.VmopAgentLogIn;
import org.jdrupes.vmoperator.runner.qemu.events.VmopAgentLogOut;
import org.jdrupes.vmoperator.runner.qemu.events.VmopAgentLoggedIn;
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

    private String currentPassword;
    private String protocol;
    private final Path configDir;
    private boolean vmopAgentConnected;
    private boolean userLoginRequested;

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
        fire(new WatchFile(configDir.resolve(DATA_DISPLAY_PASSWORD)));
    }

    /**
     * On vmop agent connected.
     *
     * @param event the event
     */
    @Handler
    public void onVmopAgentConnected(VmopAgentConnected event) {
        vmopAgentConnected = true;
    }

    /**
     * On configure qemu.
     *
     * @param event the event
     */
    @Handler
    public void onConfigureQemu(ConfigureQemu event) {
        if (event.runState() == RunState.TERMINATING) {
            return;
        }
        protocol
            = event.configuration().vm.display.spice != null ? "spice" : null;
        configureAccess(false);
    }

    /**
     * Watch for changes of the password file.
     *
     * @param event the event
     */
    @Handler
    @SuppressWarnings("PMD.EmptyCatchBlock")
    public void onFileChanged(FileChanged event) {
        if (event.path().equals(configDir.resolve(DATA_DISPLAY_PASSWORD))) {
            configureAccess(true);
        }
    }

    @SuppressWarnings("PMD.DataflowAnomalyAnalysis")
    private void configureAccess(boolean passwordChange) {
        var userLoginConfigured = readFromFile(DATA_DISPLAY_LOGIN)
            .map(Boolean::parseBoolean).orElse(false);
        if (!userLoginConfigured) {
            // Check if it was configured before and there may thus be an
            // active auto login
            if (userLoginRequested && vmopAgentConnected) {
                // Make sure to log out
                fire(new VmopAgentLogOut());
            }
            userLoginRequested = false;
            configurePassword();
            return;
        }

        // With user login configured, we have to make sure that the
        // user is logged in before we set the password and thus allow
        // access to the display.
        userLoginRequested = true;
        if (!vmopAgentConnected) {
            if (passwordChange) {
                logger.warning(() -> "Request for user login before "
                    + "VM operator agent has connected");
            }
            return;
        }

        var user = readFromFile(DATA_DISPLAY_USER);
        if (user.isEmpty()) {
            logger.warning(() -> "Login requested, but no user configured");
        }
        fire(new VmopAgentLogIn(user.get()).setAssociated(this, user.get()));
    }

    /**
     * On vmop agent logged in.
     *
     * @param event the event
     */
    @Handler
    public void onVmopAgentLoggedIn(VmopAgentLoggedIn event) {
        configurePassword();
    }

    private void configurePassword() {
        if (protocol == null) {
            return;
        }
        if (setDisplayPassword()) {
            setPasswordExpiry();
        }
    }

    private boolean setDisplayPassword() {
        return readFromFile(DATA_DISPLAY_PASSWORD).map(password -> {
            if (Objects.equals(this.currentPassword, password)) {
                return true;
            }
            this.currentPassword = password;
            logger.fine(() -> "Updating display password");
            fire(new MonitorCommand(
                new QmpSetDisplayPassword(protocol, password)));
            return true;
        }).orElse(false);
    }

    private void setPasswordExpiry() {
        readFromFile(DATA_PASSWORD_EXPIRY).ifPresent(expiry -> {
            logger.fine(() -> "Updating expiry time to " + expiry);
            fire(
                new MonitorCommand(new QmpSetPasswordExpiry(protocol, expiry)));
        });
    }

    private Optional<String> readFromFile(String dataItem) {
        Path path = configDir.resolve(dataItem);
        String label = dataItem.replace('-', ' ');
        if (path.toFile().canRead()) {
            logger.finer(() -> "Found display user");
            try {
                return Optional.ofNullable(Files.readString(path));
            } catch (IOException e) {
                logger.log(Level.WARNING, e, () -> "Cannot read " + label + ": "
                    + e.getMessage());
                return Optional.empty();
            }
        } else {
            logger.finer(() -> "No " + label);
            return Optional.empty();
        }
    }
}
