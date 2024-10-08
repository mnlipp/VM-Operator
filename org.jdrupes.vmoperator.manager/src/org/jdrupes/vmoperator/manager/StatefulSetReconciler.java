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
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.generic.dynamic.Dynamics;
import io.kubernetes.client.util.generic.options.PatchOptions;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.logging.Logger;
import org.jdrupes.vmoperator.common.K8sV1StatefulSetStub;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
import org.jdrupes.vmoperator.util.GsonPtr;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Before version 3.4, the pod running the VM was created by a stateful set.
 * Starting with version 3.4, this reconciler simply deletes the stateful
 * set, provided that the VM is not running.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
/* default */ class StatefulSetReconciler {

    protected final Logger logger = Logger.getLogger(getClass().getName());
    private final Configuration fmConfig;

    /**
     * Instantiates a new stateful set reconciler.
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
    @SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
    public void reconcile(VmDefChanged event, Map<String, Object> model,
            VmChannel channel)
            throws IOException, TemplateException, ApiException {
        var metadata = event.vmDefinition().getMetadata();
        model.put("usingSts", false);

        // If exists, delete when not running or supposed to be not running.
        var stsStub = K8sV1StatefulSetStub.get(channel.client(),
            metadata.getNamespace(), metadata.getName());
        if (stsStub.model().isEmpty()) {
            return;
        }

        // Stateful set still exists, check if replicas is 0 so we can
        // delete it.
        var stsModel = stsStub.model().get();
        if (stsModel.getSpec().getReplicas() == 0) {
            stsStub.delete();
            return;
        }

        // Cannot yet delete the stateful set.
        model.put("usingSts", true);

        // Check if VM is supposed to be stopped. If so,
        // set replicas to 0. This is the first step of the transition,
        // the stateful set will be deleted when the VM is restarted.
        var fmTemplate = fmConfig.getTemplate("runnerSts.ftl.yaml");
        StringWriter out = new StringWriter();
        fmTemplate.process(model, out);
        // Avoid Yaml.load due to
        // https://github.com/kubernetes-client/java/issues/2741
        var stsDef = Dynamics.newFromYaml(
            new Yaml(new SafeConstructor(new LoaderOptions())), out.toString());
        var desired = GsonPtr.to(stsDef.getRaw())
            .to("spec").getAsInt("replicas").orElse(1);
        if (desired == 1) {
            return;
        }

        // Do apply changes (set replicas to 0)
        PatchOptions opts = new PatchOptions();
        opts.setForce(true);
        opts.setFieldManager("kubernetes-java-kubectl-apply");
        if (stsStub.patch(V1Patch.PATCH_FORMAT_APPLY_YAML,
            new V1Patch(channel.client().getJSON().serialize(stsDef)), opts)
            .isEmpty()) {
            logger.warning(
                () -> "Could not patch stateful set for " + stsStub.name());
        }
    }

}
