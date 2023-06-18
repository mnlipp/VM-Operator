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

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.LogManager;
import org.jdrupes.vmoperator.util.FsdUtils;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.Components;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.Start;
import org.jgrapes.core.events.Stop;
import org.jgrapes.io.NioDispatcher;

public class Manager extends Component {

    public static final String APP_NAME = "vmoperator";
    private static Manager app;

    public Manager() throws IOException {
        // Attach a general nio dispatcher
        attach(new NioDispatcher());
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

        CoreV1Api api = new CoreV1Api();
        V1PodList list = api.listPodForAllNamespaces(null, null, null, null,
            null, null, null, null, null, null);
        for (V1Pod item : list.getItems()) {
            System.out.println(item.getMetadata().getName());
        }

//        CustomObjectsApi cApi = new CustomObjectsApi();
//        var obj = cApi.getNamespacedCustomObject("vmoperator.jdrupes.org", "v1",
//            "default", "vms", "test");
//        obj = null;
    }

    @Handler
    public void onStop(Stop event) {
        System.out.println("(Done.)");
    }

    static {
        try {
            InputStream props;
            var path = FsdUtils.findConfigFile(Manager.APP_NAME,
                "logging.properties");
            if (path.isPresent()) {
                props = Files.newInputStream(path.get());
            } else {
                props = Manager.class.getResourceAsStream("logging.properties");
            }
            LogManager.getLogManager().readConfiguration(props);
        } catch (IOException e) {
            e.printStackTrace(); // NOPMD
        }
    }

    /**
     * The main method.
     *
     * @param args the arguments
     * @throws Exception the exception
     */
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public static void main(String[] args) throws Exception {
        // The Manager is the root component
        app = new Manager();

        // Prepare Stop
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                app.fire(new Stop(), Channel.BROADCAST);
                Components.awaitExhaustion();
            } catch (InterruptedException e) {
                // Cannot do anything about this.
            }
        }));

        // Start application
        Components.start(app);
    }

}
