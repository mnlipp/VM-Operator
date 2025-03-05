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

import com.google.gson.JsonObject;
import io.kubernetes.client.apimachinery.GroupVersionKind;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.logging.Level;
import org.jdrupes.vmoperator.common.Constants.Crd;
import org.jdrupes.vmoperator.common.Constants.Status;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.K8sDynamicStub;
import org.jdrupes.vmoperator.common.VmDefinitionStub;
import org.jdrupes.vmoperator.manager.events.ChannelManager;
import org.jdrupes.vmoperator.manager.events.Exit;
import org.jdrupes.vmoperator.manager.events.ModifyVm;
import org.jdrupes.vmoperator.manager.events.UpdateAssignment;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
import org.jdrupes.vmoperator.util.GsonPtr;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.HandlingError;
import org.jgrapes.core.events.Start;
import org.jgrapes.util.events.ConfigurationUpdate;

/**
 * Implements a controller as defined in the
 * [Operator Whitepaper](https://github.com/cncf/tag-app-delivery/blob/eece8f7307f2970f46f100f51932db106db46968/operator-wg/whitepaper/Operator-WhitePaper_v1-0.md#operator-components-in-kubernetes).
 * 
 * The implementation splits the controller in two components. The
 * {@link VmMonitor} and the {@link Reconciler}. The former watches
 * the VM definitions (CRs) and generates {@link VmDefChanged} events
 * when they change. The latter handles the changes and reconciles the
 * resources in the cluster.
 * 
 * The controller itself supports a single configuration property:
 * ```yaml
 * "/Manager":
 *   "/Controller":
 *     namespace: vmop-dev
 * ```
 * This may only be set when running the Manager (and thus the Controller)
 * outside a container during development.  
 * 
 * ![Controller components](controller-components.svg)
 * 
 * @startuml controller-components.svg
 * skinparam component {
 *   BackGroundColor #FEFECE
 *   BorderColor #A80036
 *   BorderThickness 1.25
 *   BackgroundColor<<internal>> #F1F1F1
 *   BorderColor<<internal>> #181818
 *   BorderThickness<<internal>> 1
 * }
 * 
 * [Controller]
 * [Controller] *--> [VmWatcher]
 * [Controller] *--> [Reconciler]
 * @enduml
 */
public class Controller extends Component {

    private String namespace;

    /**
     * Creates a new instance.
     */
    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
    public Controller(Channel componentChannel) {
        super(componentChannel);
        // Prepare component tree
        ChannelManager<String, VmChannel, ?> chanMgr
            = new ChannelManager<>(name -> {
                try {
                    return new VmChannel(channel(), newEventPipeline(),
                        new K8sClient());
                } catch (IOException e) {
                    logger.log(Level.SEVERE, e, () -> "Failed to create client"
                        + " for handling changes: " + e.getMessage());
                    return null;
                }
            });
        attach(new VmMonitor(channel(), chanMgr));
        attach(new DisplaySecretMonitor(channel(), chanMgr));
        // Currently, we don't use the IP assigned by the load balancer
        // to access the VM's console. Might change in the future.
        // attach(new ServiceMonitor(channel()).channelManager(chanMgr));
        attach(new Reconciler(channel()));
        attach(new PoolMonitor(channel()));
    }

    /**
     * Special handling of {@link ApiException} thrown by handlers.
     *
     * @param event the event
     */
    @Handler(channels = Channel.class)
    public void onHandlingError(HandlingError event) {
        if (event.throwable() instanceof ApiException exc) {
            logger.log(Level.WARNING, exc,
                () -> "Problem accessing kubernetes: " + exc.getResponseBody());
            event.stop();
        }
    }

    /**
     * Configure the component.
     *
     * @param event the event
     */
    @Handler
    public void onConfigurationUpdate(ConfigurationUpdate event) {
        event.structured(componentPath()).ifPresent(c -> {
            if (c.containsKey("namespace")) {
                namespace = (String) c.get("namespace");
            }
        });
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

        // Verify that a namespace has been configured
        if (namespace == null) {
            var path = Path
                .of("/var/run/secrets/kubernetes.io/serviceaccount/namespace");
            if (Files.isReadable(path)) {
                namespace = Files.lines(path).findFirst().orElse(null);
                fire(new ConfigurationUpdate().add(componentPath(), "namespace",
                    namespace));
            }
        }
        if (namespace == null) {
            logger.severe(() -> "Namespace to control not configured and"
                + " no file in kubernetes directory.");
            event.cancel(true);
            fire(new Exit(2));
            return;
        }
        logger.fine(() -> "Controlling namespace \"" + namespace + "\".");
    }

    /**
     * On modify vm.
     *
     * @param event the event
     * @throws ApiException the api exception
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @Handler
    public void onModifyVm(ModifyVm event, VmChannel channel)
            throws ApiException, IOException {
        patchVmDef(channel.client(), event.name(), "spec/vm/" + event.path(),
            event.value());
    }

    private void patchVmDef(K8sClient client, String name, String path,
            Object value) throws ApiException, IOException {
        var vmStub = K8sDynamicStub.get(client,
            new GroupVersionKind(Crd.GROUP, "", Crd.KIND_VM), namespace,
            name);

        // Patch running
        String valueAsText = value instanceof String
            ? "\"" + value + "\""
            : value.toString();
        var res = vmStub.patch(V1Patch.PATCH_FORMAT_JSON_PATCH,
            new V1Patch("[{\"op\": \"replace\", \"path\": \"/"
                + path + "\", \"value\": " + valueAsText + "}]"),
            client.defaultPatchOptions());
        if (!res.isPresent()) {
            logger.warning(
                () -> "Cannot patch definition for Vm " + vmStub.name());
        }
    }

    /**
     * Attempt to Update the assignment information in the status of the
     * VM CR. Returns true if successful. The handler does not attempt
     * retries, because in case of failure it will be necessary to
     * re-evaluate the chosen VM.
     *
     * @param event the event
     * @param channel the channel
     * @throws ApiException the api exception
     */
    @Handler
    public void onUpdatedAssignment(UpdateAssignment event, VmChannel channel)
            throws ApiException {
        try {
            var vmDef = channel.vmDefinition();
            var vmStub = VmDefinitionStub.get(channel.client(),
                new GroupVersionKind(Crd.GROUP, "", Crd.KIND_VM),
                vmDef.namespace(), vmDef.name());
            if (vmStub.updateStatus(vmDef, from -> {
                JsonObject status = from.statusJson();
                var assignment = GsonPtr.to(status).to(Status.ASSIGNMENT);
                assignment.set("pool", event.fromPool().name());
                assignment.set("user", event.toUser());
                assignment.set("lastUsed", Instant.now().toString());
                return status;
            }).isPresent()) {
                event.setResult(true);
            }
        } catch (ApiException e) {
            // Log exceptions except for conflict, which can be expected
            if (HttpURLConnection.HTTP_CONFLICT != e.getCode()) {
                throw e;
            }
        }
        event.setResult(false);
    }
}
