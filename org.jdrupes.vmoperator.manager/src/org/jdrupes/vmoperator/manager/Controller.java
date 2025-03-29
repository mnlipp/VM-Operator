/*
 * VM-Operator
 * Copyright (C) 2023, 2025 Michael N. Lipp
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
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.logging.Level;
import org.jdrupes.vmoperator.common.Constants.Crd;
import org.jdrupes.vmoperator.common.Constants.Status;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.K8sObserver.ResponseType;
import org.jdrupes.vmoperator.common.VmDefinition.Assignment;
import org.jdrupes.vmoperator.common.VmDefinitionStub;
import org.jdrupes.vmoperator.common.VmPool;
import org.jdrupes.vmoperator.manager.events.AssignVm;
import org.jdrupes.vmoperator.manager.events.ChannelManager;
import org.jdrupes.vmoperator.manager.events.Exit;
import org.jdrupes.vmoperator.manager.events.GetPools;
import org.jdrupes.vmoperator.manager.events.GetVms;
import org.jdrupes.vmoperator.manager.events.GetVms.VmData;
import org.jdrupes.vmoperator.manager.events.ModifyVm;
import org.jdrupes.vmoperator.manager.events.PodChanged;
import org.jdrupes.vmoperator.manager.events.UpdateAssignment;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmPoolChanged;
import org.jdrupes.vmoperator.manager.events.VmResourceChanged;
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
 * the VM definitions (CRs) and generates {@link VmResourceChanged} events
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
    private final ChannelManager<String, VmChannel, ?> chanMgr;

    /**
     * Creates a new instance.
     */
    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
    public Controller(Channel componentChannel) {
        super(componentChannel);
        // Prepare component tree
        chanMgr = new ChannelManager<>(name -> {
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
        attach(new PodMonitor(channel(), chanMgr));
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
     * Returns the VM data.
     *
     * @param event the event
     */
    @Handler
    public void onGetVms(GetVms event) {
        event.setResult(chanMgr.channels().stream()
            .filter(c -> event.name().isEmpty()
                || c.vmDefinition().name().equals(event.name().get()))
            .filter(c -> event.user().isEmpty() && event.roles().isEmpty()
                || !c.vmDefinition().permissionsFor(event.user().orElse(null),
                    event.roles()).isEmpty())
            .filter(c -> event.fromPool().isEmpty()
                || c.vmDefinition().assignment().map(Assignment::pool)
                    .map(p -> p.equals(event.fromPool().get())).orElse(false))
            .filter(c -> event.toUser().isEmpty()
                || c.vmDefinition().assignment().map(Assignment::user)
                    .map(u -> u.equals(event.toUser().get())).orElse(false))
            .map(c -> new VmData(c.vmDefinition(), c))
            .toList());
    }

    /**
     * Assign a VM if not already assigned.
     *
     * @param event the event
     * @throws ApiException the api exception
     * @throws InterruptedException 
     */
    @Handler
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public void onAssignVm(AssignVm event)
            throws ApiException, InterruptedException {
        while (true) {
            // Search for existing assignment.
            var vmQuery = chanMgr.channels().stream()
                .filter(c -> c.vmDefinition().assignment().map(Assignment::pool)
                    .map(p -> p.equals(event.fromPool())).orElse(false))
                .filter(c -> c.vmDefinition().assignment().map(Assignment::user)
                    .map(u -> u.equals(event.toUser())).orElse(false))
                .findFirst();
            if (vmQuery.isPresent()) {
                var vmDef = vmQuery.get().vmDefinition();
                event.setResult(new VmData(vmDef, vmQuery.get()));
                return;
            }

            // Get the pool definition for checking possible assignment
            VmPool vmPool = newEventPipeline().fire(new GetPools()
                .withName(event.fromPool())).get().stream().findFirst()
                .orElse(null);
            if (vmPool == null) {
                return;
            }

            // Find available VM.
            vmQuery = chanMgr.channels().stream()
                .filter(c -> vmPool.isAssignable(c.vmDefinition()))
                .sorted(Comparator.comparing((VmChannel c) -> c.vmDefinition()
                    .assignment().map(Assignment::lastUsed)
                    .orElse(Instant.ofEpochSecond(0)))
                    .thenComparing(preferRunning))
                .findFirst();

            // None found
            if (vmQuery.isEmpty()) {
                return;
            }

            // Assign to user
            var chosenVm = vmQuery.get();
            if (Optional.ofNullable(chosenVm.fire(new UpdateAssignment(
                vmPool, event.toUser())).get()).orElse(false)) {
                var vmDef = chosenVm.vmDefinition();
                event.setResult(new VmData(vmDef, chosenVm));

                // Make sure that a newly assigned VM is running.
                chosenVm.fire(new ModifyVm(vmDef.name(), "state", "Running"));
                return;
            }
        }
    }

    private static Comparator<VmChannel> preferRunning
        = new Comparator<>() {
            @Override
            public int compare(VmChannel ch1, VmChannel ch2) {
                if (ch1.vmDefinition().conditionStatus("Running").orElse(false)
                    && !ch2.vmDefinition().conditionStatus("Running")
                        .orElse(false)) {
                    return -1;
                }
                return 0;
            }
        };

    /**
     * When s pool is deleted, remove all related assignments.
     *
     * @param event the event
     * @throws InterruptedException 
     */
    @Handler
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public void onPoolChanged(VmPoolChanged event) throws InterruptedException {
        if (!event.deleted()) {
            return;
        }
        var vms = newEventPipeline()
            .fire(new GetVms().assignedFrom(event.vmPool().name())).get();
        for (var vm : vms) {
            vm.channel().fire(new UpdateAssignment(event.vmPool(), null));
        }
    }

    /**
     * Remove runner version from status when pod is deleted
     *
     * @param event the event
     * @param channel the channel
     * @throws ApiException the api exception
     */
    @Handler
    public void onPodChange(PodChanged event, VmChannel channel)
            throws ApiException {
        if (event.type() == ResponseType.DELETED) {
            // Remove runner info from status
            var vmDef = channel.vmDefinition();
            var vmStub = VmDefinitionStub.get(channel.client(),
                new GroupVersionKind(Crd.GROUP, "", Crd.KIND_VM),
                vmDef.namespace(), vmDef.name());
            vmStub.updateStatus(from -> {
                JsonObject status = from.statusJson();
                status.remove(Status.RUNNER_VERSION);
                return status;
            });
        }
    }
}
