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

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.util.Config;
import java.io.IOException;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.Start;

/**
 * The application class.
 */
public class Controller extends Component {

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
     * Handle the start event. Has higher priority because it configures
     * the default Kubernetes client.
     *
     * @param event the event
     * @throws IOException 
     * @throws ApiException 
     */
    @Handler(priority = 100)
    public void onStart(Start event) throws IOException, ApiException {
        var client = Config.defaultClient();
        Configuration.setDefaultApiClient(client);
    }
}
