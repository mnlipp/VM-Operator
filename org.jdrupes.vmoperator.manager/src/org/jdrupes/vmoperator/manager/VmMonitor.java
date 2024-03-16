/*
 * VM-Operator
 * Copyright (C) 2023,2024 Michael N. Lipp
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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.generic.options.ListOptions;
import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_GROUP;
import org.jdrupes.vmoperator.common.K8s;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.K8sDynamicModel;
import org.jdrupes.vmoperator.common.K8sDynamicModels;
import org.jdrupes.vmoperator.common.K8sDynamicStub;
import org.jdrupes.vmoperator.common.K8sObserver.ResponseType;
import org.jdrupes.vmoperator.common.K8sV1ConfigMapStub;
import org.jdrupes.vmoperator.common.K8sV1PodStub;
import org.jdrupes.vmoperator.common.K8sV1StatefulSetStub;
import static org.jdrupes.vmoperator.manager.Constants.APP_NAME;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_KIND_VM;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_NAME;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
import org.jdrupes.vmoperator.util.GsonPtr;
import org.jgrapes.core.Channel;

/**
 * Watches for changes of VM definitions.
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis", "PMD.ExcessiveImports" })
public class VmMonitor
        extends AbstractMonitor<K8sDynamicModel, K8sDynamicModels, VmChannel> {

    /**
     * Instantiates a new VM definition watcher.
     *
     * @param componentChannel the component channel
     */
    public VmMonitor(Channel componentChannel) {
        super(componentChannel, K8sDynamicModel.class, K8sDynamicModels.class);
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
            Watch.Response<K8sDynamicModel> response) {
        V1ObjectMeta metadata = response.object.getMetadata();
        VmChannel channel = channel(metadata.getName());
        if (channel == null) {
            return;
        }

        // Get full definition and associate with channel as backup
        var vmDef = response.object;
        if (vmDef.data() == null) {
            // ADDED event does not provide data, see
            // https://github.com/kubernetes-client/java/issues/3215
            vmDef = getModel(client, vmDef);
        }
        if (vmDef.data() != null) {
            // New data, augment and save
            addDynamicData(channel.client(), vmDef);
            channel.setVmDefinition(vmDef);
        } else {
            // Reuse cached
            vmDef = channel.vmDefinition();
        }
        if (vmDef == null) {
            logger.warning(
                () -> "Cannot get model for " + response.object.getMetadata());
            return;
        }

        // Create and fire event
        channel.pipeline()
            .fire(new VmDefChanged(ResponseType.valueOf(response.type),
                channel.setGeneration(
                    response.object.getMetadata().getGeneration()),
                vmDef), channel);
    }

    private K8sDynamicModel getModel(K8sClient client, K8sDynamicModel vmDef) {
        try {
            return K8sDynamicStub.get(client, context(), namespace(),
                vmDef.metadata().getName()).model().orElse(null);
        } catch (ApiException e) {
            return null;
        }
    }

    private void addDynamicData(K8sClient client, K8sDynamicModel vmState) {
        var rootNode = GsonPtr.to(vmState.data()).get(JsonObject.class);
        rootNode.addProperty("nodeName", "");

        // VM definition status changes before the pod terminates.
        // This results in pod information being shown for a stopped
        // VM which is irritating. So check condition first.
        var isRunning = GsonPtr.to(rootNode).to("status", "conditions")
            .get(JsonArray.class)
            .asList().stream().filter(el -> "Running"
                .equals(((JsonObject) el).get("type").getAsString()))
            .findFirst().map(el -> "True"
                .equals(((JsonObject) el).get("status").getAsString()))
            .orElse(false);
        if (!isRunning) {
            return;
        }
        var podSearch = new ListOptions();
        podSearch.setLabelSelector("app.kubernetes.io/name=" + APP_NAME
            + ",app.kubernetes.io/component=" + APP_NAME
            + ",app.kubernetes.io/instance=" + vmState.getMetadata().getName());
        try {
            var podList
                = K8sV1PodStub.list(client, namespace(), podSearch);
            for (var podStub : podList) {
                rootNode.addProperty("nodeName",
                    podStub.model().get().getSpec().getNodeName());
            }
        } catch (ApiException e) {
            logger.log(Level.WARNING, e,
                () -> "Cannot access node information: " + e.getMessage());
        }
    }
}
