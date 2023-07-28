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
import freemarker.core.ParseException;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.TemplateHashModel;
import freemarker.template.TemplateNotFoundException;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import io.kubernetes.client.util.generic.dynamic.Dynamics;
import io.kubernetes.client.util.generic.options.ListOptions;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.jdrupes.vmoperator.manager.Constants.APP_NAME;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_GROUP;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_NAME;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_VERSION;
import org.jdrupes.vmoperator.manager.VmDefChanged.Type;
import org.jdrupes.vmoperator.util.ExtendedObjectWrapper;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;

/**
 * Adapts Kubenetes resources to changes in VM definitions (CRs). 
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis",
    "PMD.AvoidDuplicateLiterals" })
public class Reconciler extends Component {

    private final Configuration fmConfig;

    /**
     * Instantiates a new reconciler.
     *
     * @param componentChannel the component channel
     */
    public Reconciler(Channel componentChannel) {
        super(componentChannel);

        // Configure freemarker library
        fmConfig = new Configuration(Configuration.VERSION_2_3_32);
        fmConfig.setDefaultEncoding("utf-8");
        fmConfig.setObjectWrapper(new ExtendedObjectWrapper(
            fmConfig.getIncompatibleImprovements()));
        fmConfig.setTemplateExceptionHandler(
            TemplateExceptionHandler.RETHROW_HANDLER);
        fmConfig.setLogTemplateExceptions(false);
        fmConfig.setClassForTemplateLoading(Reconciler.class, "");
    }

    /**
     * Handles the change event.
     *
     * @param event the event
     * @param channel the channel
     * @throws ApiException the api exception
     * @throws IOException 
     * @throws ParseException 
     * @throws MalformedTemplateNameException 
     * @throws TemplateNotFoundException 
     * @throws TemplateException 
     * @throws KubectlException 
     */
    @Handler
    @SuppressWarnings("PMD.ConfusingTernary")
    public void onVmDefChanged(VmDefChanged event, WatchChannel channel)
            throws ApiException, TemplateException, IOException {
        DynamicKubernetesApi vmDefApi = new DynamicKubernetesApi(VM_OP_GROUP,
            VM_OP_VERSION, event.crd().getName(), channel.client());
        var defMeta = event.metadata();

        // Get common data for all reconciles
        DynamicKubernetesObject vmDef = null;
        Map<String, Object> model = null;
        if (event.type() != Type.DELETED) {
            vmDef = K8s.get(vmDefApi, defMeta).get();

            // Prepare Freemarker model
            model = new HashMap<>();
            model.put("cr", vmDef.getRaw());
            model.put("constants",
                (TemplateHashModel) new DefaultObjectWrapperBuilder(
                    Configuration.VERSION_2_3_32)
                        .build().getStaticModels()
                        .get(Constants.class.getName()));
        }

        // Reconcile
        if (event.type() != Type.DELETED) {
            reconcileDataPvc(model, channel);
            reconcileDisks(vmDef, channel);
            reconcileConfigMap(event, model, channel);
            reconcilePod(event, model, channel);
        } else {
            reconcilePod(event, model, channel);
            reconcileConfigMap(event, model, channel);
            deletePvcs(event, channel);
        }
    }

    @SuppressWarnings("PMD.ConfusingTernary")
    private void reconcileDataPvc(Map<String, Object> model,
            WatchChannel channel)
            throws TemplateException, ApiException, IOException {
        // Combine template and data and parse result
        var fmTemplate = fmConfig.getTemplate("vmDataPvc.ftl.yaml");
        StringWriter out = new StringWriter();
        fmTemplate.process(model, out);
        // Avoid Yaml.load due to
        // https://github.com/kubernetes-client/java/issues/2741
        var pvcDef = Dynamics.newFromYaml(out.toString());

        // Get API and check if PVC exists
        DynamicKubernetesApi pvcApi = new DynamicKubernetesApi("", "v1",
            "persistentvolumeclaims", channel.client());
        var existing = K8s.get(pvcApi, pvcDef.getMetadata());

        // If PVC does not exist, create. Else patch (apply)
        if (existing.isEmpty()) {
            pvcApi.create(pvcDef);
        } else {
            // spec is immutable, so mix in existing spec
            GsonPtr.to(pvcDef.getRaw()).set("spec", GsonPtr
                .to(existing.get().getRaw()).get(JsonObject.class, "spec")
                .get().deepCopy());
            K8s.apply(pvcApi, existing.get(),
                channel.client().getJSON().serialize(pvcDef));
        }
    }

    private void reconcileDisks(DynamicKubernetesObject vmDef,
            WatchChannel channel) throws ApiException {
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

    private void deletePvcs(VmDefChanged event, WatchChannel channel)
            throws ApiException {
        // Get API and check and list related
        var pvcApi = K8s.pvcApi(channel.client());
        var pvcs = pvcApi.list(event.metadata().getNamespace(),
            new ListOptions().labelSelector(
                "app.kubernetes.io/managed-by=" + VM_OP_NAME
                    + ",app.kubernetes.io/name=" + APP_NAME
                    + ",app.kubernetes.io/instance="
                    + event.metadata().getName()));
        for (var pvc : pvcs.getObject().getItems()) {
            K8s.delete(pvcApi, pvc);
        }
    }

    private void reconcileConfigMap(VmDefChanged event,
            Map<String, Object> model, WatchChannel channel)
            throws IOException, TemplateException, ApiException {
        // Get API and check if exists
        DynamicKubernetesApi cmApi = new DynamicKubernetesApi("", "v1",
            "configmaps", channel.client());
        var existing = K8s.get(cmApi, event.metadata());

        // If deleted, delete
        if (event.type() == Type.DELETED) {
            if (existing.isPresent()) {
                K8s.delete(cmApi, existing.get());
            }
            return;
        }

        // Combine template and data and parse result
        var fmTemplate = fmConfig.getTemplate("runnerConfig.ftl.yaml");
        StringWriter out = new StringWriter();
        fmTemplate.process(model, out);
        // Avoid Yaml.load due to
        // https://github.com/kubernetes-client/java/issues/2741
        var mapDef = Dynamics.newFromYaml(out.toString());

        // Apply
        K8s.apply(cmApi, mapDef, out.toString());
    }

    private void reconcilePod(VmDefChanged event, Map<String, Object> model,
            WatchChannel channel)
            throws IOException, TemplateException, ApiException {
        // Check if exists
        DynamicKubernetesApi podApi = new DynamicKubernetesApi("", "v1",
            "pods", channel.client());
        var existing = K8s.get(podApi, event.metadata());

        // If deleted, delete
        if (event.type() == Type.DELETED) {
            if (existing.isPresent()) {
                K8s.delete(podApi, existing.get());
            }
            return;
        }

        // Combine template and data and parse result
        var fmTemplate = fmConfig.getTemplate("runnerPod.ftl.yaml");
        StringWriter out = new StringWriter();
        fmTemplate.process(model, out);
        // Avoid Yaml.load due to
        // https://github.com/kubernetes-client/java/issues/2741
        var podDef = Dynamics.newFromYaml(out.toString());

        // Nothing can be updated here
        if (existing.isEmpty()) {
            podApi.create(podDef);
        }
    }

}
