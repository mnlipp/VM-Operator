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
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1APIResource;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import okhttp3.Call;
import org.jdrupes.vmoperator.manager.VmChangedEvent.Type;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.Start;
import org.jgrapes.core.events.Stop;

/**
 * Watches for changes of VM definitions.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class VmDefinitionWatcher extends Component {

    private static final String CR_GROUP = "vmoperator.jdrupes.org";
    private static final String CR_VERSION = "v1";
    private static final String CR_KIND = "VirtualMachine";
    private CoreV1Api api;
    private CustomObjectsApi coa;
    private V1APIResource vmsCrd;
    private String managedNamespace = "default";
    private final Map<String, WatchChannel> channels
        = new ConcurrentHashMap<>();

    /**
     * Instantiates a new VM definition watcher.
     *
     * @param componentChannel the component channel
     */
    public VmDefinitionWatcher(Channel componentChannel) {
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
        ApiClient client = Config.defaultClient();
        Configuration.setDefaultApiClient(client);

        // Get access to APIs
        api = new CoreV1Api();
        coa = new CustomObjectsApi();

        // Derive all information from the CRD
        var resources = coa.getAPIResources(CR_GROUP, CR_VERSION);
        vmsCrd = resources.getResources().stream()
            .filter(r -> CR_KIND.equals(r.getKind())).findFirst().get();

        // Watch the resources (vm definitions)
        Call call = coa.listNamespacedCustomObjectCall(
            CR_GROUP, CR_VERSION, managedNamespace, vmsCrd.getName(), null,
            false, null, null, null, null, null, null, null, true, null);
        new Thread(() -> {
            try (Watch<V1Namespace> watch = Watch.createWatch(client,
                call, new TypeToken<Watch.Response<V1Namespace>>() {
                }.getType())) {
                for (Watch.Response<V1Namespace> item : watch) {
                    handleCrEvent(item);
                }
            } catch (IOException | ApiException e) {
                logger.log(Level.FINE, e, () -> "Probem while watching: "
                    + e.getMessage());
            }
            fire(new Stop());

        }).start();
    }

    private void handleCrEvent(Watch.Response<V1Namespace> item) {
        V1ObjectMeta metadata = item.object.getMetadata();
        WatchChannel channel = channels.computeIfAbsent(metadata.getName(),
            k -> new WatchChannel(channel(), newEventPipeline(), api, coa));
        channel.pipeline().fire(new VmChangedEvent(
            VmChangedEvent.Type.valueOf(item.type), metadata), channel);
    }

    /**
     * Remove VM channel when VM is deleted.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler(priority = -10_000)
    public void onVmChanged(VmChangedEvent event, WatchChannel channel) {
        if (event.type() == Type.DELETED) {
            channels.remove(event.metadata().getName());
        }
    }

}
