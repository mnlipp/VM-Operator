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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.AdapterTemplateModel;
import freemarker.template.Configuration;
import freemarker.template.SimpleNumber;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import org.jdrupes.vmoperator.common.Convertions;
import org.jdrupes.vmoperator.common.K8sObserver;
import org.jdrupes.vmoperator.common.VmDefinition;
import org.jdrupes.vmoperator.common.VmDefinition.Assignment;
import org.jdrupes.vmoperator.common.VmPool;
import org.jdrupes.vmoperator.manager.events.GetPools;
import org.jdrupes.vmoperator.manager.events.ResetVm;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmResourceChanged;
import org.jdrupes.vmoperator.util.ExtendedObjectWrapper;
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
 * * A [`PVC`](https://kubernetes.io/docs/concepts/storage/persistent-volumes/)
 *   for 1 MiB of persistent storage used by the Runner (referred to as the
 *   "runnerDataPvc")
 *   
 * * The PVCs for the VM's disks.
 *
 * * A [`Pod`](https://kubernetes.io/docs/concepts/workloads/pods/) with the
 *   runner instance[^oldSts].
 *
 * * (Optional) A load balancer
 *   [`Service`](https://kubernetes.io/docs/tasks/access-application-cluster/create-external-load-balancer/)
 *   that allows the user to access a VM's console without knowing which
 *   node it runs on.
 * 
 * [^oldSts]: Before version 3.4, the operator created a
 *    [`StatefulSet`](https://kubernetes.io/docs/concepts/workloads/controllers/statefulset/)
 *    that created the pod.
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
 *   
 * * `loggingProperties`: If defined, specifies the default logging
 *   properties to be used by the runners managed by the controller.
 *   This property is a string that holds the content of
 *   a logging.properties file.
 *   
 * @see org.jdrupes.vmoperator.manager.DisplaySecretReconciler
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis",
    "PMD.AvoidDuplicateLiterals" })
public class Reconciler extends Component {

    /** The Constant mapper. */
    @SuppressWarnings("PMD.FieldNamingConventions")
    protected static final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("PMD.SingularField")
    private final Configuration fmConfig;
    private final ConfigMapReconciler cmReconciler;
    private final DisplaySecretReconciler dsReconciler;
    private final PvcReconciler pvcReconciler;
    private final PodReconciler podReconciler;
    private final LoadBalancerReconciler lbReconciler;
    @SuppressWarnings("PMD.UseConcurrentHashMap")
    private final Map<String, Object> config = new HashMap<>();

    /**
     * Instantiates a new reconciler.
     *
     * @param componentChannel the component channel
     */
    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
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
        dsReconciler = attach(new DisplaySecretReconciler(componentChannel));
        pvcReconciler = new PvcReconciler(fmConfig);
        podReconciler = new PodReconciler(fmConfig);
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
     * @throws TemplateException the template exception
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @Handler
    @SuppressWarnings("PMD.ConfusingTernary")
    public void onVmResourceChanged(VmResourceChanged event, VmChannel channel)
            throws ApiException, TemplateException, IOException {
        // Ownership relationships takes care of deletions
        if (event.type() == K8sObserver.ResponseType.DELETED) {
            return;
        }

        // Create model for processing templates
        var vmDef = event.vmDefinition();
        Map<String, Object> model = prepareModel(vmDef);
        cmReconciler.reconcile(model, channel, event.specChanged());

        // The remaining reconcilers depend only on changes of the spec part
        // or the pod state.
        if (!event.specChanged() && !event.podChanged()) {
            return;
        }
        dsReconciler.reconcile(vmDef, model, channel, event.specChanged());
        pvcReconciler.reconcile(vmDef, model, channel, event.specChanged());
        podReconciler.reconcile(vmDef, model, channel, event.specChanged());
        lbReconciler.reconcile(vmDef, model, channel, event.specChanged());
    }

    /**
     * Reset the VM by incrementing the reset count and doing a 
     * partial reconcile (configmap only).
     *
     * @param event the event
     * @param channel the channel
     * @throws IOException 
     * @throws ApiException 
     * @throws TemplateException 
     */
    @Handler
    public void onResetVm(ResetVm event, VmChannel channel)
            throws ApiException, IOException, TemplateException {
        var vmDef = channel.vmDefinition();
        var extra = vmDef.extra();
        extra.resetCount(extra.resetCount() + 1);
        Map<String, Object> model
            = prepareModel(channel.vmDefinition());
        cmReconciler.reconcile(model, channel, true);
    }

    private Map<String, Object> prepareModel(VmDefinition vmDef)
            throws TemplateModelException, ApiException {
        @SuppressWarnings("PMD.UseConcurrentHashMap")
        Map<String, Object> model = new HashMap<>();
        model.put("managerVersion",
            Optional.ofNullable(Reconciler.class.getPackage()
                .getImplementationVersion()).orElse("(Unknown)"));
        model.put("cr", vmDef);
        model.put("reconciler", config);
        model.put("constants", constantsMap(Constants.class));
        addLoginRequestedFor(model, vmDef);

        // Methods
        model.put("parseQuantity", parseQuantityModel);
        model.put("formatMemory", formatMemoryModel);
        model.put("imageLocation", imgageLocationModel);
        model.put("toJson", toJsonModel);
        return model;
    }

    /**
     * Creates a map with constants. Needed because freemarker doesn't support
     * nested classes with its static models.
     *
     * @param clazz the clazz
     * @return the map
     */
    @SuppressWarnings("PMD.EmptyCatchBlock")
    private Map<String, Object> constantsMap(Class<?> clazz) {
        @SuppressWarnings("PMD.UseConcurrentHashMap")
        Map<String, Object> result = new HashMap<>();
        Arrays.stream(clazz.getFields()).filter(f -> {
            var modifiers = f.getModifiers();
            return Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers)
                && f.getType() == String.class;
        }).forEach(f -> {
            try {
                result.put(f.getName(), f.get(null));
            } catch (IllegalArgumentException | IllegalAccessException e) {
                // Should not happen, ignore
            }
        });
        Arrays.stream(clazz.getClasses()).filter(c -> {
            var modifiers = c.getModifiers();
            return Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers);
        }).forEach(c -> {
            result.put(c.getSimpleName(), constantsMap(c));
        });
        return result;
    }

    private void addLoginRequestedFor(Map<String, Object> model,
            VmDefinition vmDef) {
        vmDef.assignment().filter(a -> {
            try {
                return newEventPipeline()
                    .fire(new GetPools().withName(a.pool())).get()
                    .stream().findFirst().map(VmPool::loginOnAssignment)
                    .orElse(false);
            } catch (InterruptedException e) {
                logger.log(Level.WARNING, e, e::getMessage);
            }
            return false;
        }).map(Assignment::user)
            .or(() -> vmDef.fromSpec("vm", "display", "loggedInUser"))
            .ifPresent(u -> model.put("loginRequestedFor", u));
    }

    private final TemplateMethodModelEx parseQuantityModel
        = new TemplateMethodModelEx() {
            @Override
            @SuppressWarnings("PMD.PreserveStackTrace")
            public Object exec(@SuppressWarnings("rawtypes") List arguments)
                    throws TemplateModelException {
                var arg = arguments.get(0);
                if (arg instanceof SimpleNumber number) {
                    return number.getAsNumber();
                }
                try {
                    return Quantity.fromString(arg.toString()).getNumber();
                } catch (NumberFormatException e) {
                    throw new TemplateModelException("Cannot parse memory "
                        + "specified as \"" + arg + "\": " + e.getMessage());
                }
            }
        };

    private final TemplateMethodModelEx formatMemoryModel
        = new TemplateMethodModelEx() {
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
        };

    private final TemplateMethodModelEx imgageLocationModel
        = new TemplateMethodModelEx() {
            @Override
            @SuppressWarnings({ "PMD.PreserveStackTrace",
                "PMD.AvoidLiteralsInIfCondition" })
            public Object exec(@SuppressWarnings("rawtypes") List arguments)
                    throws TemplateModelException {
                var image = ((SimpleScalar) arguments.get(0)).getAsString();
                if (image.isEmpty()) {
                    return "";
                }
                try {
                    var imageUri
                        = new URI("file://" + Constants.IMAGE_REPO_PATH + "/")
                            .resolve(image);
                    if ("file".equals(imageUri.getScheme())) {
                        return imageUri.getPath();
                    }
                    return imageUri.toString();
                } catch (URISyntaxException e) {
                    logger.warning(() -> "Invalid CDROM image: " + image);
                }
                return image;
            }
        };

    private final TemplateMethodModelEx toJsonModel
        = new TemplateMethodModelEx() {
            @Override
            @SuppressWarnings("PMD.PreserveStackTrace")
            public Object exec(@SuppressWarnings("rawtypes") List arguments)
                    throws TemplateModelException {
                try {
                    return mapper.writeValueAsString(
                        ((AdapterTemplateModel) arguments.get(0))
                            .getAdaptedObject(Object.class));
                } catch (JsonProcessingException e) {
                    return "{}";
                }
            }
        };
}
