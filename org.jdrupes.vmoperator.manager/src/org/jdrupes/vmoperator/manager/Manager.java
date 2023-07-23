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
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.LogManager;
import org.jdrupes.vmoperator.util.FsdUtils;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.Components;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.Stop;
import org.jgrapes.io.NioDispatcher;

/**
 * The application class.
 */
public class Manager extends Component {

    /** The Constant APP_NAME. */
    public static final String APP_NAME = "vmoperator";
    private static Manager app;

    /**
     * Instantiates a new manager.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public Manager() throws IOException {
        // Prepare component tree
        attach(new NioDispatcher());
        attach(new VmWatcher(channel()));
        attach(new Reconciler(channel()));
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
            var path = FsdUtils.findConfigFile(Manager.APP_NAME,
                "logging.properties");
            if (path.isPresent()) {
                props = Files.newInputStream(path.get());
            } else {
                props = Manager.class.getResourceAsStream("logging.properties");
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
    public static void main(String[] args) throws Exception {
        // The Manager is the root component
        app = new Manager();

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
    }

}
