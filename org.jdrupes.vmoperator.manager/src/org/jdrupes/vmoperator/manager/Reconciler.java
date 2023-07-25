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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import io.kubernetes.client.util.generic.options.PatchOptions;
import java.util.Collections;
import static org.jdrupes.vmoperator.manager.Constants.APP_NAME;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_GROUP;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_NAME;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_VERSION;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;

/**
 * Adapts Kubenetes resources to changes in VM definitions (CRs). 
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
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
     * @throws KubectlException 
     */
    @Handler
    public void onVmDefChanged(VmDefChanged event, WatchChannel channel)
            throws ApiException {
        DynamicKubernetesApi vmDefApi = new DynamicKubernetesApi(VM_OP_GROUP,
            VM_OP_VERSION, event.crd().getName(), channel.client());
        var defMeta = event.metadata();
        var vmDef = vmDefApi.get(defMeta.getNamespace(), defMeta.getName())
            .getObject();

        @SuppressWarnings("PMD.AvoidDuplicateLiterals")
        var disks = GsonPtr.to(vmDef.getRaw())
            .get(JsonArray.class, "spec", "vm", "disks")
            .map(JsonArray::asList).orElse(Collections.emptyList());
        int index = 0;
        for (var disk : disks) {
            reconcileDisk(vmDef, index++, (JsonObject) disk, channel);
        }
    }

    @SuppressWarnings({ "PMD.AvoidDuplicateLiterals", "PMD.ConfusingTernary" })
    private void reconcileDisk(DynamicKubernetesObject vmDefinition,
            int index, JsonObject diskDef, WatchChannel channel)
            throws ApiException {
        var pvcObject = new DynamicKubernetesObject();
        pvcObject.setApiVersion("v1");
        pvcObject.setKind("PersistentVolumeClaim");
        var pvcDef = GsonPtr.to(pvcObject.getRaw());
        var vmDef = GsonPtr.to(vmDefinition.getRaw());
        var pvcTpl = GsonPtr.to(diskDef).to("volumeClaimTemplate");

        // Copy metadata from template and add missing/additional data.
        var vmName = vmDef.getAsString("metadata", "name").orElse("default");
        pvcDef.get(JsonObject.class).add("metadata",
            pvcTpl.to("metadata").get(JsonObject.class).deepCopy());
        var defMeta = pvcDef.to("metadata");
        defMeta.computeIfAbsent("namespace", () -> new JsonPrimitive(
            vmDef.getAsString("metadata", "namespace").orElse("default")));
        defMeta.computeIfAbsent("name", () -> new JsonPrimitive(
            vmName + "-disk-" + index));
        var pvcLbls = pvcDef.to("metadata", "labels");
        pvcLbls.set("app.kubernetes.io/name", APP_NAME);
        pvcLbls.set("app.kubernetes.io/instance", vmName);
        pvcLbls.set("app.kubernetes.io/component", "disk");
        pvcLbls.set("app.kubernetes.io/managed-by", VM_OP_NAME);

        // Get API and check if PVC exists
        DynamicKubernetesApi pvcApi = new DynamicKubernetesApi("", "v1",
            "persistentvolumeclaims", channel.client());
        var existing = pvcApi.get(defMeta.getAsString("namespace").get(),
            defMeta.getAsString("name").get());

        // If PVC does not exist, create. Else patch (apply)
        if (!existing.isSuccess()) {
            // PVC does not exist yet, copy spec from template
            pvcDef.get(JsonObject.class).add("spec",
                pvcTpl.to("spec").get(JsonObject.class).deepCopy());
            // Add missing
            pvcDef.to("spec").computeIfAbsent("accessModes",
                () -> GsonPtr.to(new JsonArray()).set(0, "ReadWriteOnce")
                    .get());
            pvcDef.to("spec").computeIfAbsent("volumeMode", "Block");
            pvcApi.create(pvcObject);
        } else {
            // spec is immutable, so mix in existing spec
            pvcDef.set("spec", GsonPtr.to(existing.getObject().getRaw())
                .to("spec").get().deepCopy());
            PatchOptions opts = new PatchOptions();
            opts.setForce(false);
            opts.setFieldManager("kubernetes-java-kubectl-apply");
            pvcApi.patch(pvcObject.getMetadata().getNamespace(),
                pvcObject.getMetadata().getName(),
                V1Patch.PATCH_FORMAT_APPLY_YAML,
                new V1Patch(channel.client().getJSON().serialize(pvcObject)),
                opts).throwsApiException();
        }
    }

}
