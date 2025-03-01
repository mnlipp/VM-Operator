/*
 * VM-Operator
 * Copyright (C) 2024 Michael N. Lipp
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

import com.google.gson.JsonObject;
import io.kubernetes.client.apimachinery.GroupVersionKind;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.EventsV1Event;
import java.io.IOException;
import java.util.logging.Level;
import static org.jdrupes.vmoperator.common.Constants.APP_NAME;
import org.jdrupes.vmoperator.common.Constants.Crd;
import org.jdrupes.vmoperator.common.K8s;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.VmDefinitionStub;
import org.jdrupes.vmoperator.runner.qemu.events.Exit;
import org.jdrupes.vmoperator.runner.qemu.events.SpiceDisconnectedEvent;
import org.jdrupes.vmoperator.runner.qemu.events.SpiceInitializedEvent;
import org.jgrapes.core.Channel;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.Start;

/**
 * A (sub)component that updates the console status in the CR status.
 * Created as child of {@link StatusUpdater}.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class ConsoleTracker extends VmDefUpdater {

    private VmDefinitionStub vmStub;
    private String mainChannelClientHost;
    private long mainChannelClientPort;

    /**
     * Instantiates a new status updater.
     *
     * @param componentChannel the component channel
     */
    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
    public ConsoleTracker(Channel componentChannel) {
        super(componentChannel);
        apiClient = (K8sClient) io.kubernetes.client.openapi.Configuration
            .getDefaultApiClient();
    }

    /**
     * Handle the start event.
     *
     * @param event the event
     * @throws IOException 
     * @throws ApiException 
     */
    @Handler
    public void onStart(Start event) {
        if (namespace == null) {
            return;
        }
        try {
            vmStub = VmDefinitionStub.get(apiClient,
                new GroupVersionKind(Crd.GROUP, "", Crd.KIND_VM),
                namespace, vmName);
        } catch (ApiException e) {
            logger.log(Level.SEVERE, e,
                () -> "Cannot access VM object, terminating.");
            event.cancel(true);
            fire(new Exit(1));
        }
    }

    /**
     * On spice connected.
     *
     * @param event the event
     * @throws ApiException the api exception
     */
    @Handler
    @SuppressWarnings({ "PMD.AvoidLiteralsInIfCondition",
        "PMD.AvoidDuplicateLiterals" })
    public void onSpiceInitialized(SpiceInitializedEvent event)
            throws ApiException {
        if (vmStub == null) {
            return;
        }

        // Only process connections using main channel.
        if (event.channelType() != 1) {
            return;
        }
        mainChannelClientHost = event.clientHost();
        mainChannelClientPort = event.clientPort();
        vmStub.updateStatus(from -> {
            JsonObject status = from.statusJson();
            status.addProperty("consoleClient", event.clientHost());
            updateCondition(from, status, "ConsoleConnected", true, "Connected",
                "Connection from " + event.clientHost());
            return status;
        });

        // Log event
        var evt = new EventsV1Event()
            .reportingController(Crd.GROUP + "/" + APP_NAME)
            .action("ConsoleConnectionUpdate")
            .reason("Connection from " + event.clientHost());
        K8s.createEvent(apiClient, vmStub.model().get(), evt);
    }

    /**
     * On spice disconnected.
     *
     * @param event the event
     * @throws ApiException the api exception
     */
    @Handler
    @SuppressWarnings("PMD.AvoidDuplicateLiterals")
    public void onSpiceDisconnected(SpiceDisconnectedEvent event)
            throws ApiException {
        if (vmStub == null) {
            return;
        }

        // Only process disconnects from main channel.
        if (!event.clientHost().equals(mainChannelClientHost)
            || event.clientPort() != mainChannelClientPort) {
            return;
        }
        vmStub.updateStatus(from -> {
            JsonObject status = from.statusJson();
            status.addProperty("consoleClient", "");
            updateCondition(from, status, "ConsoleConnected", false,
                "Disconnected", event.clientHost() + " has disconnected");
            return status;
        });

        // Log event
        var evt = new EventsV1Event()
            .reportingController(Crd.GROUP + "/" + APP_NAME)
            .action("ConsoleConnectionUpdate")
            .reason("Disconnected from " + event.clientHost());
        K8s.createEvent(apiClient, vmStub.model().get(), evt);
    }
}
