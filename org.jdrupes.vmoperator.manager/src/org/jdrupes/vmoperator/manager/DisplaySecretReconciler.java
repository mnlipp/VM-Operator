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

import com.google.gson.JsonObject;
import freemarker.template.TemplateException;
import io.kubernetes.client.apimachinery.GroupVersionKind;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.util.generic.options.ListOptions;
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
import java.util.logging.Logger;
import static org.jdrupes.vmoperator.common.Constants.APP_NAME;
import org.jdrupes.vmoperator.common.Constants.Crd;
import org.jdrupes.vmoperator.common.Constants.DisplaySecret;
import org.jdrupes.vmoperator.common.Constants.Status;
import org.jdrupes.vmoperator.common.K8sV1SecretStub;
import org.jdrupes.vmoperator.common.VmDefinition;
import org.jdrupes.vmoperator.common.VmDefinitionStub;
import org.jdrupes.vmoperator.manager.events.GetDisplaySecret;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmResourceChanged;
import org.jdrupes.vmoperator.util.DataPath;
import org.jgrapes.core.Channel;
import org.jgrapes.core.CompletionLock;
import org.jgrapes.core.Component;
import org.jgrapes.core.Event;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.util.events.ConfigurationUpdate;
import org.jose4j.base64url.Base64;

/**
 * The properties of the display secret do not only depend on the
 * VM definition, but also on events that occur during runtime.
 * The reconciler for the display secret is therefore a separate
 * component.
 * 
 * The reconciler supports the following configuration properties:
 * 
 *   * `passwordValidity`: the validity of the random password in seconds.
 *     Used to calculate the password expiry time in the generated secret.
 */
public class DisplaySecretReconciler extends Component {

    protected final Logger logger = Logger.getLogger(getClass().getName());
    private int passwordValidity = 10;
    private final List<PendingRequest> pendingPrepares
        = Collections.synchronizedList(new LinkedList<>());

    /**
     * Instantiates a new display secret reconciler.
     *
     * @param componentChannel the component channel
     */
    public DisplaySecretReconciler(Channel componentChannel) {
        super(componentChannel);
    }

    /**
     * On configuration update.
     *
     * @param event the event
     */
    @Handler
    public void onConfigurationUpdate(ConfigurationUpdate event) {
        event.structured(componentPath())
            // for backward compatibility
            .or(() -> {
                var oldConfig = event
                    .structured("/Manager/Controller/DisplaySecretMonitor");
                if (oldConfig.isPresent()) {
                    logger.warning(() -> "Using configuration with old "
                        + "path '/Manager/Controller/DisplaySecretMonitor' "
                        + "for `passwordValidity`, please update "
                        + "the configuration.");
                }
                return oldConfig;
            }).ifPresent(c -> {
                try {
                    Optional.ofNullable(c.get("passwordValidity"))
                        .map(p -> p instanceof Integer ? (Integer) p
                            : Integer.valueOf((String) p))
                        .ifPresent(p -> {
                            passwordValidity = p;
                        });
                } catch (NumberFormatException e) {
                    logger.warning(
                        () -> "Malformed configuration: " + e.getMessage());
                }
            });
    }

    /**
     * Reconcile. If the configuration prevents generating a secret
     * or the secret already exists, do nothing. Else generate a new
     * secret with a random password and immediate expiration, thus
     * preventing access to the display.
     *
     * @param vmDef the VM definition
     * @param model the model
     * @param channel the channel
     * @param specChanged the spec changed
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws TemplateException the template exception
     * @throws ApiException the api exception
     */
    public void reconcile(VmDefinition vmDef, Map<String, Object> model,
            VmChannel channel, boolean specChanged)
            throws IOException, TemplateException, ApiException {
        // Nothing to do unless spec changed
        if (!specChanged) {
            return;
        }

        // Secret needed at all?
        var display = vmDef.fromVm("display").get();
        if (!DataPath.<Boolean> get(display, "spice", "generateSecret")
            .orElse(true)) {
            return;
        }

        // Check if exists
        ListOptions options = new ListOptions();
        options.setLabelSelector("app.kubernetes.io/name=" + APP_NAME + ","
            + "app.kubernetes.io/component=" + DisplaySecret.NAME + ","
            + "app.kubernetes.io/instance=" + vmDef.name());
        var stubs = K8sV1SecretStub.list(channel.client(), vmDef.namespace(),
            options);
        if (!stubs.isEmpty()) {
            return;
        }

        // Create secret
        var secretName = vmDef.name() + "-" + DisplaySecret.NAME;
        logger.fine(() -> "Create/update secret " + secretName);
        var secret = new V1Secret();
        secret.setMetadata(new V1ObjectMeta().namespace(vmDef.namespace())
            .name(secretName)
            .putLabelsItem("app.kubernetes.io/name", APP_NAME)
            .putLabelsItem("app.kubernetes.io/component", DisplaySecret.NAME)
            .putLabelsItem("app.kubernetes.io/instance", vmDef.name()));
        secret.setType("Opaque");
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
        secret.setStringData(Map.of(DisplaySecret.PASSWORD, password,
            DisplaySecret.EXPIRY, "now"));
        K8sV1SecretStub.create(channel.client(), secret);
    }

    /**
     * Prepares access to the console for the user from the event.
     * Generates a new password and sends it to the runner.
     * Requests the VM (via the runner) to login the user if specified
     * in the event.
     *
     * @param event the event
     * @param channel the channel
     * @throws ApiException the api exception
     */
    @Handler
    public void onGetDisplaySecret(GetDisplaySecret event, VmChannel channel)
            throws ApiException {
        // Get VM definition and check if running
        var vmStub = VmDefinitionStub.get(channel.client(),
            new GroupVersionKind(Crd.GROUP, "", Crd.KIND_VM),
            event.vmDefinition().namespace(), event.vmDefinition().name());
        var vmDef = vmStub.model().orElse(null);
        if (vmDef == null || !vmDef.conditionStatus("Running").orElse(false)) {
            return;
        }

        // Update console user in status
        vmDef = vmStub.updateStatus(from -> {
            JsonObject status = from.statusJson();
            status.addProperty(Status.CONSOLE_USER, event.user());
            return status;
        }).get();

        // Get secret and update password in secret
        var stub = getSecretStub(event, channel, vmDef);
        if (stub == null) {
            return;
        }
        var secret = stub.model().get();
        if (!updatePassword(secret, event)) {
            return;
        }

        // Register wait for confirmation (by VM status change,
        // after secret update)
        var pending = new PendingRequest(event,
            event.vmDefinition().displayPasswordSerial().orElse(0L) + 1,
            new CompletionLock(event, 1500));
        pendingPrepares.add(pending);
        Event.onCompletion(event, e -> {
            pendingPrepares.remove(pending);
        });

        // Update, will (eventually) trigger confirmation
        stub.update(secret).getObject();
    }

    private K8sV1SecretStub getSecretStub(GetDisplaySecret event,
            VmChannel channel, VmDefinition vmDef) throws ApiException {
        // Look for secret
        ListOptions options = new ListOptions();
        options.setLabelSelector("app.kubernetes.io/name=" + APP_NAME + ","
            + "app.kubernetes.io/component=" + DisplaySecret.NAME + ","
            + "app.kubernetes.io/instance=" + vmDef.name());
        var stubs = K8sV1SecretStub.list(channel.client(), vmDef.namespace(),
            options);
        if (stubs.isEmpty()) {
            // No secret means no password for this VM wanted
            event.setResult(null);
            return null;
        }
        return stubs.iterator().next();
    }

    private boolean updatePassword(V1Secret secret, GetDisplaySecret event) {
        var expiry = Optional.ofNullable(secret.getData()
            .get(DisplaySecret.EXPIRY)).map(b -> new String(b)).orElse(null);
        if (secret.getData().get(DisplaySecret.PASSWORD) != null
            && stillValid(expiry)) {
            // Fixed secret, don't touch
            event.setResult(
                new String(secret.getData().get(DisplaySecret.PASSWORD)));
            return false;
        }

        // Generate password and set expiry
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
        secret.setStringData(Map.of(DisplaySecret.PASSWORD, password,
            DisplaySecret.EXPIRY,
            Long.toString(Instant.now().getEpochSecond() + passwordValidity)));
        event.setResult(password);
        return true;
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
    public void onVmResourceChanged(VmResourceChanged event, Channel channel) {
        synchronized (pendingPrepares) {
            String vmName = event.vmDefinition().name();
            for (var pending : pendingPrepares) {
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
    private static class PendingRequest {
        public final GetDisplaySecret event;
        public final long expectedSerial;
        public final CompletionLock lock;

        /**
         * Instantiates a new pending get.
         *
         * @param event the event
         * @param expectedSerial the expected serial
         */
        public PendingRequest(GetDisplaySecret event, long expectedSerial,
                CompletionLock lock) {
            super();
            this.event = event;
            this.expectedSerial = expectedSerial;
            this.lock = lock;
        }
    }
}
