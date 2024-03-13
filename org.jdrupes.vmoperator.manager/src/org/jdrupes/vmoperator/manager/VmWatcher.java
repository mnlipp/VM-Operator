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
import io.kubernetes.client.apimachinery.GroupVersion;
import io.kubernetes.client.apimachinery.GroupVersionKind;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.ApisApi;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1APIGroup;
import io.kubernetes.client.openapi.models.V1APIResource;
import io.kubernetes.client.openapi.models.V1GroupVersionForDiscovery;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.options.ListOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_GROUP;
import org.jdrupes.vmoperator.common.K8s;
import org.jdrupes.vmoperator.common.K8sDynamicModel;
import org.jdrupes.vmoperator.common.K8sDynamicStub;
import static org.jdrupes.vmoperator.manager.Constants.APP_NAME;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_KIND_VM;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_NAME;
import org.jdrupes.vmoperator.manager.events.Exit;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
import org.jdrupes.vmoperator.manager.events.VmDefChanged.Type;
import org.jdrupes.vmoperator.util.GsonPtr;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.Components;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.Start;
import org.jgrapes.core.events.Stop;
import org.jgrapes.util.events.ConfigurationUpdate;

/**
 * Watches for changes of VM definitions.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class VmWatcher extends Component {

    private String namespaceToWatch;
    private final Map<String, VmChannel> channels = new ConcurrentHashMap<>();

    /**
     * Instantiates a new VM definition watcher.
     *
     * @param componentChannel the component channel
     */
    public VmWatcher(Channel componentChannel) {
        super(componentChannel);
    }

    /**
     * Configure the component.
     *
     * @param event the event
     */
    @Handler
    public void onConfigurationUpdate(ConfigurationUpdate event) {
        event.structured(Components.manager(parent()).componentPath())
            .ifPresent(c -> {
                if (c.containsKey("namespace")) {
                    namespaceToWatch = (String) c.get("namespace");
                }
            });
    }

    /**
     * Handle the start event.
     *
     * @param event the event
     * @throws IOException 
     * @throws ApiException 
     */
    @Handler(priority = 10)
    public void onStart(Start event) {
        try {
            startWatching();
        } catch (IOException | ApiException e) {
            logger.log(Level.SEVERE, e,
                () -> "Cannot watch VMs, terminating.");
            event.cancel(true);
            fire(new Exit(1));
        }
    }

    private void startWatching() throws IOException, ApiException {
        // Get namespace
        if (namespaceToWatch == null) {
            var path = Path
                .of("/var/run/secrets/kubernetes.io/serviceaccount/namespace");
            if (Files.isReadable(path)) {
                namespaceToWatch = Files.lines(path).findFirst().orElse(null);
            }
        }
        // Availability already checked by Controller.onStart
        logger.fine(() -> "Watching namespace \"" + namespaceToWatch + "\".");

        // Get all our API versions
        var client = Config.defaultClient();
        var apis = new ApisApi(client).getAPIVersions();
        var vmOpApiVersions = apis.getGroups().stream()
            .filter(g -> g.getName().equals(VM_OP_GROUP)).findFirst()
            .map(V1APIGroup::getVersions).stream().flatMap(l -> l.stream())
            .map(V1GroupVersionForDiscovery::getVersion).toList();

        // Remove left overs
        var coa = new CustomObjectsApi(client);
        purge(client, coa, vmOpApiVersions);

        // Start a watcher thread for each existing CRD version.
        // The watcher will send us an "ADDED" for each existing VM.
        for (var version : vmOpApiVersions) {
            coa.getAPIResources(VM_OP_GROUP, version)
                .getResources().stream()
                .filter(r -> VM_OP_KIND_VM.equals(r.getKind()))
                .findFirst()
                .ifPresent(crd -> watchVmDefs(crd, version));
        }
    }

    @SuppressWarnings("PMD.CognitiveComplexity")
    private void purge(ApiClient client, CustomObjectsApi coa,
            List<String> vmOpApiVersions) throws ApiException {
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
            var listObj = api.list(namespaceToWatch, opts).getObject();
            if (listObj == null) {
                continue;
            }
            for (var obj : listObj.getItems()) {
                String instance = obj.getMetadata().getLabels()
                    .get("app.kubernetes.io/instance");
                if (!known.contains(instance)) {
                    var resName = obj.getMetadata().getName();
                    var result = api.delete(namespaceToWatch, resName);
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
        for (var item : api.list(namespaceToWatch).getObject().getItems()) {
            if (!VM_OP_KIND_VM.equals(item.getKind())) {
                continue;
            }
            result.add(item.getMetadata().getName());
        }
        return result;
    }

    private void watchVmDefs(V1APIResource crd, String version) {
        @SuppressWarnings({ "PMD.AvoidInstantiatingObjectsInLoops",
            "PMD.AvoidCatchingThrowable", "PMD.AvoidCatchingGenericException" })
        var watcher = new Thread(() -> {
            try {
                logger.info(() -> "Watching objects created from "
                    + crd.getName() + "." + VM_OP_GROUP + "/" + version);
                // Watch sometimes terminates without apparent reason.
                while (true) {
                    Instant startedAt = Instant.now();
                    var client = Config.defaultClient();
                    var coa = new CustomObjectsApi(client);
                    var call = coa.listNamespacedCustomObjectCall(VM_OP_GROUP,
                        version, namespaceToWatch, crd.getName(), null, false,
                        null, null, null, null, null, null, null, true, null);
                    try (Watch<V1Namespace> watch
                        = Watch.createWatch(client, call,
                            new TypeToken<Watch.Response<V1Namespace>>() {
                            }.getType())) {
                        for (Watch.Response<V1Namespace> item : watch) {
                            handleVmDefinitionChange(crd, item);
                        }
                    } catch (IOException | ApiException | RuntimeException e) {
                        logger.log(Level.FINE, e, () -> "Problem watching \""
                            + crd.getName() + "\" (will retry): "
                            + e.getMessage());
                        delayRestart(startedAt);
                    }
                }
            } catch (Throwable e) {
                logger.log(Level.SEVERE, e, () -> "Probem watching: "
                    + e.getMessage());
            }
            fire(new Stop());
        });
        watcher.setDaemon(true);
        watcher.start();
    }

    @SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
    private void delayRestart(Instant started) {
        var runningFor = Duration
            .between(started, Instant.now()).toMillis();
        if (runningFor < 5000) {
            logger.log(Level.FINE, () -> "Waiting... ");
            try {
                Thread.sleep(5000 - runningFor);
            } catch (InterruptedException e1) { // NOPMD
                // Retry
            }
            logger.log(Level.FINE, () -> "Retrying");
        }
    }

    private void handleVmDefinitionChange(V1APIResource vmsCrd,
            Watch.Response<V1Namespace> vmDefRef) throws ApiException {
        V1ObjectMeta metadata = vmDefRef.object.getMetadata();
        VmChannel channel = channels.computeIfAbsent(metadata.getName(),
            k -> {
                try {
                    return new VmChannel(channel(), newEventPipeline(),
                        Config.defaultClient());
                } catch (IOException e) {
                    logger.log(Level.SEVERE, e, () -> "Failed to create client"
                        + " for handling changes: " + e.getMessage());
                    return null;
                }
            });
        if (channel == null) {
            return;
        }

        // Get full definition and associate with channel as backup
        @SuppressWarnings("PMD.ShortVariable")
        var gv = GroupVersion.parse(vmDefRef.object.getApiVersion());
        var vmObj = K8sDynamicStub.get(channel.client(),
            new GroupVersionKind(gv.getGroup(), gv.getVersion(), VM_OP_KIND_VM),
            metadata.getNamespace(), metadata.getName());
        K8sDynamicModel vmDef = channel.vmDefinition();
        if (vmObj.isPresent()) {
            vmDef = vmObj.get().state();
            addDynamicData(channel.client(), vmDef);
            channel.setVmDefinition(vmDef);
        }

        // Create and fire event
        channel.pipeline().fire(new VmDefChanged(VmDefChanged.Type
            .valueOf(vmDefRef.type),
            channel
                .setGeneration(vmDefRef.object.getMetadata().getGeneration()),
            vmsCrd, vmDef), channel);
    }

    private void addDynamicData(ApiClient client, K8sDynamicModel vmState) {
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
        var podList = K8s.podApi(client).list(namespaceToWatch, podSearch);
        podList.getObject().getItems().stream().forEach(pod -> {
            rootNode.addProperty("nodeName", pod.getSpec().getNodeName());
        });
    }

    /**
     * Remove VM channel when VM is deleted.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler(priority = -10_000)
    public void onVmDefChanged(VmDefChanged event, VmChannel channel) {
        if (event.type() == Type.DELETED) {
            channels.remove(event.vmDefinition().getMetadata().getName());
        }
    }

}
