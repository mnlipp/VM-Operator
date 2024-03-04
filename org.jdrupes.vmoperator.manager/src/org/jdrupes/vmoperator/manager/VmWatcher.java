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
import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.Discovery.APIResource;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1APIResource;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.options.ListOptions;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_GROUP;
import org.jdrupes.vmoperator.common.K8s;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.K8sDynamicModel;
import org.jdrupes.vmoperator.common.K8sDynamicStub;
import org.jdrupes.vmoperator.common.K8sV1PodStub;
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
public class VmWatcher extends AbstractWatcher {

    private K8sClient client;
    private APIResource context;

    /**
     * Instantiates a new VM definition watcher.
     *
     * @param componentChannel the component channel
     */
    public VmWatcher(Channel componentChannel) {
        super(componentChannel);
    }

    @Override
    protected void startWatching() throws IOException, ApiException {
        // Get all our API versions
        client = new K8sClient();
        var ctx = K8s.context(client, VM_OP_GROUP, "", VM_OP_KIND_VM);
        if (ctx.isEmpty()) {
            logger.severe(() -> "Cannot get CRD context.");
            return;
        }
        context = ctx.get();

        // Remove left overs
        var coa = new CustomObjectsApi(client);
        purge(coa, context.getVersions());

        // Start a watcher thread for each existing CRD version.
        // The watcher will send us an "ADDED" for each existing VM.
        for (var version : context.getVersions()) {
            coa.getAPIResources(VM_OP_GROUP, version)
                .getResources().stream()
                .filter(r -> VM_OP_KIND_VM.equals(r.getKind()))
                .findFirst()
                .ifPresent(crd -> watchVmDefs(crd, version));
        }
    }

    @SuppressWarnings("PMD.CognitiveComplexity")
    private void purge(CustomObjectsApi coa, List<String> vmOpApiVersions)
            throws ApiException {
        // Get existing CRs (VMs)
        Set<String> known = new HashSet<>();
        for (var version : vmOpApiVersions) {
            // Get all known CR instances.
            coa.getAPIResources(VM_OP_GROUP, version)
                .getResources().stream()
                .filter(r -> VM_OP_KIND_VM.equals(r.getKind()))
                .findFirst()
                .ifPresent(crd -> known.addAll(getKnown(client, crd, version)));
        }

        ListOptions opts = new ListOptions();
        opts.setLabelSelector(
            "app.kubernetes.io/managed-by=" + VM_OP_NAME + ","
                + "app.kubernetes.io/name=" + APP_NAME);
        for (String resource : List.of("apps/v1/statefulsets",
            "v1/configmaps", "v1/secrets")) {
            @SuppressWarnings({ "PMD.AvoidInstantiatingObjectsInLoops",
                "PMD.AvoidDuplicateLiterals" })
            var resParts = new LinkedList<>(List.of(resource.split("/")));
            var group = resParts.size() == 3 ? resParts.poll() : "";
            var version = resParts.poll();
            var plural = resParts.poll();
            // Get resources, selected by label
            @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
            var api = new DynamicKubernetesApi(group, version, plural, client);
            var listObj = api.list(namespaceToWatch(), opts).getObject();
            if (listObj == null) {
                continue;
            }
            for (var obj : listObj.getItems()) {
                String instance = obj.getMetadata().getLabels()
                    .get("app.kubernetes.io/instance");
                if (!known.contains(instance)) {
                    var resName = obj.getMetadata().getName();
                    var result = api.delete(namespaceToWatch(), resName);
                    if (!result.isSuccess()) {
                        logger.warning(() -> "Cannot cleanup resource \""
                            + resName + "\": " + result.toString());
                    }
                }
            }
        }
    }

    private Set<String> getKnown(ApiClient client, V1APIResource crd,
            String version) {
        Set<String> result = new HashSet<>();
        var api = new DynamicKubernetesApi(VM_OP_GROUP, version,
            crd.getName(), client);
        for (var item : api.list(namespaceToWatch()).getObject().getItems()) {
            if (!VM_OP_KIND_VM.equals(item.getKind())) {
                continue;
            }
            result.add(item.getMetadata().getName());
        }
        return result;
    }

    private void watchVmDefs(V1APIResource crd, String version) {
        try {
            var client = Config.defaultClient();
            var coa = new CustomObjectsApi(client);
            var call = coa.listNamespacedCustomObjectCall(VM_OP_GROUP,
                version, namespaceToWatch(), crd.getName(), null, false,
                null, null, null, null, null, null, null, true, null);
            startWatcher(crd, call,
                new TypeToken<Watch.Response<V1Namespace>>() {
                }.getType(), this::handleVmDefinitionChange);
        } catch (IOException | ApiException e) {
            logger.log(Level.SEVERE, e, () -> "Problem watching \""
                + crd.getName() + "\": " + e.getMessage());
        }
    }

    private void handleVmDefinitionChange(V1APIResource vmsCrd,
            Watch.Response<V1Namespace> vmDefRef) {
        V1ObjectMeta metadata = vmDefRef.object.getMetadata();
        VmChannel channel = channel(metadata.getName());
        if (channel == null) {
            return;
        }

        // Get full definition and associate with channel as backup
        var vmStub = K8sDynamicStub.get(channel.client(),
            context,
            metadata.getNamespace(), metadata.getName());
        try {
            vmStub.model().ifPresent(vmDef -> {
                addDynamicData(channel.client(), vmDef);
                channel.setVmDefinition(vmDef);

                // Create and fire event
                channel.pipeline().fire(new VmDefChanged(VmDefChanged.Type
                    .valueOf(vmDefRef.type),
                    channel
                        .setGeneration(
                            vmDefRef.object.getMetadata().getGeneration()),
                    vmsCrd, vmDef), channel);
            });
        } catch (ApiException e) {
            logger.log(Level.WARNING, e,
                () -> "Change notificatin for unaccessible object.");
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
                = K8sV1PodStub.list(client, namespaceToWatch(), podSearch);
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
