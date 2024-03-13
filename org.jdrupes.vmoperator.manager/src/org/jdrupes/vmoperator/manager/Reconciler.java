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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import freemarker.core.ParseException;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.SimpleNumber;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.TemplateHashModel;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateNotFoundException;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jdrupes.vmoperator.common.Convertions;
import org.jdrupes.vmoperator.common.K8sDynamicModel;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
import org.jdrupes.vmoperator.manager.events.VmDefChanged.Type;
import org.jdrupes.vmoperator.util.ExtendedObjectWrapper;
import org.jdrupes.vmoperator.util.GsonPtr;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.util.events.ConfigurationUpdate;

/**
 * Adapts Kubenetes resources for instances of the Runner 
 * application (the VMs) to changes in VM definitions (the CRs). 
 * 
 * In particular, the reconciler generates and updates:  
 * 
 * * A [`PVC`](https://kubernetes.io/docs/concepts/storage/persistent-volumes/)
 *   for storage used by all VMs as a common repository for CDROM images.
 * 
 * * A [`ConfigMap`](https://kubernetes.io/docs/concepts/configuration/configmap/)
 *   that defines the configuration file for the runner.
 *    
 * * A [`StatefulSet`](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/)
 *   that creates 
 *   * the [`Pod`](https://kubernetes.io/docs/concepts/workloads/pods/) 
 *     with the Runner instance, 
 *   * a PVC for 1 MiB of persistent storage used by the Runner 
 *     (referred to as the "runnerDataPvc") and
 *   * the PVCs for the VM's disks.
 *    
 * * (Optional) A load balancer
 *   [`Service`](https://kubernetes.io/docs/tasks/access-application-cluster/create-external-load-balancer/)
 *   that allows the user to access a VM's console without knowing which
 *   node it runs on.
 * 
 * The reconciler is part of the {@link Controller} component. It's 
 * configuration properties are therefore defined in
 * ```yaml
 * "/Manager":
 *   "/Controller":
 *     "/Reconciler":
 *       ...
 * ```
 *   
 * The reconciler supports the following configuration properties:
 * 
 * * `runnerDataPvc.storageClassName`: The storage class name
 *   to be used for the "runnerDataPvc" (the small volume used
 *   by the runner for information such as the EFI variables). By
 *   default, no `storageClassName` is generated, which causes
 *   Kubernetes to use storage from the default storage class.
 *   Define this if you want to use a specific storage class.
 *
 * * `cpuOvercommit`: The amount by which the current cpu count
 *   from the VM definition is divided when generating the 
 *   [`resources`](https://kubernetes.io/docs/reference/kubernetes-api/workload-resources/pod-v1/#resources)
 *   properties for the VM (defaults to 2).
 * 
 * * `ramOvercommit`: The amount by which the current ram size
 *   from the VM definition is divided when generating the
 *   [`resources`](https://kubernetes.io/docs/reference/kubernetes-api/workload-resources/pod-v1/#resources)
 *   properties for the VM (defaults to 1.25).
 *   
 * * `loadBalancerService`: If defined, causes a load balancer service 
 *   to be created. This property may be a boolean or
 *   YAML that defines additional labels or annotations to be merged
 *   into the service defintion. Here's an example for using
 *   [MetalLb](https://metallb.universe.tf/) as "internal load balancer":
 *   ```yaml 
 *   loadBalancerService:
 *     annotations:
 *       metallb.universe.tf/loadBalancerIPs: 192.168.168.1
 *       metallb.universe.tf/ip-allocated-from-pool: single-common
 *       metallb.universe.tf/allow-shared-ip: single-common
 *   ```
 *   This makes all VM consoles available at IP address 192.168.168.1
 *   with the port numbers from the VM definitions.
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis",
    "PMD.AvoidDuplicateLiterals" })
public class Reconciler extends Component {

    @SuppressWarnings("PMD.SingularField")
    private final Configuration fmConfig;
    private final ConfigMapReconciler cmReconciler;
    private final StatefulSetReconciler stsReconciler;
    private final LoadBalancerReconciler lbReconciler;
    @SuppressWarnings("PMD.UseConcurrentHashMap")
    private final Map<String, Object> config = new HashMap<>();

    /**
     * Instantiates a new reconciler.
     *
     * @param componentChannel the component channel
     */
    public Reconciler(Channel componentChannel) {
        super(componentChannel);

        // Configure freemarker library
        fmConfig = new Configuration(Configuration.VERSION_2_3_32);
        fmConfig.setDefaultEncoding("utf-8");
        fmConfig.setObjectWrapper(new ExtendedObjectWrapper(
            fmConfig.getIncompatibleImprovements()));
        fmConfig.setTemplateExceptionHandler(
            TemplateExceptionHandler.RETHROW_HANDLER);
        fmConfig.setLogTemplateExceptions(false);
        fmConfig.setClassForTemplateLoading(Reconciler.class, "");

        cmReconciler = new ConfigMapReconciler(fmConfig);
        stsReconciler = new StatefulSetReconciler(fmConfig);
        lbReconciler = new LoadBalancerReconciler(fmConfig);
    }

    /**
     * Configures the component.
     *
     * @param event the event
     */
    @Handler
    public void onConfigurationUpdate(ConfigurationUpdate event) {
        event.structured(componentPath()).ifPresent(c -> {
            config.putAll(c);
        });
    }

    /**
     * Handles the change event.
     *
     * @param event the event
     * @param channel the channel
     * @throws ApiException the api exception
     * @throws IOException 
     * @throws ParseException 
     * @throws MalformedTemplateNameException 
     * @throws TemplateNotFoundException 
     * @throws TemplateException 
     * @throws KubectlException 
     */
    @Handler
    @SuppressWarnings("PMD.ConfusingTernary")
    public void onVmDefChanged(VmDefChanged event, VmChannel channel)
            throws ApiException, TemplateException, IOException {
        // We're only interested in "spec" changes.
        if (!event.specChanged()) {
            return;
        }

        // Ownership relationships takes care of deletions
        var defMeta = event.vmDefinition().getMetadata();
        if (event.type() == Type.DELETED) {
            logger.fine(() -> "VM \"" + defMeta.getName() + "\" deleted");
            return;
        }

        // Reconcile, use "augmented" vm definition for model
        Map<String, Object> model = prepareModel(patchCr(event.vmDefinition()));
        var configMap = cmReconciler.reconcile(event, model, channel);
        model.put("cm", configMap.getRaw());
        stsReconciler.reconcile(event, model, channel);
        lbReconciler.reconcile(event, model, channel);
    }

    private DynamicKubernetesObject patchCr(K8sDynamicModel vmDef) {
        var json = vmDef.data().deepCopy();
        // Adjust cdromImage path
        adjustCdRomPaths(json);

        // Adjust cloud-init data
        adjustCloudInitData(json);

        return new DynamicKubernetesObject(json);
    }

    private void adjustCdRomPaths(JsonObject json) {
        var disks
            = GsonPtr.to(json).to("spec", "vm", "disks").get(JsonArray.class);
        for (var disk : disks) {
            var cdrom = (JsonObject) ((JsonObject) disk).get("cdrom");
            if (cdrom == null) {
                continue;
            }
            String image = cdrom.get("image").getAsString();
            if (image.isEmpty()) {
                continue;
            }
            try {
                @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
                var imageUri = new URI("file://" + Constants.IMAGE_REPO_PATH
                    + "/").resolve(image);
                if ("file".equals(imageUri.getScheme())) {
                    cdrom.addProperty("image", imageUri.getPath());
                } else {
                    cdrom.addProperty("image", imageUri.toString());
                }
            } catch (URISyntaxException e) {
                logger.warning(() -> "Invalid CDROM image: " + image);
            }
        }
    }

    private void adjustCloudInitData(JsonObject json) {
        var spec = GsonPtr.to(json).to("spec").get(JsonObject.class);
        if (!spec.has("cloudInit")) {
            return;
        }
        var metaData = GsonPtr.to(spec).to("cloudInit", "metaData");
        if (metaData.getAsString("instance-id").isEmpty()) {
            metaData.set("instance-id",
                GsonPtr.to(json).getAsString("metadata", "resourceVersion")
                    .map(s -> "v" + s).orElse("v1"));
        }
        if (metaData.getAsString("local-hostname").isEmpty()) {
            metaData.set("local-hostname",
                GsonPtr.to(json).getAsString("metadata", "name").get());
        }
    }

    @SuppressWarnings("PMD.CognitiveComplexity")
    private Map<String, Object> prepareModel(DynamicKubernetesObject vmDef)
            throws TemplateModelException {
        @SuppressWarnings("PMD.UseConcurrentHashMap")
        Map<String, Object> model = new HashMap<>();
        model.put("managerVersion",
            Optional.ofNullable(Reconciler.class.getPackage()
                .getImplementationVersion()).orElse("(Unknown)"));
        model.put("cr", vmDef.getRaw());
        model.put("constants",
            (TemplateHashModel) new DefaultObjectWrapperBuilder(
                Configuration.VERSION_2_3_32)
                    .build().getStaticModels()
                    .get(Constants.class.getName()));
        model.put("reconciler", config);

        // Methods
        model.put("parseQuantity", new TemplateMethodModelEx() {
            @Override
            @SuppressWarnings("PMD.PreserveStackTrace")
            public Object exec(@SuppressWarnings("rawtypes") List arguments)
                    throws TemplateModelException {
                var arg = arguments.get(0);
                if (arg instanceof Number number) {
                    return number;
                }
                try {
                    return Quantity.fromString(arg.toString()).getNumber();
                } catch (NumberFormatException e) {
                    throw new TemplateModelException("Cannot parse memory "
                        + "specified as \"" + arg + "\": " + e.getMessage());
                }
            }
        });
        model.put("formatMemory", new TemplateMethodModelEx() {
            @Override
            @SuppressWarnings("PMD.PreserveStackTrace")
            public Object exec(@SuppressWarnings("rawtypes") List arguments)
                    throws TemplateModelException {
                var arg = arguments.get(0);
                if (arg instanceof SimpleNumber number) {
                    arg = number.getAsNumber();
                }
                BigInteger bigInt;
                if (arg instanceof BigInteger value) {
                    bigInt = value;
                } else if (arg instanceof BigDecimal dec) {
                    try {
                        bigInt = dec.toBigIntegerExact();
                    } catch (ArithmeticException e) {
                        return arg;
                    }
                } else if (arg instanceof Integer value) {
                    bigInt = BigInteger.valueOf(value);
                } else if (arg instanceof Long value) {
                    bigInt = BigInteger.valueOf(value);
                } else {
                    return arg;
                }
                return Convertions.formatMemory(bigInt);
            }
        });
        return model;
    }
}
