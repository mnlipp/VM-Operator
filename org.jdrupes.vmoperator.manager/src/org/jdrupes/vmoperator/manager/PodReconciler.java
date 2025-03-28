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
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.generic.dynamic.Dynamics;
import io.kubernetes.client.util.generic.options.ListOptions;
import io.kubernetes.client.util.generic.options.PatchOptions;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.logging.Logger;
import static org.jdrupes.vmoperator.common.Constants.APP_NAME;
import org.jdrupes.vmoperator.common.Constants.DisplaySecret;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.K8sV1PodStub;
import org.jdrupes.vmoperator.common.K8sV1SecretStub;
import org.jdrupes.vmoperator.common.VmDefinition;
import org.jdrupes.vmoperator.common.VmDefinition.RequestedVmState;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Delegee for reconciling the pod.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
/* default */ class PodReconciler {

    protected final Logger logger = Logger.getLogger(getClass().getName());
    private final Configuration fmConfig;

    /**
     * Instantiates a new pod reconciler.
     *
     * @param fmConfig the fm config
     */
    public PodReconciler(Configuration fmConfig) {
        this.fmConfig = fmConfig;
    }

    /**
     * Reconcile the pod.
     *
     * @param vmDef the vm def
     * @param model the model
     * @param channel the channel
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws TemplateException the template exception
     * @throws ApiException the api exception
     */
    public void reconcile(VmDefinition vmDef, Map<String, Object> model,
            VmChannel channel)
            throws IOException, TemplateException, ApiException {
        // Don't do anything if stateful set is still in use (pre v3.4)
        if ((Boolean) model.get("usingSts")) {
            return;
        }

        // Get pod stub.
        var podStub = K8sV1PodStub.get(channel.client(), vmDef.namespace(),
            vmDef.name());

        // Nothing to do if exists and should be running
        if (vmDef.vmState() == RequestedVmState.RUNNING
            && podStub.model().isPresent()) {
            return;
        }

        // Delete if running but should be stopped
        if (vmDef.vmState() == RequestedVmState.STOPPED) {
            if (podStub.model().isPresent()) {
                podStub.delete();
            }
            return;
        }

        // Create pod. First combine template and data and parse result
        logger.fine(() -> "Create/update pod " + podStub.name());
        addDisplaySecret(channel.client(), model, vmDef);
        var fmTemplate = fmConfig.getTemplate("runnerPod.ftl.yaml");
        StringWriter out = new StringWriter();
        fmTemplate.process(model, out);
        // Avoid Yaml.load due to
        // https://github.com/kubernetes-client/java/issues/2741
        var podDef = Dynamics.newFromYaml(
            new Yaml(new SafeConstructor(new LoaderOptions())), out.toString());

        // Do apply changes
        PatchOptions opts = new PatchOptions();
        opts.setForce(true);
        opts.setFieldManager("kubernetes-java-kubectl-apply");
        if (podStub.apply(podDef).isEmpty()) {
            logger.warning(
                () -> "Could not patch pod for " + podStub.name());
        }
    }

    private void addDisplaySecret(K8sClient client, Map<String, Object> model,
            VmDefinition vmDef) throws ApiException {
        ListOptions options = new ListOptions();
        options.setLabelSelector("app.kubernetes.io/name=" + APP_NAME + ","
            + "app.kubernetes.io/component=" + DisplaySecret.NAME + ","
            + "app.kubernetes.io/instance=" + vmDef.name());
        var dsStub = K8sV1SecretStub
            .list(client, vmDef.namespace(), options).stream().findFirst();
        if (dsStub.isPresent()) {
            dsStub.get().model().ifPresent(m -> {
                model.put("displaySecret", m.getMetadata().getName());
            });
        }
    }

}
