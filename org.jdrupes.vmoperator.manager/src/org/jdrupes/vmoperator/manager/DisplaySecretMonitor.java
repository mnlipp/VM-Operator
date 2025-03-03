/*
 * VM-Operator
 * Copyright (C) 2025 Michael N. Lipp
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

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.Watch.Response;
import io.kubernetes.client.util.generic.options.ListOptions;
import io.kubernetes.client.util.generic.options.PatchOptions;
import java.io.IOException;
import java.util.logging.Level;
import static org.jdrupes.vmoperator.common.Constants.APP_NAME;
import org.jdrupes.vmoperator.common.Constants.DisplaySecret;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_NAME;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.K8sV1PodStub;
import org.jdrupes.vmoperator.common.K8sV1SecretStub;
import org.jdrupes.vmoperator.manager.events.ChannelDictionary;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jgrapes.core.Channel;

/**
 * Watches for changes of display secrets. Updates an artifical attribute
 * of the pod running the VM in response to force an update of the files 
 * in the pod that reflect the information from the secret.
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis", "PMD.TooManyStaticImports" })
public class DisplaySecretMonitor
        extends AbstractMonitor<V1Secret, V1SecretList, VmChannel> {

    private final ChannelDictionary<String, VmChannel, ?> channelDictionary;

    /**
     * Instantiates a new display secrets monitor.
     *
     * @param componentChannel the component channel
     * @param channelDictionary the channel dictionary
     */
    public DisplaySecretMonitor(Channel componentChannel,
            ChannelDictionary<String, VmChannel, ?> channelDictionary) {
        super(componentChannel, V1Secret.class, V1SecretList.class);
        this.channelDictionary = channelDictionary;
        context(K8sV1SecretStub.CONTEXT);
        ListOptions options = new ListOptions();
        options.setLabelSelector("app.kubernetes.io/name=" + APP_NAME + ","
            + "app.kubernetes.io/component=" + DisplaySecret.NAME);
        options(options);
    }

    @Override
    protected void prepareMonitoring() throws IOException, ApiException {
        client(new K8sClient());
    }

    @Override
    protected void handleChange(K8sClient client, Response<V1Secret> change) {
        String vmName = change.object.getMetadata().getLabels()
            .get("app.kubernetes.io/instance");
        if (vmName == null) {
            return;
        }
        var channel = channelDictionary.channel(vmName).orElse(null);
        if (channel == null || channel.vmDefinition() == null) {
            return;
        }

        try {
            patchPod(client, change);
        } catch (ApiException e) {
            logger.log(Level.WARNING, e,
                () -> "Cannot patch pod annotations: " + e.getMessage());
        }
    }

    private void patchPod(K8sClient client, Response<V1Secret> change)
            throws ApiException {
        // Force update for pod
        ListOptions listOpts = new ListOptions();
        listOpts.setLabelSelector(
            "app.kubernetes.io/managed-by=" + VM_OP_NAME + ","
                + "app.kubernetes.io/name=" + APP_NAME + ","
                + "app.kubernetes.io/instance=" + change.object.getMetadata()
                    .getLabels().get("app.kubernetes.io/instance"));
        // Get pod, selected by label
        var pods = K8sV1PodStub.list(client, namespace(), listOpts);

        // If the VM is being created, the pod may not exist yet.
        if (pods.isEmpty()) {
            return;
        }
        var pod = pods.iterator().next();

        // Patch pod annotation
        PatchOptions patchOpts = new PatchOptions();
        patchOpts.setFieldManager("kubernetes-java-kubectl-apply");
        pod.patch(V1Patch.PATCH_FORMAT_JSON_PATCH,
            new V1Patch("[{\"op\": \"replace\", \"path\": "
                + "\"/metadata/annotations/vmrunner.jdrupes.org~1dpVersion\", "
                + "\"value\": \""
                + change.object.getMetadata().getResourceVersion()
                + "\"}]"),
            patchOpts);
    }
}
