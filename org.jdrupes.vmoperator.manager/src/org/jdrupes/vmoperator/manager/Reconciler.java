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
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.TemplateHashModel;
import freemarker.template.TemplateNotFoundException;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_GROUP;
import org.jdrupes.vmoperator.manager.VmDefChanged.Type;
import org.jdrupes.vmoperator.util.ExtendedObjectWrapper;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;

/**
 * Adapts Kubenetes resources to changes in VM definitions (CRs). 
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis",
    "PMD.AvoidDuplicateLiterals" })
public class Reconciler extends Component {

    @SuppressWarnings("PMD.SingularField")
    private final Configuration fmConfig;
    private final CmReconciler cmReconciler;
    private final StsReconciler stsReconciler;

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

        cmReconciler = new CmReconciler(fmConfig);
        stsReconciler = new StsReconciler(fmConfig);
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
        // Get complete VM (CR) definition
        var apiVersion = K8s.version(event.object().getApiVersion());
        DynamicKubernetesApi vmCrApi = new DynamicKubernetesApi(VM_OP_GROUP,
            apiVersion, event.crd().getName(), channel.client());
        var defMeta = event.object().getMetadata();

        // Update state
        if (event.type() != Type.DELETED) {
            channel.setVmDefinition(
                patchCr(K8s.get(vmCrApi, defMeta).get().getRaw().deepCopy()));
        }

        // Get common data for all reconciles
        JsonObject vmDef = channel.vmDefinition();
        @SuppressWarnings("PMD.UseConcurrentHashMap")
        Map<String, Object> model = new HashMap<>();
        model.put("cr", vmDef);
        model.put("constants",
            (TemplateHashModel) new DefaultObjectWrapperBuilder(
                Configuration.VERSION_2_3_32)
                    .build().getStaticModels()
                    .get(Constants.class.getName()));

        // Reconcile
        if (event.type() != Type.DELETED) {
            var configMap = cmReconciler.reconcile(event, model, channel);
            model.put("cm", configMap.getRaw());
            stsReconciler.reconcile(event, model, channel);
        } else {
            stsReconciler.reconcile(event, model, channel);
            cmReconciler.reconcile(event, model, channel);
        }
    }

    private JsonObject patchCr(JsonObject vmDef) {
        // Adjust cdromImage path
        var disks
            = GsonPtr.to(vmDef).to("spec", "vm", "disks").get(JsonArray.class);
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
        return vmDef;
    }

}
