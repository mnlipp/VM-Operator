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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_NAME;
import org.jdrupes.vmoperator.util.FsdUtils;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.Components;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.Stop;
import org.jgrapes.io.NioDispatcher;
import org.jgrapes.util.FileSystemWatcher;
import org.jgrapes.util.YamlConfigurationStore;
import org.jgrapes.util.events.WatchFile;

/**
 * The application class.
 */
public class Operator extends Component {

    private static Operator app;

    /**
     * Instantiates a new manager.
     * @param cmdLine 
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public Operator(CommandLine cmdLine) throws IOException {
        // Prepare component tree
        attach(new NioDispatcher());
        attach(new FileSystemWatcher(channel()));
        attach(new Controller(channel()));

        // Configuration store with file in /etc/opt (default)
        File config = new File(cmdLine.getOptionValue('c',
            "/etc/opt/" + VM_OP_NAME + "/config.yaml"));
        // Don't rely on night config to produce a good exception
        // for this simple case
        if (!Files.isReadable(config.toPath())) {
            throw new IOException("Cannot read configuration file " + config);
        }
        attach(new YamlConfigurationStore(channel(), config, false));
        fire(new WatchFile(config.toPath()));
    }

    /**
     * On stop.
     *
     * @param event the event
     */
    @Handler(priority = -1000)
    public void onStop(Stop event) {
        logger.fine(() -> "Applictaion stopped.");
    }

    static {
        try {
            InputStream props;
            var path = FsdUtils.findConfigFile(Constants.APP_NAME,
                "logging.properties");
            if (path.isPresent()) {
                props = Files.newInputStream(path.get());
            } else {
                props
                    = Operator.class.getResourceAsStream("logging.properties");
            }
            LogManager.getLogManager().readConfiguration(props);
        } catch (IOException e) {
            e.printStackTrace(); // NOPMD
        }
    }

    /**
     * The main method.
     *
     * @param args the arguments
     * @throws Exception the exception
     */
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public static void main(String[] args) {
        try {
            Logger.getLogger(Operator.class.getName())
                .fine(() -> "Version: "
                    + Operator.class.getPackage().getImplementationVersion());
            CommandLineParser parser = new DefaultParser();
            // parse the command line arguments
            final Options options = new Options();
            options.addOption(new Option("c", "config", true, "The configura"
                + "tion file (defaults to /etc/opt/vmoperator/config.yaml)."));
            CommandLine cmd = parser.parse(options, args);
            // The Operator is the root component
            app = new Operator(cmd);

            // Prepare Stop
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    app.fire(new Stop(), Channel.BROADCAST);
                    Components.awaitExhaustion();
                } catch (InterruptedException e) {
                    // Cannot do anything about this.
                }
            }));

            // Start application
            Components.start(app);
        } catch (IOException | InterruptedException
                | org.apache.commons.cli.ParseException e) {
            Logger.getLogger(Operator.class.getName()).log(Level.SEVERE, e,
                () -> "Failed to start runner: " + e.getMessage());
        }
    }

}
