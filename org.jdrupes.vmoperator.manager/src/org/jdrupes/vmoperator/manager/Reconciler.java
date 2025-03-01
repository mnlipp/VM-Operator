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
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.SimpleNumber;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.TemplateHashModel;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.utility.DeepUnwrap;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.generic.options.ListOptions;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.jdrupes.vmoperator.common.Constants.APP_NAME;
import org.jdrupes.vmoperator.common.Constants.DisplaySecret;
import org.jdrupes.vmoperator.common.Convertions;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.K8sObserver;
import org.jdrupes.vmoperator.common.K8sV1SecretStub;
import org.jdrupes.vmoperator.common.VmDefinition;
import org.jdrupes.vmoperator.manager.events.ResetVm;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
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
    private final StatefulSetReconciler stsReconciler;
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
        stsReconciler = new StatefulSetReconciler(fmConfig);
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
    public void onVmDefChanged(VmDefChanged event, VmChannel channel)
            throws ApiException, TemplateException, IOException {
        // We're only interested in "spec" changes.
        if (!event.specChanged()) {
            return;
        }

        // Ownership relationships takes care of deletions
        if (event.type() == K8sObserver.ResponseType.DELETED) {
            logger.fine(
                () -> "VM \"" + event.vmDefinition().name() + "\" deleted");
            return;
        }

        // Create model for processing templates
        Map<String, Object> model
            = prepareModel(channel.client(), event.vmDefinition());
        var configMap = cmReconciler.reconcile(model, channel);
        model.put("cm", configMap);
        dsReconciler.reconcile(event, model, channel);
        // Manage (eventual) removal of stateful set.
        stsReconciler.reconcile(event, model, channel);
        pvcReconciler.reconcile(event, model, channel);
        podReconciler.reconcile(event, model, channel);
        lbReconciler.reconcile(event, model, channel);
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
        vmDef.extra().ifPresent(e -> e.resetCount(e.resetCount() + 1));
        Map<String, Object> model
            = prepareModel(channel.client(), channel.vmDefinition());
        cmReconciler.reconcile(model, channel);
    }

    @SuppressWarnings({ "PMD.CognitiveComplexity", "PMD.NPathComplexity" })
    private Map<String, Object> prepareModel(K8sClient client,
            VmDefinition vmDef) throws TemplateModelException, ApiException {
        @SuppressWarnings("PMD.UseConcurrentHashMap")
        Map<String, Object> model = new HashMap<>();
        model.put("managerVersion",
            Optional.ofNullable(Reconciler.class.getPackage()
                .getImplementationVersion()).orElse("(Unknown)"));
        model.put("cr", vmDef);
        model.put("constants",
            (TemplateHashModel) new DefaultObjectWrapperBuilder(
                Configuration.VERSION_2_3_32)
                    .build().getStaticModels()
                    .get(Constants.class.getName()));
        model.put("reconciler", config);

        // Check if we have a display secret
        ListOptions options = new ListOptions();
        options.setLabelSelector("app.kubernetes.io/name=" + APP_NAME + ","
            + "app.kubernetes.io/component=" + DisplaySecret.NAME + ","
            + "app.kubernetes.io/instance=" + vmDef.name());
        var dsStub = K8sV1SecretStub
            .list(client, vmDef.namespace(), options)
            .stream()
            .findFirst();
        if (dsStub.isPresent()) {
            dsStub.get().model().ifPresent(m -> {
                model.put("displaySecret", m.getMetadata().getName());
            });
        }

        // Methods
        model.put("parseQuantity", parseQuantityModel);
        model.put("formatMemory", formatMemoryModel);
        model.put("imageLocation", imgageLocationModel);
        model.put("adjustCloudInitMeta", adjustCloudInitMetaModel);
        model.put("toJson", toJsonModel);
        return model;
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

    private final TemplateMethodModelEx adjustCloudInitMetaModel
        = new TemplateMethodModelEx() {
            @Override
            @SuppressWarnings("PMD.PreserveStackTrace")
            public Object exec(@SuppressWarnings("rawtypes") List arguments)
                    throws TemplateModelException {
                @SuppressWarnings("unchecked")
                var res = new HashMap<>((Map<String, Object>) DeepUnwrap
                    .unwrap((TemplateModel) arguments.get(0)));
                var metadata
                    = (V1ObjectMeta) ((AdapterTemplateModel) arguments.get(1))
                        .getAdaptedObject(Object.class);
                if (!res.containsKey("instance-id")) {
                    res.put("instance-id",
                        Optional.ofNullable(metadata.getResourceVersion())
                            .map(s -> "v" + s).orElse("v1"));
                }
                if (!res.containsKey("local-hostname")) {
                    res.put("local-hostname", metadata.getName());
                }
                return res;
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
