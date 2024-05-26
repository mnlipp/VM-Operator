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

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.Watch.Response;
import io.kubernetes.client.util.generic.options.ListOptions;
import java.io.IOException;
import static org.jdrupes.vmoperator.common.Constants.APP_NAME;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.K8sObserver.ResponseType;
import org.jdrupes.vmoperator.common.K8sV1SecretStub;
import static org.jdrupes.vmoperator.manager.Constants.COMP_DISPLAY_SECRET;
import org.jdrupes.vmoperator.manager.events.DisplayPasswordChanged;
import org.jdrupes.vmoperator.manager.events.GetDisplayPassword;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jgrapes.core.Channel;
import org.jgrapes.core.annotation.Handler;

/**
 * Watches for changes of display secrets.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class DisplayPasswordMonitor
        extends AbstractMonitor<V1Secret, V1SecretList, VmChannel> {

    /**
     * Instantiates a new display secrets monitor.
     *
     * @param componentChannel the component channel
     */
    public DisplayPasswordMonitor(Channel componentChannel) {
        super(componentChannel, V1Secret.class, V1SecretList.class);
        context(K8sV1SecretStub.CONTEXT);
        ListOptions options = new ListOptions();
        options.setLabelSelector("app.kubernetes.io/name=" + APP_NAME + ","
            + "app.kubernetes.io/component=" + COMP_DISPLAY_SECRET);
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
        var channel = channel(vmName).orElse(null);
        if (channel == null || channel.vmDefinition() == null) {
            return;
        }
        channel.pipeline().fire(new DisplayPasswordChanged(
            ResponseType.valueOf(change.type), change.object), channel);
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
        ListOptions options = new ListOptions();
        options.setLabelSelector("app.kubernetes.io/name=" + APP_NAME + ","
            + "app.kubernetes.io/component=" + COMP_DISPLAY_SECRET + ","
            + "app.kubernetes.io/instance=" + event.vmName());
        var stubs = K8sV1SecretStub.list(client(), namespace(), options);
        if (stubs.isEmpty()) {
            return;
        }
        stubs.iterator().next().model().map(m -> m.getData())
            .map(m -> m.get("display-password"))
            .ifPresent(p -> event.setResult(new String(p)));
    }
}
