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
import io.kubernetes.client.apimachinery.GroupVersionKind;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.generic.dynamic.Dynamics;
import io.kubernetes.client.util.generic.options.PatchOptions;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.logging.Logger;
import org.jdrupes.vmoperator.common.K8sObjectStub;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
import org.jdrupes.vmoperator.util.GsonPtr;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Delegee for reconciling the stateful set (effectively the pod).
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
/* default */ class StatefulSetReconciler {

    protected final Logger logger = Logger.getLogger(getClass().getName());
    private final Configuration fmConfig;

    /**
     * Instantiates a new config map reconciler.
     *
     * @param fmConfig the fm config
     */
    public StatefulSetReconciler(Configuration fmConfig) {
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
        var metadata = event.vmDefinition().getMetadata();

        // Combine template and data and parse result
        var fmTemplate = fmConfig.getTemplate("runnerSts.ftl.yaml");
        StringWriter out = new StringWriter();
        fmTemplate.process(model, out);
        // Avoid Yaml.load due to
        // https://github.com/kubernetes-client/java/issues/2741
        var stsDef = Dynamics.newFromYaml(
            new Yaml(new SafeConstructor(new LoaderOptions())), out.toString());

        // If exists apply changes only when transitioning state
        // or not running.
        var stsObj = K8sObjectStub.get(channel.client(),
            new GroupVersionKind("apps", "v1", "StatefulSet"),
            metadata.getNamespace(), metadata.getName());
        if (stsObj.isPresent()) {
            var current = GsonPtr.to(stsObj.get().state().getRaw())
                .to("spec").getAsInt("replicas").orElse(1);
            var desired = GsonPtr.to(stsDef.getRaw())
                .to("spec").getAsInt("replicas").orElse(1);
            if (current == 1 && desired == 1) {
                return;
            }

        }

        // Do apply changes
        PatchOptions opts = new PatchOptions();
        opts.setForce(true);
        opts.setFieldManager("kubernetes-java-kubectl-apply");
        stsObj.get().patch(V1Patch.PATCH_FORMAT_APPLY_YAML,
            new V1Patch(channel.client().getJSON().serialize(stsDef)),
            opts).throwsApiException();
    }

}
