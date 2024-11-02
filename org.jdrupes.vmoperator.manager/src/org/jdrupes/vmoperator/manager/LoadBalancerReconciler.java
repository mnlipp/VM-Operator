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

import com.google.gson.Gson;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1APIService;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import io.kubernetes.client.util.generic.dynamic.Dynamics;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import org.jdrupes.vmoperator.common.K8sV1ServiceStub;
import org.jdrupes.vmoperator.common.VmDefinition;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
import org.jdrupes.vmoperator.util.DataPath;
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
        @SuppressWarnings({ "PMD.AvoidDuplicateLiterals", "unchecked" })
        var lbsDef = Optional.of(model)
            .map(m -> (Map<String, Object>) m.get("reconciler"))
            .map(c -> c.get(LOAD_BALANCER_SERVICE)).orElse(Boolean.FALSE);
        if (!(lbsDef instanceof Map) && !(lbsDef instanceof Boolean)) {
            logger.warning(() -> "\"" + LOAD_BALANCER_SERVICE
                + "\" in configuration must be boolean or mapping but is "
                + lbsDef.getClass() + ".");
            return;
        }
        if (lbsDef instanceof Boolean isOn && !isOn) {
            return;
        }

        // Load balancer can also be turned off for VM
        var vmDef = event.vmDefinition();
        if (DataPath.<Map<String, Map<String, String>>> get(vmDef, "spec",
            LOAD_BALANCER_SERVICE).map(m -> m.isEmpty()).orElse(false)) {
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
        @SuppressWarnings("unchecked")
        var defaults = lbsDef instanceof Map
            ? (Map<String, Map<String, String>>) lbsDef
            : null;
        var client = channel.client();
        mergeMetadata(client.getJSON().getGson(), svcDef, defaults, vmDef);

        // Apply
        var svcStub = K8sV1ServiceStub
            .get(client, vmDef.namespace(), vmDef.name());
        if (svcStub.apply(svcDef).isEmpty()) {
            logger.warning(
                () -> "Could not patch service for " + svcStub.name());
        }
    }

    private void mergeMetadata(Gson gson, DynamicKubernetesObject svcDef,
            Map<String, Map<String, String>> defaults,
            VmDefinition vmDefinition) {
        // Get specific load balancer metadata from VM definition
        var vmLbMeta = DataPath
            .<Map<String, Map<String, String>>> get(vmDefinition, "spec",
                LOAD_BALANCER_SERVICE)
            .orElse(Collections.emptyMap());

        // Merge
        var svcMeta = svcDef.getMetadata();
        var svcJsonMeta = GsonPtr.to(svcDef.getRaw()).to(METADATA);
        Optional.ofNullable(mergeIfAbsent(svcMeta.getLabels(),
            mergeReplace(defaults.get(LABELS), vmLbMeta.get(LABELS))))
            .ifPresent(lbls -> svcJsonMeta.set(LABELS, gson.toJsonTree(lbls)));
        Optional.ofNullable(mergeIfAbsent(svcMeta.getAnnotations(),
            mergeReplace(defaults.get(ANNOTATIONS), vmLbMeta.get(ANNOTATIONS))))
            .ifPresent(as -> svcJsonMeta.set(ANNOTATIONS, gson.toJsonTree(as)));
    }

    private Map<String, String> mergeReplace(Map<String, String> dest,
            Map<String, String> src) {
        if (src == null) {
            return dest;
        }
        if (dest == null) {
            dest = new LinkedHashMap<>();
        } else {
            dest = new LinkedHashMap<>(dest);
        }
        for (var e : src.entrySet()) {
            if (e.getValue() == null) {
                dest.remove(e.getKey());
                continue;
            }
            dest.put(e.getKey(), e.getValue());
        }
        return dest;
    }

    private Map<String, String> mergeIfAbsent(Map<String, String> dest,
            Map<String, String> src) {
        if (src == null) {
            return dest;
        }
        if (dest == null) {
            dest = new LinkedHashMap<>();
        } else {
            dest = new LinkedHashMap<>(dest);
        }
        for (var e : src.entrySet()) {
            if (dest.containsKey(e.getKey())) {
                continue;
            }
            dest.put(e.getKey(), e.getValue());
        }
        return dest;
    }

}
