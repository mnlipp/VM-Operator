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

package org.jdrupes.vmoperator.manager;

import java.io.IOException;
import java.util.Map;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.util.events.ConfigurationUpdate;
import org.jgrapes.util.events.InitialConfiguration;

/**
 * The application class.
 */
public class Controller extends Component {

//    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    /**
     * Instantiates a new manager.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public Controller(Channel componentChannel) {
        super(componentChannel);
        // Prepare component tree
        attach(new VmWatcher(channel()));
        attach(new Reconciler(channel()));
    }

    /**
     * On configuration update.
     *
     * @param event the event
     */
    @Handler
    public void onConfigurationUpdate(ConfigurationUpdate event) {
        event.structured(componentPath()).ifPresent(c -> {
            if (event instanceof InitialConfiguration) {
                processInitialConfiguration(c);
            }
        });
    }

    private void processInitialConfiguration(
            Map<String, Object> runnerConfiguration) {
//        try {
//            config = mapper.convertValue(runnerConfiguration,
//                Configuration.class);
//            if (!config.check()) {
//                // Invalid configuration, not used, problems already logged.
//                config = null;
//            }
//
//            // Prepare firmware files and add to config
//            setFirmwarePaths();
//
//            // Obtain more context data from template
//            var tplData = dataFromTemplate();
//            swtpmDefinition = Optional.ofNullable(tplData.get("swtpm"))
//                .map(d -> new CommandDefinition("swtpm", d)).orElse(null);
//            qemuDefinition = Optional.ofNullable(tplData.get("qemu"))
//                .map(d -> new CommandDefinition("qemu", d)).orElse(null);
//
//            // Forward some values to child components
//            qemuMonitor.configure(config.monitorSocket,
//                config.vm.powerdownTimeout);
//        } catch (IllegalArgumentException | IOException | TemplateException e) {
//            logger.log(Level.SEVERE, e, () -> "Invalid configuration: "
//                + e.getMessage());
//            // Don't use default configuration
//            config = null;
//        }
    }
}
