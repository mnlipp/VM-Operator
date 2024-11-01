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

import freemarker.template.TemplateException;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.util.generic.options.ListOptions;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.logging.Logger;
import org.jdrupes.vmoperator.common.DataPath;
import org.jdrupes.vmoperator.common.K8sV1SecretStub;
import static org.jdrupes.vmoperator.manager.Constants.APP_NAME;
import static org.jdrupes.vmoperator.manager.Constants.COMP_DISPLAY_SECRET;
import static org.jdrupes.vmoperator.manager.Constants.DATA_DISPLAY_PASSWORD;
import static org.jdrupes.vmoperator.manager.Constants.DATA_PASSWORD_EXPIRY;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
import org.jose4j.base64url.Base64;

/**
 * Delegee for reconciling the display secret
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
/* default */ class DisplaySecretReconciler {

    protected final Logger logger = Logger.getLogger(getClass().getName());

    /**
     * Reconcile.
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
        var display = DataPath
            .get(event.vmDefinition().spec(), "vm", "display").get();
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

}
