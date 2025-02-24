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
import static java.nio.charset.StandardCharsets.UTF_8;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.logging.Logger;
import static org.jdrupes.vmoperator.common.Constants.APP_NAME;
import static org.jdrupes.vmoperator.common.Constants.COMP_DISPLAY_SECRET;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_GROUP;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_KIND_VM;
import org.jdrupes.vmoperator.common.K8sV1SecretStub;
import org.jdrupes.vmoperator.common.VmDefinitionStub;
import static org.jdrupes.vmoperator.manager.Constants.DATA_DISPLAY_LOGIN;
import static org.jdrupes.vmoperator.manager.Constants.DATA_DISPLAY_PASSWORD;
import static org.jdrupes.vmoperator.manager.Constants.DATA_DISPLAY_USER;
import static org.jdrupes.vmoperator.manager.Constants.DATA_PASSWORD_EXPIRY;
import org.jdrupes.vmoperator.manager.events.PrepareConsole;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
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
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis", "PMD.TooManyStaticImports" })
public class DisplaySecretReconciler extends Component {

    protected final Logger logger = Logger.getLogger(getClass().getName());
    private int passwordValidity = 10;
    private final List<PendingPrepare> pendingPrepares
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
                    if (c.containsKey("passwordValidity")) {
                        passwordValidity = Integer
                            .parseInt((String) c.get("passwordValidity"));
                    }
                } catch (ClassCastException e) {
                    logger.config("Malformed configuration: " + e.getMessage());
                }
            });
    }

    /**
     * Reconcile. If the configuration prevents generating a secret
     * or the secret already exists, do nothing. Else generate a new
     * secret with a random password and immediate expiration, thus
     * preventing access to the display.
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
        // Secret needed at all?
        var display = event.vmDefinition().fromVm("display").get();
        if (!DataPath.<Boolean> get(display, "spice", "generateSecret")
            .orElse(true)) {
            return;
        }

        // Check if exists
        var vmDef = event.vmDefinition();
        ListOptions options = new ListOptions();
        options.setLabelSelector("app.kubernetes.io/name=" + APP_NAME + ","
            + "app.kubernetes.io/component=" + COMP_DISPLAY_SECRET + ","
            + "app.kubernetes.io/instance=" + vmDef.name());
        var stubs = K8sV1SecretStub.list(channel.client(), vmDef.namespace(),
            options);
        if (!stubs.isEmpty()) {
            return;
        }

        // Create secret
        var secret = new V1Secret();
        secret.setMetadata(new V1ObjectMeta().namespace(vmDef.namespace())
            .name(vmDef.name() + "-" + COMP_DISPLAY_SECRET)
            .putLabelsItem("app.kubernetes.io/name", APP_NAME)
            .putLabelsItem("app.kubernetes.io/component", COMP_DISPLAY_SECRET)
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
        secret.setStringData(Map.of(DATA_DISPLAY_PASSWORD, password,
            DATA_PASSWORD_EXPIRY, "now"));
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
    @SuppressWarnings("PMD.StringInstantiation")
    public void onPrepareConsole(PrepareConsole event, VmChannel channel)
            throws ApiException {
        // Update console user in status
        var vmStub = VmDefinitionStub.get(channel.client(),
            new GroupVersionKind(VM_OP_GROUP, "", VM_OP_KIND_VM),
            event.vmDefinition().namespace(), event.vmDefinition().name());
        var optVmDef = vmStub.updateStatus(from -> {
            JsonObject status = from.statusJson();
            status.addProperty("consoleUser", event.user());
            return status;
        });
        if (optVmDef.isEmpty()) {
            return;
        }
        var vmDef = optVmDef.get();

        // Check if access is possible
        if (event.loginUser()
            ? !vmDef.conditionStatus("Booted").orElse(false)
            : !vmDef.conditionStatus("Running").orElse(false)) {
            return;
        }

        // Look for secret
        ListOptions options = new ListOptions();
        options.setLabelSelector("app.kubernetes.io/name=" + APP_NAME + ","
            + "app.kubernetes.io/component=" + COMP_DISPLAY_SECRET + ","
            + "app.kubernetes.io/instance=" + vmDef.name());
        var stubs = K8sV1SecretStub.list(channel.client(), vmDef.namespace(),
            options);
        if (stubs.isEmpty()) {
            // No secret means no password for this VM wanted
            event.setResult(null);
            return;
        }
        var stub = stubs.iterator().next();

        // Get secret and update
        var secret = stub.model().get();
        var updPw = updatePassword(secret, event);
        var updUsr = updateUser(secret, event);
        if (!updPw && !updUsr) {
            return;
        }

        // Register wait for confirmation (by VM status change)
        var pending = new PendingPrepare(event,
            event.vmDefinition().displayPasswordSerial().orElse(0L) + 1,
            new CompletionLock(event, 1500));
        pendingPrepares.add(pending);
        Event.onCompletion(event, e -> {
            pendingPrepares.remove(pending);
        });

        // Update, will (eventually) trigger confirmation
        stub.update(secret).getObject();
    }

    private boolean updateUser(V1Secret secret, PrepareConsole event) {
        var curUser = DataPath.<byte[]> get(secret, "data", DATA_DISPLAY_USER)
            .map(b -> new String(b, UTF_8)).orElse(null);
        var curLogin = DataPath.<byte[]> get(secret, "data", DATA_DISPLAY_LOGIN)
            .map(b -> new String(b, UTF_8)).map(Boolean::parseBoolean)
            .orElse(null);
        if (Objects.equals(curUser, event.user()) && Objects.equals(
            curLogin, event.loginUser())) {
            return false;
        }
        secret.getData().put(DATA_DISPLAY_USER, event.user().getBytes(UTF_8));
        secret.getData().put(DATA_DISPLAY_LOGIN,
            Boolean.toString(event.loginUser()).getBytes(UTF_8));
        return true;
    }

    private boolean updatePassword(V1Secret secret, PrepareConsole event) {
        var expiry = Optional.ofNullable(secret.getData()
            .get(DATA_PASSWORD_EXPIRY)).map(b -> new String(b)).orElse(null);
        if (secret.getData().get(DATA_DISPLAY_PASSWORD) != null
            && stillValid(expiry)) {
            // Fixed secret, don't touch
            event.setResult(
                new String(secret.getData().get(DATA_DISPLAY_PASSWORD)));
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
        secret.setStringData(Map.of(DATA_DISPLAY_PASSWORD, password,
            DATA_PASSWORD_EXPIRY,
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
    public void onVmDefChanged(VmDefChanged event, Channel channel) {
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
    @SuppressWarnings("PMD.DataClass")
    private static class PendingPrepare {
        public final PrepareConsole event;
        public final long expectedSerial;
        public final CompletionLock lock;

        /**
         * Instantiates a new pending get.
         *
         * @param event the event
         * @param expectedSerial the expected serial
         */
        public PendingPrepare(PrepareConsole event, long expectedSerial,
                CompletionLock lock) {
            super();
            this.event = event;
            this.expectedSerial = expectedSerial;
            this.lock = lock;
        }
    }
}
