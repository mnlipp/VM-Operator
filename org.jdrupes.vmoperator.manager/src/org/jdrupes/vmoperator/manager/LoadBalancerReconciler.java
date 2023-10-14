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
import io.kubernetes.client.openapi.models.V1APIService;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import io.kubernetes.client.util.generic.dynamic.Dynamics;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import org.jdrupes.vmoperator.common.K8s;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
import org.jdrupes.vmoperator.util.GsonPtr;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Delegee for reconciling the service
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
/* default */ class LoadBalancerReconciler {

    private static final String LOAD_BALANCER_SERVICE = "loadBalancerService";
    private static final String METADATA
        = V1APIService.SERIALIZED_NAME_METADATA;
    private static final String LABELS = V1ObjectMeta.SERIALIZED_NAME_LABELS;
    private static final String ANNOTATIONS
        = V1ObjectMeta.SERIALIZED_NAME_ANNOTATIONS;
    protected final Logger logger = Logger.getLogger(getClass().getName());
    private final Configuration fmConfig;

    /**
     * Instantiates a new service reconciler.
     *
     * @param fmConfig the fm config
     */
    public LoadBalancerReconciler(Configuration fmConfig) {
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
        // Check if to be generated
        @SuppressWarnings({ "unchecked", "PMD.AvoidDuplicateLiterals" })
        var lbs = Optional.of(model)
            .map(m -> (Map<String, Object>) m.get("reconciler"))
            .map(c -> c.get(LOAD_BALANCER_SERVICE)).orElse(Boolean.FALSE);
        if (lbs instanceof Boolean isOn && !isOn) {
            return;
        }
        if (!(lbs instanceof Map)) {
            logger.warning(() -> "\"" + LOAD_BALANCER_SERVICE
                + "\" in configuration must be boolean or mapping but is "
                + lbs.getClass() + ".");
            return;
        }

        // Combine template and data and parse result
        var fmTemplate = fmConfig.getTemplate("runnerLoadBalancer.ftl.yaml");
        StringWriter out = new StringWriter();
        fmTemplate.process(model, out);
        // Avoid Yaml.load due to
        // https://github.com/kubernetes-client/java/issues/2741
        var svcDef = Dynamics.newFromYaml(
            new Yaml(new SafeConstructor(new LoaderOptions())), out.toString());
        mergeMetadata(svcDef, lbs, channel);

        // Apply
        DynamicKubernetesApi svcApi = new DynamicKubernetesApi("", "v1",
            "services", channel.client());
        K8s.apply(svcApi, svcDef, svcDef.getRaw().toString());
    }

    @SuppressWarnings("unchecked")
    private void mergeMetadata(DynamicKubernetesObject svcDef,
            Object lbsConfig, VmChannel channel) {
        // Get metadata from config
        Map<String, Object> asmData = Collections.emptyMap();
        if (lbsConfig instanceof Map config) {
            asmData = (Map<String, Object>) config;
        }
        var json = channel.client().getJSON();
        JsonObject cfgMeta
            = json.deserialize(json.serialize(asmData), JsonObject.class);

        // Get metadata from VM definition
        var vmMeta = GsonPtr.to(channel.vmDefinition()).to("spec")
            .get(JsonObject.class, LOAD_BALANCER_SERVICE)
            .map(JsonObject::deepCopy).orElseGet(() -> new JsonObject());

        // Merge Data from VM definition into config data
        mergeReplace(GsonPtr.to(cfgMeta).to(LABELS).get(JsonObject.class),
            GsonPtr.to(vmMeta).to(LABELS).get(JsonObject.class));
        mergeReplace(
            GsonPtr.to(cfgMeta).to(ANNOTATIONS).get(JsonObject.class),
            GsonPtr.to(vmMeta).to(ANNOTATIONS).get(JsonObject.class));

        // Merge additional data into service definition
        var svcMeta = GsonPtr.to(svcDef.getRaw()).to(METADATA);
        mergeIfAbsent(svcMeta.to(LABELS).get(JsonObject.class),
            GsonPtr.to(cfgMeta).to(LABELS).get(JsonObject.class));
        mergeIfAbsent(svcMeta.to(ANNOTATIONS).get(JsonObject.class),
            GsonPtr.to(cfgMeta).to(ANNOTATIONS).get(JsonObject.class));
    }

    private void mergeReplace(JsonObject dest, JsonObject src) {
        for (var e : src.entrySet()) {
            if (e.getValue().isJsonNull()) {
                dest.remove(e.getKey());
                continue;
            }
            dest.add(e.getKey(), e.getValue());
        }
    }

    private void mergeIfAbsent(JsonObject dest, JsonObject src) {
        for (var e : src.entrySet()) {
            if (dest.has(e.getKey())) {
                continue;
            }
            dest.add(e.getKey(), e.getValue());
        }
    }

}
