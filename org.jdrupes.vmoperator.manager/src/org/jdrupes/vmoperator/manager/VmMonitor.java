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
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.generic.options.ListOptions;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jdrupes.vmoperator.common.Constants.Crd;
import org.jdrupes.vmoperator.common.Constants.Status;
import org.jdrupes.vmoperator.common.K8s;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.K8sDynamicStub;
import org.jdrupes.vmoperator.common.K8sObserver.ResponseType;
import org.jdrupes.vmoperator.common.K8sV1ConfigMapStub;
import org.jdrupes.vmoperator.common.K8sV1StatefulSetStub;
import org.jdrupes.vmoperator.common.VmDefinition;
import org.jdrupes.vmoperator.common.VmDefinitionStub;
import org.jdrupes.vmoperator.common.VmDefinitions;
import org.jdrupes.vmoperator.common.VmExtraData;
import static org.jdrupes.vmoperator.manager.Constants.APP_NAME;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_NAME;
import org.jdrupes.vmoperator.manager.events.ChannelManager;
import org.jdrupes.vmoperator.manager.events.ModifyVm;
import org.jdrupes.vmoperator.manager.events.PodChanged;
import org.jdrupes.vmoperator.manager.events.UpdateAssignment;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmResourceChanged;
import org.jdrupes.vmoperator.util.GsonPtr;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Event;
import org.jgrapes.core.EventPipeline;
import org.jgrapes.core.annotation.Handler;

/**
 * Watches for changes of VM definitions. When a VM definition (CR)
 * becomes known, is is registered with a {@link ChannelManager} and thus
 * gets an associated {@link VmChannel} and an associated
 * {@link EventPipeline}.
 * 
 * The {@link EventPipeline} is used for submitting an action that processes
 * the change data from kubernetes, eventually transforming it to a
 * {@link VmResourceChanged} event that is handled by another
 * {@link EventPipeline} associated with the {@link VmChannel}. This
 * event pipeline should be used for all events related to changes of
 * a particular VM.
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis", "PMD.ExcessiveImports" })
public class VmMonitor extends
        AbstractMonitor<VmDefinition, VmDefinitions, VmChannel> {

    private final ChannelManager<String, VmChannel,
            EventPipeline> channelManager;

    /**
     * Instantiates a new VM definition watcher.
     *
     * @param componentChannel the component channel
     * @param channelManager the channel manager
     */
    public VmMonitor(Channel componentChannel,
            ChannelManager<String, VmChannel, EventPipeline> channelManager) {
        super(componentChannel, VmDefinition.class,
            VmDefinitions.class);
        this.channelManager = channelManager;
    }

    @Override
    protected void prepareMonitoring() throws IOException, ApiException {
        client(new K8sClient());

        // Get all our API versions
        var ctx = K8s.context(client(), Crd.GROUP, "", Crd.KIND_VM);
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
            Watch.Response<VmDefinition> response) {
        var name = response.object.getMetadata().getName();

        // Process the response data on a VM specific pipeline to
        // increase concurrency when e.g. starting many VMs.
        var preparing = channelManager.associated(name)
            .orElseGet(() -> newEventPipeline());
        preparing.submit("VmChange[" + name + "]",
            () -> processChange(client, response, preparing));
    }

    private void processChange(K8sClient client,
            Watch.Response<VmDefinition> response, EventPipeline preparing) {
        // Get full definition and associate with channel as backup
        var vmDef = response.object;
        if (vmDef.data() == null) {
            // ADDED event does not provide data, see
            // https://github.com/kubernetes-client/java/issues/3215
            vmDef = getModel(client, vmDef);
        }
        var name = response.object.getMetadata().getName();
        var channel = channelManager.channel(name)
            .orElseGet(() -> channelManager.createChannel(name));
        if (vmDef.data() != null) {
            // New data, augment and save
            addExtraData(vmDef, channel.vmDefinition());
            channel.setVmDefinition(vmDef);
        } else {
            // Reuse cached (e.g. if deleted)
            vmDef = channel.vmDefinition();
        }
        if (vmDef == null) {
            logger.warning(() -> "Cannot get defintion for "
                + response.object.getMetadata());
            return;
        }
        channelManager.put(name, channel, preparing);

        // Create and fire changed event. Remove channel from channel
        // manager on completion.
        VmResourceChanged chgEvt
            = new VmResourceChanged(ResponseType.valueOf(response.type), vmDef,
                channel.setGeneration(response.object.getMetadata()
                    .getGeneration()),
                false);
        if (ResponseType.valueOf(response.type) == ResponseType.DELETED) {
            chgEvt = Event.onCompletion(chgEvt,
                e -> channelManager.remove(e.vmDefinition().name()));
        }
        channel.fire(chgEvt);
    }

    private VmDefinition getModel(K8sClient client, VmDefinition vmDef) {
        try {
            return VmDefinitionStub.get(client, context(), namespace(),
                vmDef.metadata().getName()).model().orElse(null);
        } catch (ApiException e) {
            return null;
        }
    }

    @SuppressWarnings("PMD.AvoidDuplicateLiterals")
    private void addExtraData(VmDefinition vmDef, VmDefinition prevState) {
        var extra = new VmExtraData(vmDef);
        var prevExtra = Optional.ofNullable(prevState).map(VmDefinition::extra);

        // Maintain (or initialize) the resetCount
        extra.resetCount(prevExtra.map(VmExtraData::resetCount).orElse(0L));

        // Maintain node info
        prevExtra
            .ifPresent(e -> extra.nodeInfo(e.nodeName(), e.nodeAddresses()));
    }

    /**
     * On pod changed.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler
    public void onPodChanged(PodChanged event, VmChannel channel) {
        var vmDef = channel.vmDefinition();

        // Make sure that this is properly sync'd with VM CR changes.
        channelManager.associated(vmDef.name())
            .orElseGet(() -> activeEventPipeline())
            .submit("NodeInfo[" + vmDef.name() + "]",
                () -> {
                    updateNodeInfo(event, vmDef);
                    channel.fire(new VmResourceChanged(ResponseType.MODIFIED,
                        vmDef, false, true));
                });
    }

    private void updateNodeInfo(PodChanged event, VmDefinition vmDef) {
        var extra = vmDef.extra();
        if (event.type() == ResponseType.DELETED) {
            // The status of a deleted pod is the status before deletion,
            // i.e. the node info is still cached and must be removed.
            extra.nodeInfo("", Collections.emptyList());
            return;
        }

        // Get current node info from pod
        var pod = event.pod();
        var nodeName = Optional
            .ofNullable(pod.getSpec().getNodeName()).orElse("");
        logger.finer(() -> "Adding node name " + nodeName
            + " to VM info for " + vmDef.name());
        @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
        var addrs = new ArrayList<String>();
        Optional.ofNullable(pod.getStatus().getPodIPs())
            .orElse(Collections.emptyList()).stream()
            .map(ip -> ip.getIp()).forEach(addrs::add);
        logger.finer(() -> "Adding node addresses " + addrs
            + " to VM info for " + vmDef.name());
        extra.nodeInfo(nodeName, addrs);
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
            new GroupVersionKind(Crd.GROUP, "", Crd.KIND_VM), namespace(),
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
                if (event.toUser() == null) {
                    ((JsonObject) GsonPtr.to(status).get())
                        .remove(Status.ASSIGNMENT);
                } else {
                    var assignment = GsonPtr.to(status).to(Status.ASSIGNMENT);
                    assignment.set("pool", event.fromPool().name());
                    assignment.set("user", event.toUser());
                    assignment.set("lastUsed", Instant.now().toString());
                }
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
