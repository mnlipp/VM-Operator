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

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.ApisApi;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1APIGroup;
import io.kubernetes.client.openapi.models.V1APIResource;
import io.kubernetes.client.openapi.models.V1GroupVersionForDiscovery;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.options.ListOptions;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import okhttp3.Call;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_GROUP;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_KIND_VM;
import org.jdrupes.vmoperator.manager.VmDefChanged.Type;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.Start;
import org.jgrapes.core.events.Stop;

/**
 * Watches for changes of VM definitions.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class VmWatcher extends Component {

    private ApiClient client;
    private String managedNamespace = "qemu-vms";
    private final Map<String, WatchChannel> channels
        = new ConcurrentHashMap<>();

    /**
     * Instantiates a new VM definition watcher.
     *
     * @param componentChannel the component channel
     */
    public VmWatcher(Channel componentChannel) {
        super(componentChannel);
    }

    /**
     * Handle the start event.
     *
     * @param event the event
     * @throws IOException 
     * @throws ApiException 
     */
    @Handler
    public void onStart(Start event) throws IOException, ApiException {
        client = Configuration.getDefaultApiClient();
        // Get all our API versions
        var apis = new ApisApi(client).getAPIVersions();
        var vmOpApiVersions = apis.getGroups().stream()
            .filter(g -> g.getName().equals(VM_OP_GROUP)).findFirst()
            .map(V1APIGroup::getVersions).stream().flatMap(l -> l.stream())
            .map(V1GroupVersionForDiscovery::getVersion).toList();

        // Remove left overs
        var coa = new CustomObjectsApi(client);
        purge(coa, vmOpApiVersions);

        // Start a watcher for each existing CRD version.
        for (var version : vmOpApiVersions) {
            coa.getAPIResources(VM_OP_GROUP, version)
                .getResources().stream()
                .filter(r -> Constants.VM_OP_KIND_VM.equals(r.getKind()))
                .findFirst()
                .ifPresent(crd -> serveCrVersion(coa, crd, version));
        }
    }

    private void purge(CustomObjectsApi coa, List<String> vmOpApiVersions)
            throws ApiException {
        // Get existing CRs (VMs)
        Set<String> known = new HashSet<>();
        for (var version : vmOpApiVersions) {
            // Start a watcher for each existing CRD version.
            coa.getAPIResources(VM_OP_GROUP, version)
                .getResources().stream()
                .filter(r -> Constants.VM_OP_KIND_VM.equals(r.getKind()))
                .findFirst()
                .ifPresent(crd -> known.addAll(getKnown(crd, version)));
        }

        ListOptions opts = new ListOptions();
        opts.setLabelSelector(
            "app.kubernetes.io/managed-by=vmoperator,"
                + "app.kubernetes.io/name=vmrunner");
        for (var version : vmOpApiVersions) {
            for (String resource : List.of("pods", "configmaps",
                "persistentvolumeclaims", "secrets")) {
                // Get resources, selected by label
                @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
                var api
                    = new DynamicKubernetesApi("", version, resource, client);
                for (var obj : api.list(managedNamespace, opts).getObject()
                    .getItems()) {
                    String instance = obj.getMetadata().getLabels()
                        .get("app.kubernetes.io/instance");
                    if (!known.contains(instance)) {
                        api.delete(managedNamespace,
                            obj.getMetadata().getName());
                    }
                }
            }
        }
    }

    private Set<String> getKnown(V1APIResource crd, String version) {
        Set<String> result = new HashSet<>();
        var api = new DynamicKubernetesApi(VM_OP_GROUP, version,
            crd.getName(), client);
        for (var item : api.list(managedNamespace).getObject().getItems()) {
            if (!VM_OP_KIND_VM.equals(item.getKind())) {
                continue;
            }
            result.add(item.getMetadata().getName());
        }
        return result;
    }

    private void serveCrVersion(CustomObjectsApi coa, V1APIResource crd,
            String version) {
        @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
        var watcher = new Thread(() -> {
            try {
                // Watch sometimes terminates without apparent reason.
                while (true) {
                    var call = coa.listNamespacedCustomObjectCall(VM_OP_GROUP,
                        version, managedNamespace, crd.getName(), null, false,
                        null, null, null, null, null, null, null, true, null);
                    try (Watch<V1Namespace> watch
                        = Watch.createWatch(client, call,
                            new TypeToken<Watch.Response<V1Namespace>>() {
                            }.getType())) {
                        for (Watch.Response<V1Namespace> item : watch) {
                            handleVmDefinitionEvent(crd, item);
                        }
                    } catch (IllegalStateException e) {
                        logger.log(Level.FINE, e, () -> "Probem watching: "
                            + e.getMessage());
                    }
                }
            } catch (IOException | ApiException e) {
                logger.log(Level.FINE, e, () -> "Probem watching: "
                    + e.getMessage());
            }
            fire(new Stop());
        });
        watcher.setDaemon(true);
        watcher.start();
    }

    private void handleVmDefinitionEvent(V1APIResource vmsCrd,
            Watch.Response<V1Namespace> item) {
        V1ObjectMeta metadata = item.object.getMetadata();
        WatchChannel channel = channels.computeIfAbsent(metadata.getName(),
            k -> new WatchChannel(channel(), newEventPipeline(), client));
        channel.pipeline().fire(new VmDefChanged(VmDefChanged.Type
            .valueOf(item.type), vmsCrd, item.object), channel);
    }

    /**
     * Remove VM channel when VM is deleted.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler(priority = -10_000)
    public void onVmDefChanged(VmDefChanged event, WatchChannel channel) {
        if (event.type() == Type.DELETED) {
            channels.remove(event.object().getMetadata().getName());
        }
    }

}
