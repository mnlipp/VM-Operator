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

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_GROUP;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_VERSION;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;

/**
 * Adapts Kubenetes resources to changes in VM definitions (CRs). 
 */
public class Reconciler extends Component {

    /**
     * Instantiates a new reconciler.
     *
     * @param componentChannel the component channel
     */
    public Reconciler(Channel componentChannel) {
        super(componentChannel);
    }

    /**
     * Handles the change event.
     *
     * @param event the event
     * @param channel the channel
     * @throws ApiException the api exception
     */
    @Handler
    public void onVmDefChanged(VmDefChanged event, WatchChannel channel)
            throws ApiException {
        DynamicKubernetesApi vmDefApi = new DynamicKubernetesApi(VM_OP_GROUP,
            VM_OP_VERSION, event.crd().getName(), channel.client());
        var defMeta = event.metadata();
        var vmDef = vmDefApi.get(defMeta.getNamespace(), defMeta.getName());

//        DynamicKubernetesApi cmApi = new DynamicKubernetesApi("", "v1",
//            "configmaps", channel.client());
//        var cm = new DynamicKubernetesObject();
//        cm.setApiVersion("v1");
//        cm.setKind("ConfigMap");
//        V1ObjectMeta metadata = new V1ObjectMeta();
//        metadata.setNamespace("default");
//        metadata.setName("test");
//        cm.setMetadata(metadata);
//        JsonObject data = new JsonObject();
//        data.addProperty("test", "value");
//        cm.getRaw().add("data", data);
//
//        var response = cmApi.create("default", cm, new CreateOptions())
//            .throwsApiException();

//        var obj = channel.coa().getNamespacedCustomObject(VM_OP_GROUP, VM_OP_VERSION,
//            md.getNamespace(), event.crd().getName(), md.getName());
        event = null;

    }

}
