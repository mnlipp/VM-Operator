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
import static org.jdrupes.vmoperator.common.Constants.VM_OP_NAME;
import org.jdrupes.vmoperator.common.K8sV1PvcStub;
import org.jdrupes.vmoperator.common.VmDefinition;
import org.jdrupes.vmoperator.manager.events.VmChannel;
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
     * @param vmDef the VM definition
     * @param model the model
     * @param channel the channel
     * @param specChanged the spec changed
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws TemplateException the template exception
     * @throws ApiException the api exception
     */
    @SuppressWarnings({ "PMD.AvoidDuplicateLiterals", "unchecked" })
    public void reconcile(VmDefinition vmDef, Map<String, Object> model,
            VmChannel channel, boolean specChanged)
            throws IOException, TemplateException, ApiException {
        Set<String> knownPvcs;
        if (!specChanged && channel.associated(this, Set.class).isPresent()) {
            knownPvcs = (Set<String>) channel.associated(this, Set.class).get();
        } else {
            ListOptions listOpts = new ListOptions();
            listOpts.setLabelSelector(
                "app.kubernetes.io/managed-by=" + VM_OP_NAME + ","
                    + "app.kubernetes.io/name=" + APP_NAME + ","
                    + "app.kubernetes.io/instance=" + vmDef.name());
            knownPvcs = K8sV1PvcStub.list(channel.client(),
                vmDef.namespace(), listOpts).stream().map(K8sV1PvcStub::name)
                .collect(Collectors.toSet());
            channel.setAssociated(this, knownPvcs);
        }

        // Reconcile runner data pvc
        reconcileRunnerDataPvc(vmDef, model, channel, knownPvcs, specChanged);

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
            reconcileRunnerDiskPvc(vmDef, model, channel, specChanged, diskDef);
        }
    }

    private void reconcileRunnerDataPvc(VmDefinition vmDef,
            Map<String, Object> model, VmChannel channel,
            Set<String> knownPvcs, boolean specChanged)
            throws TemplateNotFoundException, MalformedTemplateNameException,
            ParseException, IOException, TemplateException, ApiException {

        // Look for old (sts generated) name.
        var stsRunnerDataPvcName
            = "runner-data" + "-" + vmDef.name() + "-0";
        if (knownPvcs.contains(stsRunnerDataPvcName)) {
            model.put("runnerDataPvcName", stsRunnerDataPvcName);
            return;
        }

        // Generate PVC
        var runnerDataPvcName = vmDef.name() + "-runner-data";
        logger.fine(() -> "Create/update pvc " + runnerDataPvcName);
        model.put("runnerDataPvcName", runnerDataPvcName);
        if (!specChanged) {
            // Augmenting the model is all we have to do
            return;
        }
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

    private void reconcileRunnerDiskPvc(VmDefinition vmDef,
            Map<String, Object> model, VmChannel channel, boolean specChanged,
            Map<String, Object> diskDef)
            throws TemplateNotFoundException, MalformedTemplateNameException,
            ParseException, IOException, TemplateException, ApiException {
        // Generate PVC
        var pvcName = vmDef.name() + "-" + diskDef.get("generatedDiskName");
        diskDef.put("generatedPvcName", pvcName);
        if (!specChanged) {
            // Augmenting the model is all we have to do
            return;
        }

        // Generate PVC
        logger.fine(() -> "Create/update pvc " + pvcName);
        model.put("disk", diskDef);
        var fmTemplate = fmConfig.getTemplate("runnerDiskPvc.ftl.yaml");
        StringWriter out = new StringWriter();
        fmTemplate.process(model, out);
        model.remove("disk");
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
