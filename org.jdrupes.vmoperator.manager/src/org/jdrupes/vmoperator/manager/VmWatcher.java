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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_GROUP;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_KIND_VM;
import org.jdrupes.vmoperator.manager.VmDefChanged.Type;
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

    private ApiClient client;
    private String namespaceToWatch;
    private final Map<String, VmChannel> channels
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
     * On configuration update.
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
    public void onStart(Start event) throws IOException, ApiException {
        // Get namespace
        if (namespaceToWatch == null) {
            var path = Path
                .of("/var/run/secrets/kubernetes.io/serviceaccount/namespace");
            if (Files.isReadable(path)) {
                namespaceToWatch = Files.lines(path).findFirst().orElse(null);
            }
        }
        if (namespaceToWatch == null) {
            logger.severe(() -> "Namespace to watch not configured and"
                + " no file in kubernetes directory.");
            event.cancel(true);
            fire(new Stop());
            return;
        }
        logger
            .fine(() -> "Controlling namespace \"" + namespaceToWatch + "\".");

        // Get all our API versions
        client = Configuration.getDefaultApiClient();
        var apis = new ApisApi(client).getAPIVersions();
        var vmOpApiVersions = apis.getGroups().stream()
            .filter(g -> g.getName().equals(VM_OP_GROUP)).findFirst()
            .map(V1APIGroup::getVersions).stream().flatMap(l -> l.stream())
            .map(V1GroupVersionForDiscovery::getVersion).toList();

        // Remove left overs
        var coa = new CustomObjectsApi(client);
        purge(coa, vmOpApiVersions);

        // Start a watcher thread for each existing CRD version.
        // The watcher will send us an "ADDED" for each existing VM.
        for (var version : vmOpApiVersions) {
            coa.getAPIResources(VM_OP_GROUP, version)
                .getResources().stream()
                .filter(r -> Constants.VM_OP_KIND_VM.equals(r.getKind()))
                .findFirst()
                .ifPresent(crd -> watchVmDefs(coa, crd, version));
        }
    }

    @SuppressWarnings({ "PMD.AvoidInstantiatingObjectsInLoops",
        "PMD.CognitiveComplexity" })
    private void purge(CustomObjectsApi coa, List<String> vmOpApiVersions)
            throws ApiException {
        // Get existing CRs (VMs)
        Set<String> known = new HashSet<>();
        for (var version : vmOpApiVersions) {
            // Get all known CR instances.
            coa.getAPIResources(VM_OP_GROUP, version)
                .getResources().stream()
                .filter(r -> Constants.VM_OP_KIND_VM.equals(r.getKind()))
                .findFirst()
                .ifPresent(crd -> known.addAll(getKnown(crd, version)));
        }

        ListOptions opts = new ListOptions();
        opts.setLabelSelector(
            "app.kubernetes.io/managed-by=" + Constants.VM_OP_NAME + ","
                + "app.kubernetes.io/name=" + Constants.APP_NAME);
        for (String resource : List.of("apps/v1/statefulsets",
            "v1/configmaps", "v1/secrets")) {
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

    private Set<String> getKnown(V1APIResource crd, String version) {
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

    private void watchVmDefs(CustomObjectsApi coa, V1APIResource crd,
            String version) {
        @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
        var watcher = new Thread(() -> {
            try {
                // Watch sometimes terminates without apparent reason.
                while (true) {
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

    private void handleVmDefinitionChange(V1APIResource vmsCrd,
            Watch.Response<V1Namespace> item) {
        V1ObjectMeta metadata = item.object.getMetadata();
        VmChannel channel = channels.computeIfAbsent(metadata.getName(),
            k -> new VmChannel(channel(), newEventPipeline(), client));
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
    public void onVmDefChanged(VmDefChanged event, VmChannel channel) {
        if (event.type() == Type.DELETED) {
            channels.remove(event.object().getMetadata().getName());
        }
    }

}