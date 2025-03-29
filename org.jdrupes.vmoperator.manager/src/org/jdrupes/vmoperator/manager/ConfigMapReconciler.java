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

import com.google.gson.JsonObject;
import freemarker.template.AdapterTemplateModel;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.utility.DeepUnwrap;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import io.kubernetes.client.util.generic.dynamic.Dynamics;
import io.kubernetes.client.util.generic.options.ListOptions;
import io.kubernetes.client.util.generic.options.PatchOptions;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import org.jdrupes.vmoperator.common.K8s;
import static org.jdrupes.vmoperator.manager.Constants.APP_NAME;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_NAME;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.util.DataPath;
import org.jdrupes.vmoperator.util.GsonPtr;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Delegee for reconciling the config map
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
/* default */ class ConfigMapReconciler {

    protected final Logger logger = Logger.getLogger(getClass().getName());
    private final Configuration fmConfig;

    /**
     * Instantiates a new config map reconciler.
     *
     * @param fmConfig the fm config
     */
    public ConfigMapReconciler(Configuration fmConfig) {
        this.fmConfig = fmConfig;
    }

    /**
     * Reconcile.
     *
     * @param model the model
     * @param channel the channel
     * @param modelChanged the model has changed
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws TemplateException the template exception
     * @throws ApiException the API exception
     */
    @SuppressWarnings("PMD.AvoidDuplicateLiterals")
    public void reconcile(Map<String, Object> model, VmChannel channel,
            boolean modelChanged)
            throws IOException, TemplateException, ApiException {
        // Check if an update is needed
        var prevData = channel.associated(PrevData.class)
            .orElseGet(() -> new PrevData(null, new HashMap<>()));
        Object newInputs = model.get("loginRequestedFor");
        if (!modelChanged && Objects.equals(prevData.inputs, newInputs)) {
            // Make added data available in new model
            model.putAll(prevData.added);
            return;
        }
        prevData = new PrevData(newInputs, prevData.added);
        channel.setAssociated(PrevData.class, prevData);

        // Combine template and data and parse result
        logger.fine(() -> "Create/update configmap "
            + DataPath.<String> get(model, "cr", "name").orElse("unknown"));
        model.put("adjustCloudInitMeta", adjustCloudInitMetaModel);
        prevData.added.put("adjustCloudInitMeta", adjustCloudInitMetaModel);
        var fmTemplate = fmConfig.getTemplate("runnerConfig.ftl.yaml");
        StringWriter out = new StringWriter();
        fmTemplate.process(model, out);
        // Avoid Yaml.load due to
        // https://github.com/kubernetes-client/java/issues/2741
        var newCm = Dynamics.newFromYaml(
            new Yaml(new SafeConstructor(new LoaderOptions())), out.toString());

        // Maybe override logging.properties from reconciler configuration.
        DataPath.<String> get(model, "reconciler", "loggingProperties")
            .ifPresent(props -> {
                GsonPtr.to(newCm.getRaw()).getAs(JsonObject.class, "data")
                    .get().addProperty("logging.properties", props);
            });

        // Maybe override logging.properties from VM definition.
        DataPath.<String> get(model, "cr", "spec", "loggingProperties")
            .ifPresent(props -> {
                GsonPtr.to(newCm.getRaw()).getAs(JsonObject.class, "data")
                    .get().addProperty("logging.properties", props);
            });

        // Get API and update
        DynamicKubernetesApi cmApi = new DynamicKubernetesApi("", "v1",
            "configmaps", channel.client());

        // Apply and maybe force pod update
        var updatedCm = K8s.apply(cmApi, newCm, newCm.getRaw().toString());
        maybeForceUpdate(channel.client(), updatedCm);
        model.put("configMapResourceVersion",
            updatedCm.getMetadata().getResourceVersion());
        prevData.added.put("configMapResourceVersion",
            updatedCm.getMetadata().getResourceVersion());
    }

    /**
     * Key for association.
     */
    private record PrevData(Object inputs, Map<String, Object> added) {
    }

    /**
     * Triggers update of config map mounted in pod
     * See https://ahmet.im/blog/kubernetes-secret-volumes-delay/
     * @param client 
     * 
     * @param newCm
     */
    private void maybeForceUpdate(ApiClient client,
            DynamicKubernetesObject newCm) {
        ListOptions listOpts = new ListOptions();
        listOpts.setLabelSelector(
            "app.kubernetes.io/managed-by=" + VM_OP_NAME + ","
                + "app.kubernetes.io/name=" + APP_NAME + ","
                + "app.kubernetes.io/instance=" + newCm.getMetadata()
                    .getLabels().get("app.kubernetes.io/instance"));
        // Get pod, selected by label
        var podApi = new DynamicKubernetesApi("", "v1", "pods", client);
        var pods = podApi
            .list(newCm.getMetadata().getNamespace(), listOpts).getObject();

        // If the VM is being created, the pod may not exist yet.
        if (pods == null || pods.getItems().isEmpty()) {
            return;
        }
        var pod = pods.getItems().get(0);

        // Patch pod annotation
        PatchOptions patchOpts = new PatchOptions();
        patchOpts.setFieldManager("kubernetes-java-kubectl-apply");
        var podMeta = pod.getMetadata();
        var res = podApi.patch(podMeta.getNamespace(), podMeta.getName(),
            V1Patch.PATCH_FORMAT_JSON_PATCH,
            new V1Patch("[{\"op\": \"replace\", \"path\": "
                + "\"/metadata/annotations/vmrunner.jdrupes.org~1cmVersion\", "
                + "\"value\": \"" + newCm.getMetadata().getResourceVersion()
                + "\"}]"),
            patchOpts);
        if (!res.isSuccess()) {
            logger.warning(
                () -> "Cannot patch pod annotations: " + res.getStatus());
        }
    }

    private final TemplateMethodModelEx adjustCloudInitMetaModel
        = new TemplateMethodModelEx() {
            @Override
            @SuppressWarnings("PMD.PreserveStackTrace")
            public Object exec(@SuppressWarnings("rawtypes") List arguments)
                    throws TemplateModelException {
                @SuppressWarnings("unchecked")
                var res = new HashMap<>((Map<String, Object>) DeepUnwrap
                    .unwrap((TemplateModel) arguments.get(0)));
                var metadata
                    = (V1ObjectMeta) ((AdapterTemplateModel) arguments.get(1))
                        .getAdaptedObject(Object.class);
                if (!res.containsKey("instance-id")) {
                    res.put("instance-id",
                        Optional.ofNullable(metadata.getGeneration())
                            .map(s -> "v" + s).orElse("v1"));
                }
                if (!res.containsKey("local-hostname")) {
                    res.put("local-hostname", metadata.getName());
                }
                return res;
            }
        };

}
