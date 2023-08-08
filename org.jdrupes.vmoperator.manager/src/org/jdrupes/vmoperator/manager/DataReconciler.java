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
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.Dynamics;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

/**
 * Delegee for reconciling the data PVC
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
/* default */ class DataReconciler {

    private final Configuration fmConfig;

    /**
     * Instantiates a new config map reconciler.
     *
     * @param fmConfig the fm config
     */
    public DataReconciler(Configuration fmConfig) {
        this.fmConfig = fmConfig;
    }

    /**
     * Reconcile.
     *
     * @param model the model
     * @param channel the channel
     * @throws TemplateException the template exception
     * @throws ApiException the api exception
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @SuppressWarnings("PMD.ConfusingTernary")
    public void reconcile(Map<String, Object> model, VmChannel channel)
            throws TemplateException, ApiException, IOException {
        // Combine template and data and parse result
        var fmTemplate = fmConfig.getTemplate("runnerDataPvc.ftl.yaml");
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

}
