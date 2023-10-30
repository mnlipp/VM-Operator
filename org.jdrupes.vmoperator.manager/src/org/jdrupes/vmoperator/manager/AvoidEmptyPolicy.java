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
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.webconsole.base.Conlet;
import org.jgrapes.webconsole.base.ConsoleConnection;
import org.jgrapes.webconsole.base.events.AddConletRequest;
import org.jgrapes.webconsole.base.events.ConsoleConfigured;
import org.jgrapes.webconsole.base.events.ConsoleReady;
import org.jgrapes.webconsole.base.events.RenderConlet;

/**
 * 
 */
public class AvoidEmptyPolicy extends Component {

    private final String renderedFlagName = getClass().getName() + ".rendered";

    /**
     * Creates a new component with its channel set to the given channel.
     * 
     * @param componentChannel
     */
    public AvoidEmptyPolicy(Channel componentChannel) {
        super(componentChannel);
    }

    /**
     * On console ready.
     *
     * @param event the event
     * @param connection the connection
     */
    @Handler
    public void onConsoleReady(ConsoleReady event,
            ConsoleConnection connection) {
        connection.session().put(renderedFlagName, false);
    }

    /**
     * On render conlet.
     *
     * @param event the event
     * @param connection the connection
     */
    @Handler
    public void onRenderConlet(RenderConlet event,
            ConsoleConnection connection) {
        connection.session().put(renderedFlagName, true);
    }

    /**
     * On console configured.
     *
     * @param event the event
     * @param connection the console connection
     * @throws InterruptedException the interrupted exception
     */
    @Handler
    public void onConsoleConfigured(ConsoleConfigured event,
            ConsoleConnection connection) throws InterruptedException,
            IOException {
        if ((Boolean) connection.session().getOrDefault(
            renderedFlagName, false)) {
            return;
        }
        fire(new AddConletRequest(event.event().event().renderSupport(),
            "org.jdrupes.vmoperator.vmconlet.VmConlet",
            Conlet.RenderMode
                .asSet(Conlet.RenderMode.Preview, Conlet.RenderMode.View)),
            connection);
    }

}
