/*
 * VM-Operator
 * Copyright (C) 2024 Michael N. Lipp
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

import io.kubernetes.client.Discovery.APIResource;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1APIResource;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watch.Response;

import java.io.IOException;
import java.util.logging.Level;
import static org.jdrupes.vmoperator.manager.Constants.APP_NAME;
import static org.jdrupes.vmoperator.manager.Constants.COMP_DISPLAY_SECRET;

import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.manager.events.DisplaySecretChanged;
import org.jdrupes.vmoperator.manager.events.Exit;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jgrapes.core.Channel;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.Start;

/**
 * Watches for changes of display secrets.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class DisplaySecretsWatcher extends AbstractMonitor {

    protected DisplaySecretsWatcher(Channel componentChannel) {
        super(componentChannel, null, null);
        // TODO Auto-generated constructor stub
    }

    @Override
    protected void handleChange(K8sClient client, Response change) {
        // TODO Auto-generated method stub

    }

//    /**
//     * Instantiates a new VM definition watcher.
//     *
//     * @param componentChannel the component channel
//     */
//    public DisplaySecretsWatcher(Channel componentChannel) {
//        super(componentChannel);
//    }
//
//    /**
//     * Handle the start event.
//     *
//     * @param event the event
//     * @throws IOException 
//     * @throws ApiException 
//     */
//    @Handler(priority = 10)
//    public void onStart(Start event) {
//        try {
//            start();
//        } catch (IOException | ApiException e) {
//            logger.log(Level.SEVERE, e,
//                () -> "Cannot watch display-secrets, terminating.");
//            event.cancel(true);
//            fire(new Exit(1));
//        }
//    }
//
//    @Override
//    protected void start() throws IOException, ApiException {
//        // Build call
//        var client = Config.defaultClient();
//        var api = new CoreV1Api(client);
//        var call = api.listNamespacedSecretCall(namespaceToWatch(),
//            null, false, null, null,
//            "app.kubernetes.io/name=" + APP_NAME
//                + ",app.kubernetes.io/component="
//                + COMP_DISPLAY_SECRET,
//            null, null, null, null, null, true, null);
//        V1APIResource resource = new V1APIResource();
//        resource.setName("secrets");
//        resource.setVersion(new V1Secret().getApiVersion());
//        startWatcher(resource, call, new TypeToken<Watch.Response<V1Secret>>() {
//        }.getType(), this::handleSecretChange);
//    }
//
//    @SuppressWarnings("PMD.UnusedFormalParameter")
//    private void handleSecretChange(V1APIResource resource,
//            Watch.Response<V1Secret> secret) {
//        String vmName = secret.object.getMetadata().getLabels()
//            .get("app.kubernetes.io/instance");
//        if (vmName == null) {
//            logger.warning(() -> "Secret "
//                + secret.object.getMetadata().getName() + " misses required"
//                + " label app.kubernetes.io/instance");
//            return;
//        }
//        VmChannel channel = channel(vmName, false);
//        if (channel == null) {
//            return;
//        }
//        channel.pipeline().fire(new DisplaySecretChanged(
//            DisplaySecretChanged.Type.valueOf(secret.type), secret.object),
//            channel);
//    }
//
}
