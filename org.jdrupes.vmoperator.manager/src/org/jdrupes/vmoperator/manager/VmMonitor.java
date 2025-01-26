/*
 * VM-Operator
 * Copyright (C) 2023,2025 Michael N. Lipp
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
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.generic.options.ListOptions;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_GROUP;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_KIND_VM;
import org.jdrupes.vmoperator.common.K8s;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.K8sDynamicStub;
import org.jdrupes.vmoperator.common.K8sObserver.ResponseType;
import org.jdrupes.vmoperator.common.K8sV1ConfigMapStub;
import org.jdrupes.vmoperator.common.K8sV1PodStub;
import org.jdrupes.vmoperator.common.K8sV1StatefulSetStub;
import org.jdrupes.vmoperator.common.VmDefinition;
import org.jdrupes.vmoperator.common.VmDefinitionModel;
import org.jdrupes.vmoperator.common.VmDefinitionModels;
import org.jdrupes.vmoperator.common.VmDefinitionStub;
import org.jdrupes.vmoperator.common.VmPool;
import static org.jdrupes.vmoperator.manager.Constants.APP_NAME;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_NAME;
import org.jdrupes.vmoperator.manager.events.AssignVm;
import org.jdrupes.vmoperator.manager.events.ChannelManager;
import org.jdrupes.vmoperator.manager.events.GetPools;
import org.jdrupes.vmoperator.manager.events.GetVms;
import org.jdrupes.vmoperator.manager.events.GetVms.VmData;
import org.jdrupes.vmoperator.manager.events.ModifyVm;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
import org.jdrupes.vmoperator.util.GsonPtr;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Event;
import org.jgrapes.core.annotation.Handler;

/**
 * Watches for changes of VM definitions.
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis", "PMD.ExcessiveImports" })
public class VmMonitor extends
        AbstractMonitor<VmDefinitionModel, VmDefinitionModels, VmChannel> {

    private final ChannelManager<String, VmChannel, ?> channelManager;

    /**
     * Instantiates a new VM definition watcher.
     *
     * @param componentChannel the component channel
     * @param channelManager the channel manager
     */
    public VmMonitor(Channel componentChannel,
            ChannelManager<String, VmChannel, ?> channelManager) {
        super(componentChannel, VmDefinitionModel.class,
            VmDefinitionModels.class);
        this.channelManager = channelManager;
    }

    @Override
    protected void prepareMonitoring() throws IOException, ApiException {
        client(new K8sClient());

        // Get all our API versions
        var ctx = K8s.context(client(), VM_OP_GROUP, "", VM_OP_KIND_VM);
        if (ctx.isEmpty()) {
            logger.severe(() -> "Cannot get CRD context.");
            return;
        }
        context(ctx.get());

        // Remove left over resources
        purge();
    }

    @SuppressWarnings("PMD.CognitiveComplexity")
    private void purge() throws ApiException {
        // Get existing CRs (VMs)
        var known = K8sDynamicStub.list(client(), context(), namespace())
            .stream().map(stub -> stub.name()).collect(Collectors.toSet());
        ListOptions opts = new ListOptions();
        opts.setLabelSelector(
            "app.kubernetes.io/managed-by=" + VM_OP_NAME + ","
                + "app.kubernetes.io/name=" + APP_NAME);
        for (var context : Set.of(K8sV1StatefulSetStub.CONTEXT,
            K8sV1ConfigMapStub.CONTEXT)) {
            for (var resStub : K8sDynamicStub.list(client(), context,
                namespace(), opts)) {
                String instance = resStub.model()
                    .map(m -> m.metadata().getName()).orElse("(unknown)");
                if (!known.contains(instance)) {
                    resStub.delete();
                }
            }
        }
    }

    @Override
    protected void handleChange(K8sClient client,
            Watch.Response<VmDefinitionModel> response) {
        V1ObjectMeta metadata = response.object.getMetadata();
        VmChannel channel = channelManager.channelGet(metadata.getName());

        // Get full definition and associate with channel as backup
        var vmModel = response.object;
        if (vmModel.data() == null) {
            // ADDED event does not provide data, see
            // https://github.com/kubernetes-client/java/issues/3215
            vmModel = getModel(client, vmModel);
        }
        VmDefinition vmDef = null;
        if (vmModel.data() != null) {
            // New data, augment and save
            vmDef = client.getJSON().getGson().fromJson(vmModel.data(),
                VmDefinition.class);
            addDynamicData(channel.client(), vmDef, channel.vmDefinition());
            channel.setVmDefinition(vmDef);
        }
        if (vmDef == null) {
            // Reuse cached (e.g. if deleted)
            vmDef = channel.vmDefinition();
        }
        if (vmDef == null) {
            logger.warning(() -> "Cannot get defintion for "
                + response.object.getMetadata());
            return;
        }

        // Create and fire changed event. Remove channel from channel
        // manager on completion.
        VmDefChanged chgEvt
            = new VmDefChanged(ResponseType.valueOf(response.type),
                channel.setGeneration(response.object.getMetadata()
                    .getGeneration()),
                vmDef);
        if (ResponseType.valueOf(response.type) == ResponseType.DELETED) {
            chgEvt = Event.onCompletion(chgEvt,
                e -> channelManager.remove(e.vmDefinition().name()));
        }
        channel.pipeline().fire(chgEvt, channel);
    }

    private VmDefinitionModel getModel(K8sClient client,
            VmDefinitionModel vmDef) {
        try {
            return VmDefinitionStub.get(client, context(), namespace(),
                vmDef.metadata().getName()).model().orElse(null);
        } catch (ApiException e) {
            return null;
        }
    }

    @SuppressWarnings("PMD.AvoidDuplicateLiterals")
    private void addDynamicData(K8sClient client, VmDefinition vmDef,
            VmDefinition prevState) {
        // Maintain (or initialize) the resetCount
        vmDef.extra("resetCount",
            Optional.ofNullable(prevState).map(d -> d.extra("resetCount"))
                .orElse(0L));

        // Node information
        // Add defaults in case the VM is not running
        vmDef.extra("nodeName", "");
        vmDef.extra("nodeAddress", "");

        // VM definition status changes before the pod terminates.
        // This results in pod information being shown for a stopped
        // VM which is irritating. So check condition first.
        if (!vmDef.conditionStatus("Running").orElse(false)) {
            return;
        }
        var podSearch = new ListOptions();
        podSearch.setLabelSelector("app.kubernetes.io/name=" + APP_NAME
            + ",app.kubernetes.io/component=" + APP_NAME
            + ",app.kubernetes.io/instance=" + vmDef.name());
        try {
            var podList
                = K8sV1PodStub.list(client, namespace(), podSearch);
            for (var podStub : podList) {
                var nodeName = podStub.model().get().getSpec().getNodeName();
                vmDef.extra("nodeName", nodeName);
                logger.fine(() -> "Added node name " + nodeName
                    + " to VM info for " + vmDef.name());
                @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
                var addrs = new ArrayList<String>();
                podStub.model().get().getStatus().getPodIPs().stream()
                    .map(ip -> ip.getIp()).forEach(addrs::add);
                vmDef.extra("nodeAddresses", addrs);
                logger.fine(() -> "Added node addresses " + addrs
                    + " to VM info for " + vmDef.name());
            }
        } catch (ApiException e) {
            logger.log(Level.WARNING, e,
                () -> "Cannot access node information: " + e.getMessage());
        }
    }

    /**
     * Returns the VM data.
     *
     * @param event the event
     */
    @Handler
    public void onGetVms(GetVms event) {
        event.setResult(channelManager.channels().stream()
            .filter(c -> event.name().isEmpty()
                || c.vmDefinition().name().equals(event.name().get()))
            .filter(c -> event.user().isEmpty() && event.roles().isEmpty()
                || !c.vmDefinition().permissionsFor(event.user().orElse(null),
                    event.roles()).isEmpty())
            .filter(c -> event.fromPool().isEmpty()
                || c.vmDefinition().assignedFrom()
                    .map(p -> p.equals(event.fromPool().get())).orElse(false))
            .filter(c -> event.toUser().isEmpty()
                || c.vmDefinition().assignedTo()
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
        VmPool vmPool = null;
        while (true) {
            // Search for existing assignment.
            var assignedVm = channelManager.channels().stream()
                .filter(c -> c.vmDefinition().assignedFrom()
                    .map(p -> p.equals(event.fromPool())).orElse(false))
                .filter(c -> c.vmDefinition().assignedTo()
                    .map(u -> u.equals(event.toUser())).orElse(false))
                .findFirst();
            if (assignedVm.isPresent()) {
                var vmDef = assignedVm.get().vmDefinition();
                event.setResult(new VmData(vmDef, assignedVm.get()));
                return;
            }

            // Get the pool definition for retention time calculations
            if (vmPool == null) {
                vmPool = newEventPipeline().fire(new GetPools()
                    .withName(event.fromPool())).get().stream().findFirst()
                    .orElse(null);
                if (vmPool == null) {
                    return;
                }
            }

            // Find available VM.
            var pool = vmPool;
            assignedVm = channelManager.channels().stream()
                .filter(c -> isAssignable(pool, c.vmDefinition()))
                .sorted(Comparator.comparing(c -> c.vmDefinition()
                    .assignmentLastUsed().orElse(Instant.ofEpochSecond(0))))
                .findFirst();

            // None found
            if (assignedVm.isEmpty()) {
                return;
            }

            // Assign to user
            var vmDef = assignedVm.get().vmDefinition();
            var vmStub = VmDefinitionStub.get(client(),
                new GroupVersionKind(VM_OP_GROUP, "", VM_OP_KIND_VM),
                vmDef.namespace(), vmDef.name());
            vmStub.updateStatus(from -> {
                JsonObject status = from.status();
                var assignment = GsonPtr.to(status).to("assignment");
                assignment.set("pool", event.fromPool());
                assignment.set("user", event.toUser());
                assignment.set("lastUsed", Instant.now().toString());
                return status;
            });

            // Make sure that a newly assigned VM is running.
            fire(new ModifyVm(vmDef.name(), "state", "Running",
                assignedVm.get()));
        }
    }

    @SuppressWarnings("PMD.SimplifyBooleanReturns")
    private boolean isAssignable(VmPool pool, VmDefinition vmDef) {
        // Check if the VM is in the pool
        if (!vmDef.pools().contains(pool.name())) {
            return false;
        }

        // Check if the VM is not in use
        if (vmDef.consoleConnected()) {
            return false;
        }

        // If not assigned, it's usable
        if (vmDef.assignedTo().isEmpty()) {
            return true;
        }

        // Check if it is to be retained
        if (vmDef.assignmentLastUsed()
            .map(lu -> pool.retainUntil(lu))
            .map(ru -> Instant.now().isBefore(ru)).orElse(false)) {
            return false;
        }

        // Additional check in case lastUsed has not been updated
        // by PoolMonitor#onVmDefChanged() yet ("race condition")
        if (vmDef.condition("ConsoleConnected")
            .map(cc -> cc.getLastTransitionTime().toInstant())
            .map(t -> pool.retainUntil(t))
            .map(ru -> Instant.now().isBefore(ru)).orElse(false)) {
            return false;
        }
        return true;
    }
}
