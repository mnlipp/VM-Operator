/*
 * VM-Operator
 * Copyright (C) 2024 Michael N. Lipp
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
import io.kubernetes.client.apimachinery.GroupVersionKind;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.Watch.Response;
import io.kubernetes.client.util.generic.options.ListOptions;
import io.kubernetes.client.util.generic.options.PatchOptions;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.logging.Level;
import static org.jdrupes.vmoperator.common.Constants.APP_NAME;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_GROUP;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_KIND_VM;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_NAME;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.K8sV1PodStub;
import org.jdrupes.vmoperator.common.K8sV1SecretStub;
import org.jdrupes.vmoperator.common.VmDefinitionStub;
import static org.jdrupes.vmoperator.manager.Constants.COMP_DISPLAY_SECRET;
import static org.jdrupes.vmoperator.manager.Constants.DATA_DISPLAY_PASSWORD;
import static org.jdrupes.vmoperator.manager.Constants.DATA_PASSWORD_EXPIRY;
import org.jdrupes.vmoperator.manager.events.ChannelDictionary;
import org.jdrupes.vmoperator.manager.events.GetDisplayPassword;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
import org.jgrapes.core.Channel;
import org.jgrapes.core.CompletionLock;
import org.jgrapes.core.Event;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.util.events.ConfigurationUpdate;
import org.jose4j.base64url.Base64;

/**
 * Watches for changes of display secrets. The component supports the
 * following configuration properties:
 * 
 *   * `passwordValidity`: the validity of the random password in seconds.
 *     Used to calculate the password expiry time in the generated secret.
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis", "PMD.TooManyStaticImports" })
public class DisplaySecretMonitor
        extends AbstractMonitor<V1Secret, V1SecretList, VmChannel> {

    private int passwordValidity = 10;
    private final List<PendingGet> pendingGets
        = Collections.synchronizedList(new LinkedList<>());
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
            + "app.kubernetes.io/component=" + COMP_DISPLAY_SECRET);
        options(options);
    }

    /**
     * On configuration update.
     *
     * @param event the event
     */
    @Handler
    @Override
    public void onConfigurationUpdate(ConfigurationUpdate event) {
        super.onConfigurationUpdate(event);
        event.structured(componentPath()).ifPresent(c -> {
            try {
                if (c.containsKey("passwordValidity")) {
                    passwordValidity = Integer
                        .parseInt((String) c.get("passwordValidity"));
                }
            } catch (ClassCastException e) {
                logger.config("Malformed configuration: " + e.getMessage());
            }
        });
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

    /**
     * On get display secrets.
     *
     * @param event the event
     * @param channel the channel
     * @throws ApiException the api exception
     */
    @Handler
    @SuppressWarnings("PMD.StringInstantiation")
    public void onGetDisplaySecrets(GetDisplayPassword event, VmChannel channel)
            throws ApiException {
        // Update console user in status
        var vmStub = VmDefinitionStub.get(client(),
            new GroupVersionKind(VM_OP_GROUP, "", VM_OP_KIND_VM),
            event.vmDefinition().namespace(), event.vmDefinition().name());
        vmStub.updateStatus(from -> {
            JsonObject status = from.statusJson();
            status.addProperty("consoleUser", event.user());
            return status;
        });

        // Look for secret
        ListOptions options = new ListOptions();
        options.setLabelSelector("app.kubernetes.io/name=" + APP_NAME + ","
            + "app.kubernetes.io/component=" + COMP_DISPLAY_SECRET + ","
            + "app.kubernetes.io/instance="
            + event.vmDefinition().metadata().getName());
        var stubs = K8sV1SecretStub.list(client(),
            event.vmDefinition().namespace(), options);
        if (stubs.isEmpty()) {
            // No secret means no password for this VM wanted
            return;
        }
        var stub = stubs.iterator().next();

        // Check validity
        var model = stub.model().get();
        @SuppressWarnings("PMD.StringInstantiation")
        var expiry = Optional.ofNullable(model.getData()
            .get(DATA_PASSWORD_EXPIRY)).map(b -> new String(b)).orElse(null);
        if (model.getData().get(DATA_DISPLAY_PASSWORD) != null
            && stillValid(expiry)) {
            // Fixed secret, don't touch
            event.setResult(
                new String(model.getData().get(DATA_DISPLAY_PASSWORD)));
            return;
        }
        updatePassword(stub, event);
    }

    @SuppressWarnings("PMD.StringInstantiation")
    private void updatePassword(K8sV1SecretStub stub, GetDisplayPassword event)
            throws ApiException {
        SecureRandom random = null;
        try {
            random = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) { // NOPMD
            // "Every implementation of the Java platform is required
            // to support at least one strong SecureRandom implementation."
        }
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        var password = Base64.encode(bytes);
        var model = stub.model().get();
        model.setStringData(Map.of(DATA_DISPLAY_PASSWORD, password,
            DATA_PASSWORD_EXPIRY,
            Long.toString(Instant.now().getEpochSecond() + passwordValidity)));
        event.setResult(password);

        // Prepare wait for confirmation (by VM status change)
        var pending = new PendingGet(event,
            event.vmDefinition().displayPasswordSerial().orElse(0L) + 1,
            new CompletionLock(event, 1500));
        pendingGets.add(pending);
        Event.onCompletion(event, e -> {
            pendingGets.remove(pending);
        });

        // Update, will (eventually) trigger confirmation
        stub.update(model).getObject();
    }

    private boolean stillValid(String expiry) {
        if (expiry == null || "never".equals(expiry)) {
            return true;
        }
        @SuppressWarnings({ "PMD.CloseResource", "resource" })
        var scanner = new Scanner(expiry);
        if (!scanner.hasNextLong()) {
            return false;
        }
        long expTime = scanner.nextLong();
        return expTime > Instant.now().getEpochSecond() + passwordValidity;
    }

    /**
     * On vm def changed.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler
    @SuppressWarnings("PMD.AvoidSynchronizedStatement")
    public void onVmDefChanged(VmDefChanged event, Channel channel) {
        synchronized (pendingGets) {
            String vmName = event.vmDefinition().name();
            for (var pending : pendingGets) {
                if (pending.event.vmDefinition().name().equals(vmName)
                    && event.vmDefinition().displayPasswordSerial()
                        .map(s -> s >= pending.expectedSerial).orElse(false)) {
                    pending.lock.remove();
                    // pending will be removed from pendingGest by
                    // waiting thread, see updatePassword
                    continue;
                }
            }
        }
    }

    /**
     * The Class PendingGet.
     */
    @SuppressWarnings("PMD.DataClass")
    private static class PendingGet {
        public final GetDisplayPassword event;
        public final long expectedSerial;
        public final CompletionLock lock;

        /**
         * Instantiates a new pending get.
         *
         * @param event the event
         * @param expectedSerial the expected serial
         */
        public PendingGet(GetDisplayPassword event, long expectedSerial,
                CompletionLock lock) {
            super();
            this.event = event;
            this.expectedSerial = expectedSerial;
            this.lock = lock;
        }
    }
}
