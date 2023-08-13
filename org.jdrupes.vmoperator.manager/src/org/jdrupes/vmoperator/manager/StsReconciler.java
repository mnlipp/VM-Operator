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
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.Dynamics;
import io.kubernetes.client.util.generic.options.PatchOptions;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.logging.Logger;
import org.jdrupes.vmoperator.manager.VmDefChanged.Type;

/**
 * Delegee for reconciling the stateful set (effectively the pod).
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
/* default */ class StsReconciler {

    protected final Logger logger = Logger.getLogger(getClass().getName());
    private final Configuration fmConfig;

    /**
     * Instantiates a new config map reconciler.
     *
     * @param fmConfig the fm config
     */
    public StsReconciler(Configuration fmConfig) {
        this.fmConfig = fmConfig;
    }

    /**
     * Reconcile stateful set.
     *
     * @param event the event
     * @param model the model
     * @param channel the channel
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws TemplateException the template exception
     * @throws ApiException the api exception
     */
    public void reconcile(VmDefChanged event, Map<String, Object> model,
            VmChannel channel)
            throws IOException, TemplateException, ApiException {
        DynamicKubernetesApi stsApi = new DynamicKubernetesApi("apps", "v1",
            "statefulsets", channel.client());

        // Maybe delete
        if (event.type() == Type.DELETED) {
            var meta = GsonPtr.to((JsonObject) model.get("cr")).to("metadata");
            PatchOptions opts = new PatchOptions();
            opts.setFieldManager("kubernetes-java-kubectl-apply");
            stsApi.patch(meta.getAsString("namespace").get(),
                meta.getAsString("name").get(), V1Patch.PATCH_FORMAT_JSON_PATCH,
                new V1Patch("[{\"op\": \"replace\", "
                    + "\"path\": \"/spec/replicas\", \"value\": 0}]"),
                opts).throwsApiException();
            stsApi.delete(meta.getAsString("namespace").get(),
                meta.getAsString("name").get()).throwsApiException();
            return;
        }

        // Never change existing if replicas is greater 0 (would cause
        // update and thus VM restart). Apply will happen when replicas
        // changes from 0 to 1, i.e. on next powerdown/powerup.
        var metadata = event.object().getMetadata();
        var existing = K8s.get(stsApi, metadata);
        if (existing.isPresent() && GsonPtr.to(existing.get().getRaw())
            .to("spec").getAsInt("replicas").orElse(1) > 0) {
            return;
        }

        // Combine template and data and parse result
        var fmTemplate = fmConfig.getTemplate("runnerSts.ftl.yaml");
        StringWriter out = new StringWriter();
        fmTemplate.process(model, out);
        // Avoid Yaml.load due to
        // https://github.com/kubernetes-client/java/issues/2741
        var stsDef = Dynamics.newFromYaml(out.toString());
        PatchOptions opts = new PatchOptions();
        opts.setForce(false);
        opts.setFieldManager("kubernetes-java-kubectl-apply");
        stsApi.patch(stsDef.getMetadata().getNamespace(),
            stsDef.getMetadata().getName(), V1Patch.PATCH_FORMAT_APPLY_YAML,
            new V1Patch(channel.client().getJSON().serialize(stsDef)),
            opts).throwsApiException();
    }

}
