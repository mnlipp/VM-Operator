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

import freemarker.core.ParseException;
import freemarker.template.Configuration;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.TemplateException;
import freemarker.template.TemplateNotFoundException;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.generic.dynamic.Dynamics;
import io.kubernetes.client.util.generic.options.ListOptions;
import io.kubernetes.client.util.generic.options.PatchOptions;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import static org.jdrupes.vmoperator.common.Constants.APP_NAME;
import org.jdrupes.vmoperator.common.Constants.Crd;
import org.jdrupes.vmoperator.common.K8sV1PvcStub;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
import org.jdrupes.vmoperator.util.DataPath;
import org.jdrupes.vmoperator.util.GsonPtr;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Delegee for reconciling the stateful set (effectively the pod).
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
/* default */ class PvcReconciler {

    protected final Logger logger = Logger.getLogger(getClass().getName());
    private final Configuration fmConfig;

    /**
     * Instantiates a new pvc reconciler.
     *
     * @param fmConfig the fm config
     */
    public PvcReconciler(Configuration fmConfig) {
        this.fmConfig = fmConfig;
    }

    /**
     * Reconcile the PVCs.
     *
     * @param event the event
     * @param model the model
     * @param channel the channel
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws TemplateException the template exception
     * @throws ApiException the api exception
     */
    @SuppressWarnings("PMD.AvoidDuplicateLiterals")
    public void reconcile(VmDefChanged event, Map<String, Object> model,
            VmChannel channel)
            throws IOException, TemplateException, ApiException {
        var vmDef = event.vmDefinition();

        // Existing disks
        ListOptions listOpts = new ListOptions();
        listOpts.setLabelSelector(
            "app.kubernetes.io/managed-by=" + Crd.NAME + ","
                + "app.kubernetes.io/name=" + APP_NAME + ","
                + "app.kubernetes.io/instance=" + vmDef.name());
        var knownDisks = K8sV1PvcStub.list(channel.client(),
            vmDef.namespace(), listOpts);
        var knownPvcs = knownDisks.stream().map(K8sV1PvcStub::name)
            .collect(Collectors.toSet());

        // Reconcile runner data pvc
        reconcileRunnerDataPvc(event, model, channel, knownPvcs);

        // Reconcile pvcs for defined disks
        var diskDefs = vmDef.<List<Map<String, Object>>> fromVm("disks")
            .orElse(List.of());
        var diskCounter = 0;
        for (var diskDef : diskDefs) {
            if (!diskDef.containsKey("volumeClaimTemplate")) {
                continue;
            }
            var diskName = DataPath.get(diskDef, "volumeClaimTemplate",
                "metadata", "name").map(name -> name + "-disk")
                .orElse("disk-" + diskCounter);
            diskCounter += 1;
            diskDef.put("generatedDiskName", diskName);

            // Don't do anything if pvc with old (sts generated) name exists.
            var stsDiskPvcName = diskName + "-" + vmDef.name() + "-0";
            if (knownPvcs.contains(stsDiskPvcName)) {
                diskDef.put("generatedPvcName", stsDiskPvcName);
                continue;
            }

            // Update PVC
            model.put("disk", diskDef);
            reconcileRunnerDiskPvc(event, model, channel);
        }
        model.remove("disk");
    }

    private void reconcileRunnerDataPvc(VmDefChanged event,
            Map<String, Object> model, VmChannel channel,
            Set<String> knownPvcs)
            throws TemplateNotFoundException, MalformedTemplateNameException,
            ParseException, IOException, TemplateException, ApiException {
        var vmDef = event.vmDefinition();

        // Look for old (sts generated) name.
        var stsRunnerDataPvcName
            = "runner-data" + "-" + vmDef.name() + "-0";
        if (knownPvcs.contains(stsRunnerDataPvcName)) {
            model.put("runnerDataPvcName", stsRunnerDataPvcName);
            return;
        }

        // Generate PVC
        model.put("runnerDataPvcName", vmDef.name() + "-runner-data");
        var fmTemplate = fmConfig.getTemplate("runnerDataPvc.ftl.yaml");
        StringWriter out = new StringWriter();
        fmTemplate.process(model, out);
        // Avoid Yaml.load due to
        // https://github.com/kubernetes-client/java/issues/2741
        var pvcDef = Dynamics.newFromYaml(
            new Yaml(new SafeConstructor(new LoaderOptions())), out.toString());

        // Do apply changes
        var pvcStub = K8sV1PvcStub.get(channel.client(),
            vmDef.namespace(), (String) model.get("runnerDataPvcName"));
        PatchOptions opts = new PatchOptions();
        opts.setForce(true);
        opts.setFieldManager("kubernetes-java-kubectl-apply");
        if (pvcStub.patch(V1Patch.PATCH_FORMAT_APPLY_YAML,
            new V1Patch(channel.client().getJSON().serialize(pvcDef)), opts)
            .isEmpty()) {
            logger.warning(
                () -> "Could not patch pvc for " + pvcStub.name());
        }
    }

    private void reconcileRunnerDiskPvc(VmDefChanged event,
            Map<String, Object> model, VmChannel channel)
            throws TemplateNotFoundException, MalformedTemplateNameException,
            ParseException, IOException, TemplateException, ApiException {
        var vmDef = event.vmDefinition();

        // Generate PVC
        @SuppressWarnings("unchecked")
        var diskDef = (Map<String, Object>) model.get("disk");
        var pvcName = vmDef.name() + "-" + diskDef.get("generatedDiskName");
        diskDef.put("generatedPvcName", pvcName);
        var fmTemplate = fmConfig.getTemplate("runnerDiskPvc.ftl.yaml");
        StringWriter out = new StringWriter();
        fmTemplate.process(model, out);
        // Avoid Yaml.load due to
        // https://github.com/kubernetes-client/java/issues/2741
        var pvcDef = Dynamics.newFromYaml(
            new Yaml(new SafeConstructor(new LoaderOptions())), out.toString());

        // Apply changes
        var pvcStub
            = K8sV1PvcStub.get(channel.client(), vmDef.namespace(), pvcName);
        var pvc = pvcStub.model();
        if (pvc.isEmpty()
            || !"Bound".equals(pvc.get().getStatus().getPhase())) {
            // Does not exist or isn't bound, use apply
            PatchOptions opts = new PatchOptions();
            opts.setForce(true);
            opts.setFieldManager("kubernetes-java-kubectl-apply");
            if (pvcStub.patch(V1Patch.PATCH_FORMAT_APPLY_YAML,
                new V1Patch(channel.client().getJSON().serialize(pvcDef)), opts)
                .isEmpty()) {
                logger.warning(
                    () -> "Could not patch pvc for " + pvcStub.name());
            }
            return;
        }

        // If bound, use json merge, omitting immutable fields
        var spec = GsonPtr.to(pvcDef.getRaw()).to("spec");
        spec.removeExcept("volumeAttributesClassName", "resources");
        spec.get("resources").ifPresent(p -> p.removeExcept("requests"));
        PatchOptions opts = new PatchOptions();
        opts.setFieldManager("kubernetes-java-kubectl-apply");
        if (pvcStub.patch(V1Patch.PATCH_FORMAT_JSON_MERGE_PATCH,
            new V1Patch(channel.client().getJSON().serialize(pvcDef)), opts)
            .isEmpty()) {
            logger.warning(
                () -> "Could not patch pvc for " + pvcStub.name());
        }
    }
}
