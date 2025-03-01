/*
 * VM-Operator
 * Copyright (C) 2023,2025 Michael N. Lipp
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
import org.jdrupes.vmoperator.common.Constants.DisplaySecret;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpSetDisplayPassword;
import org.jdrupes.vmoperator.runner.qemu.commands.QmpSetPasswordExpiry;
import org.jdrupes.vmoperator.runner.qemu.events.ConfigureQemu;
import org.jdrupes.vmoperator.runner.qemu.events.MonitorCommand;
import org.jdrupes.vmoperator.runner.qemu.events.RunnerStateChange.RunState;
import org.jdrupes.vmoperator.runner.qemu.events.VmopAgentConnected;
import org.jdrupes.vmoperator.runner.qemu.events.VmopAgentLogIn;
import org.jdrupes.vmoperator.runner.qemu.events.VmopAgentLogOut;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.Event;
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
    private String loggedInUser;

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
        fire(new WatchFile(configDir.resolve(DisplaySecret.PASSWORD)));
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
        loggedInUser = event.configuration().vm.display.loggedInUser;
        configureLogin();
        if (event.runState() == RunState.STARTING) {
            configurePassword();
        }
    }

    /**
     * On vmop agent connected.
     *
     * @param event the event
     */
    @Handler
    public void onVmopAgentConnected(VmopAgentConnected event) {
        vmopAgentConnected = true;
        configureLogin();
    }

    private void configureLogin() {
        if (!vmopAgentConnected) {
            return;
        }
        Event<?> evt = loggedInUser != null
            ? new VmopAgentLogIn(loggedInUser)
            : new VmopAgentLogOut();
        fire(evt);
    }

    /**
     * Watch for changes of the password file.
     *
     * @param event the event
     */
    @Handler
    @SuppressWarnings("PMD.EmptyCatchBlock")
    public void onFileChanged(FileChanged event) {
        if (event.path().equals(configDir.resolve(DisplaySecret.PASSWORD))) {
            configurePassword();
        }
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
        return readFromFile(DisplaySecret.PASSWORD).map(password -> {
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
        readFromFile(DisplaySecret.EXPIRY).ifPresent(expiry -> {
            logger.fine(() -> "Updating expiry time to " + expiry);
            fire(
                new MonitorCommand(new QmpSetPasswordExpiry(protocol, expiry)));
        });
    }

    private Optional<String> readFromFile(String dataItem) {
        Path path = configDir.resolve(dataItem);
        String label = dataItem.replace('-', ' ');
        if (path.toFile().canRead()) {
            logger.finer(() -> "Found " + label);
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
