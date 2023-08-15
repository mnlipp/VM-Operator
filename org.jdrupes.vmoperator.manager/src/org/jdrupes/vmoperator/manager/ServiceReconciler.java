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
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.Dynamics;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.logging.Logger;
import org.jdrupes.vmoperator.manager.VmDefChanged.Type;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Delegee for reconciling the service
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
/* default */ class ServiceReconciler {

    protected final Logger logger = Logger.getLogger(getClass().getName());
    private final Configuration fmConfig;

    /**
     * Instantiates a new service reconciler.
     *
     * @param fmConfig the fm config
     */
    public ServiceReconciler(Configuration fmConfig) {
        this.fmConfig = fmConfig;
    }

    /**
     * Reconcile.
     *
     * @param event the event
     * @param model the model
     * @param channel the channel
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws TemplateException the template exception
     * @throws ApiException the api exception
     */
    public void reconcile(VmDefChanged event,
            Map<String, Object> model, VmChannel channel)
            throws IOException, TemplateException, ApiException {
        // Get API and check if exists
        DynamicKubernetesApi svcApi = new DynamicKubernetesApi("", "v1",
            "services", channel.client());
        var existing = K8s.get(svcApi, event.object().getMetadata());

        // If deleted, delete
        if (event.type() == Type.DELETED) {
            if (existing.isPresent()) {
                K8s.delete(svcApi, existing.get());
            }
            return;
        }

        // Combine template and data and parse result
        var fmTemplate = fmConfig.getTemplate("runnerService.ftl.yaml");
        StringWriter out = new StringWriter();
        fmTemplate.process(model, out);
        // Avoid Yaml.load due to
        // https://github.com/kubernetes-client/java/issues/2741
        var mapDef = Dynamics.newFromYaml(
            new Yaml(new SafeConstructor(new LoaderOptions())), out.toString());

        // Apply
        K8s.apply(svcApi, mapDef, out.toString());
    }

}
