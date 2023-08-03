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
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import static org.jdrupes.vmoperator.manager.Constants.STATE_STOPPED;
import org.jdrupes.vmoperator.manager.VmDefChanged.Type;

/**
 * Delegee for reconciling the pod.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
/* default */ class PodReconciler {

    private final Configuration fmConfig;

    /**
     * Instantiates a new config map reconciler.
     *
     * @param fmConfig the fm config
     */
    public PodReconciler(Configuration fmConfig) {
        this.fmConfig = fmConfig;
    }

    /**
     * Reconcile pod.
     *
     * @param event the event
     * @param model the model
     * @param channel the channel
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws TemplateException the template exception
     * @throws ApiException the api exception
     */
    public void reconcile(VmDefChanged event, Map<String, Object> model,
            WatchChannel channel)
            throws IOException, TemplateException, ApiException {
        // Check if exists
        DynamicKubernetesApi podApi = new DynamicKubernetesApi("", "v1",
            "pods", channel.client());
        var existing = K8s.get(podApi, event.object().getMetadata());

        // Get state
        var state = GsonPtr.to((JsonObject) model.get("cr")).to("spec", "vm")
            .getAsString("state").get();

        // If deleted or stopped, delete
        if (event.type() == Type.DELETED || STATE_STOPPED.equals(state)) {
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

        // Check if update
        if (existing.isEmpty()) {
            podApi.create(podDef);
        } else {
            // only annotations are updated
            var metadata = new JsonObject();
            metadata.add("annotations", GsonPtr.to(podDef.getRaw())
                .to("metadata").get(JsonObject.class, "annotations").get());
            var patch = new JsonObject();
            patch.add("metadata", metadata);
            podApi.patch(existing.get().getMetadata().getNamespace(),
                existing.get().getMetadata().getName(),
                V1Patch.PATCH_FORMAT_JSON_MERGE_PATCH,
                new V1Patch(channel.client().getJSON().serialize(patch)))
                .throwsApiException();
        }
    }

}
