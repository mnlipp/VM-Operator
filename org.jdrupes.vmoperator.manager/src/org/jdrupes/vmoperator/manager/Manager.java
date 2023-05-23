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
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.Components;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.Start;
import org.jgrapes.core.events.Stop;
import org.jgrapes.io.NioDispatcher;

public class Manager extends Component {

    private static Manager app;

    public Manager() throws IOException {
        // Attach a general nio dispatcher
        attach(new NioDispatcher());
    }

    /**
     * Handle the start event.
     *
     * @param event the event
     */
    @Handler
    public void onStart(Start event) {
        System.out.println("Hello World!");
    }

    @Handler
    public void onStop(Stop event) {
        System.out.println("(Done.)");
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
