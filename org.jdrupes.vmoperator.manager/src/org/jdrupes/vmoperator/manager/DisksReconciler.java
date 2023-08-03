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
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import io.kubernetes.client.util.generic.options.ListOptions;
import java.util.Collections;
import static org.jdrupes.vmoperator.manager.Constants.APP_NAME;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_NAME;

/**
 * Delegee for reconciling the PVCs for the disks
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
/* default */ class DisksReconciler {

    /**
     * Reconcile disks.
     *
     * @param vmDef the vm def
     * @param channel the channel
     * @throws ApiException the api exception
     */
    public void reconcile(DynamicKubernetesObject vmDef,
            WatchChannel channel) throws ApiException {
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
        var pvcRaw = GsonPtr.to(pvcObject.getRaw());
        var vmRaw = GsonPtr.to(vmDefinition.getRaw());
        var pvcTpl = GsonPtr.to(diskDef).to("volumeClaimTemplate");

        // Copy base and metadata from template and add missing/additional data.
        pvcObject.setApiVersion(pvcTpl.getAsString("apiVersion").get());
        pvcObject.setKind(pvcTpl.getAsString("kind").get());
        var vmName = vmRaw.getAsString("metadata", "name").orElse("default");
        pvcRaw.get(JsonObject.class).add("metadata",
            pvcTpl.to("metadata").get(JsonObject.class).deepCopy());
        var defMeta = pvcRaw.to("metadata");
        defMeta.computeIfAbsent("namespace", () -> new JsonPrimitive(
            vmRaw.getAsString("metadata", "namespace").orElse("default")));
        defMeta.computeIfAbsent("name", () -> new JsonPrimitive(
            vmName + "-disk-" + index));
        var pvcLbls = pvcRaw.to("metadata", "labels");
        pvcLbls.set("app.kubernetes.io/name", APP_NAME);
        pvcLbls.set("app.kubernetes.io/instance", vmName);
        pvcLbls.set("app.kubernetes.io/component", "disk");
        pvcLbls.set("app.kubernetes.io/managed-by", VM_OP_NAME);

        // Get API and check if PVC exists
        DynamicKubernetesApi pvcApi = new DynamicKubernetesApi("", "v1",
            "persistentvolumeclaims", channel.client());
        var existing = K8s.get(pvcApi, pvcObject.getMetadata());

        // If PVC does not exist, create. Else patch (apply)
        if (existing.isEmpty()) {
            // PVC does not exist yet, copy spec from template
            pvcRaw.get(JsonObject.class).add("spec",
                pvcTpl.to("spec").get(JsonObject.class).deepCopy());
            pvcApi.create(pvcObject);
        } else {
            // spec is immutable, so mix in existing spec
            pvcRaw.set("spec", GsonPtr.to(existing.get().getRaw())
                .to("spec").get().deepCopy());
            K8s.apply(pvcApi, existing.get(),
                channel.client().getJSON().serialize(pvcObject));
        }
    }

    /**
     * Delete the PVCs generated from the defined disks.
     *
     * @param event the event
     * @param channel the channel
     * @throws ApiException the api exception
     */
    public void deleteDisks(VmDefChanged event, WatchChannel channel)
            throws ApiException {
        // Get API and check and list related
        var pvcApi = K8s.pvcApi(channel.client());
        var pvcs = pvcApi.list(event.object().getMetadata().getNamespace(),
            new ListOptions().labelSelector(
                "app.kubernetes.io/managed-by=" + VM_OP_NAME
                    + ",app.kubernetes.io/name=" + APP_NAME
                    + ",app.kubernetes.io/instance="
                    + event.object().getMetadata().getName()));
        for (var pvc : pvcs.getObject().getItems()) {
            K8s.delete(pvcApi, pvc);
        }
    }

}
