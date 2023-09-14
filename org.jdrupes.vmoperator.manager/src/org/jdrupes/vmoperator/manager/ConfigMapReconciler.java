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

import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import io.kubernetes.client.util.generic.dynamic.Dynamics;
import io.kubernetes.client.util.generic.options.ListOptions;
import io.kubernetes.client.util.generic.options.PatchOptions;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.logging.Logger;
import static org.jdrupes.vmoperator.manager.Constants.APP_NAME;
import static org.jdrupes.vmoperator.util.Constants.VM_OP_NAME;
import org.jdrupes.vmoperator.util.K8s;
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
     * @param event the event
     * @param model the model
     * @param channel the channel
     * @return the dynamic kubernetes object
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws TemplateException the template exception
     * @throws ApiException the api exception
     */
    public DynamicKubernetesObject reconcile(VmDefChanged event,
            Map<String, Object> model, VmChannel channel)
            throws IOException, TemplateException, ApiException {
        // Get API
        DynamicKubernetesApi cmApi = new DynamicKubernetesApi("", "v1",
            "configmaps", channel.client());

        // Combine template and data and parse result
        var fmTemplate = fmConfig.getTemplate("runnerConfig.ftl.yaml");
        StringWriter out = new StringWriter();
        fmTemplate.process(model, out);
        // Avoid Yaml.load due to
        // https://github.com/kubernetes-client/java/issues/2741
        var mapDef = Dynamics.newFromYaml(
            new Yaml(new SafeConstructor(new LoaderOptions())), out.toString());

        // Apply and maybe force pod update
        var newState = K8s.apply(cmApi, mapDef, out.toString());
        maybeForceUpdate(channel.client(), newState);
        return newState;
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
                + "app.kubernetes.io/name=" + APP_NAME);
        // Get pod, selected by label
        var podApi = new DynamicKubernetesApi("", "v1", "pods", client);
        var pods = podApi
            .list(newCm.getMetadata().getNamespace(), listOpts).getObject();

        // If the VM is being created, the pod may not exist yet.
        if (pods == null || pods.getItems().size() == 0) {
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

}
