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

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.generic.options.ListOptions;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.jdrupes.vmoperator.common.Constants.Crd;
import org.jdrupes.vmoperator.common.K8s;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.K8sDynamicStub;
import org.jdrupes.vmoperator.common.K8sObserver.ResponseType;
import org.jdrupes.vmoperator.common.K8sV1ConfigMapStub;
import org.jdrupes.vmoperator.common.K8sV1StatefulSetStub;
import org.jdrupes.vmoperator.common.VmDefinition;
import org.jdrupes.vmoperator.common.VmDefinition.Assignment;
import org.jdrupes.vmoperator.common.VmDefinitionStub;
import org.jdrupes.vmoperator.common.VmDefinitions;
import org.jdrupes.vmoperator.common.VmExtraData;
import org.jdrupes.vmoperator.common.VmPool;
import static org.jdrupes.vmoperator.manager.Constants.APP_NAME;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_NAME;
import org.jdrupes.vmoperator.manager.events.AssignVm;
import org.jdrupes.vmoperator.manager.events.ChannelManager;
import org.jdrupes.vmoperator.manager.events.GetPools;
import org.jdrupes.vmoperator.manager.events.GetVms;
import org.jdrupes.vmoperator.manager.events.GetVms.VmData;
import org.jdrupes.vmoperator.manager.events.ModifyVm;
import org.jdrupes.vmoperator.manager.events.PodChanged;
import org.jdrupes.vmoperator.manager.events.UpdateAssignment;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Event;
import org.jgrapes.core.annotation.Handler;

/**
 * Watches for changes of VM definitions.
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis", "PMD.ExcessiveImports" })
public class VmMonitor extends
        AbstractMonitor<VmDefinition, VmDefinitions, VmChannel> {

    private final ChannelManager<String, VmChannel, ?> channelManager;

    /**
     * Instantiates a new VM definition watcher.
     *
     * @param componentChannel the component channel
     * @param channelManager the channel manager
     */
    public VmMonitor(Channel componentChannel,
            ChannelManager<String, VmChannel, ?> channelManager) {
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
        V1ObjectMeta metadata = response.object.getMetadata();
        AtomicBoolean toBeAdded = new AtomicBoolean(false);
        VmChannel channel = channelManager.channel(metadata.getName())
            .orElseGet(() -> {
                toBeAdded.set(true);
                return channelManager.createChannel(metadata.getName());
            });

        // Get full definition and associate with channel as backup
        var vmDef = response.object;
        if (vmDef.data() == null) {
            // ADDED event does not provide data, see
            // https://github.com/kubernetes-client/java/issues/3215
            vmDef = getModel(client, vmDef);
        }
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
        if (toBeAdded.get()) {
            channelManager.put(vmDef.name(), channel);
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
        var prevExtra
            = Optional.ofNullable(prevState).flatMap(VmDefinition::extra);

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
        if (channel.vmDefinition().extra().isEmpty()) {
            return;
        }
        var extra = channel.vmDefinition().extra().get();
        var pod = event.pod();
        if (event.type() == ResponseType.DELETED) {
            // The status of a deleted pod is the status before deletion,
            // i.e. the node info is still there.
            extra.nodeInfo("", Collections.emptyList());
        } else {
            var nodeName = Optional
                .ofNullable(pod.getSpec().getNodeName()).orElse("");
            logger.finer(() -> "Adding node name " + nodeName
                + " to VM info for " + channel.vmDefinition().name());
            @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
            var addrs = new ArrayList<String>();
            Optional.ofNullable(pod.getStatus().getPodIPs())
                .orElse(Collections.emptyList()).stream()
                .map(ip -> ip.getIp()).forEach(addrs::add);
            logger.finer(() -> "Adding node addresses " + addrs
                + " to VM info for " + channel.vmDefinition().name());
            if (Objects.equals(nodeName, extra.nodeName())
                && Objects.equals(addrs, extra.nodeAddresses())) {
                return;
            }
            extra.nodeInfo(nodeName, addrs);
        }
        channel.fire(new VmDefChanged(ResponseType.MODIFIED, false,
            channel.vmDefinition()));
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
            var vmQuery = channelManager.channels().stream()
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
            vmQuery = channelManager.channels().stream()
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
                chosenVm.pipeline().fire(new ModifyVm(vmDef.name(),
                    "state", "Running", chosenVm));
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

}
