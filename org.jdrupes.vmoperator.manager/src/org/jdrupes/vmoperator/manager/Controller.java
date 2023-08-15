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
import java.io.IOException;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.Start;

/**
 * Implements a controller as defined in the
 * [Operator Whitepaper](https://github.com/cncf/tag-app-delivery/blob/eece8f7307f2970f46f100f51932db106db46968/operator-wg/whitepaper/Operator-WhitePaper_v1-0.md#operator-components-in-kubernetes).
 */
public class Controller extends Component {

    /**
     * Creates a new instance.
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
        // Make sure to use thread specific client
        // https://github.com/kubernetes-client/java/issues/100
        Configuration.setDefaultApiClient(null);
    }
}
