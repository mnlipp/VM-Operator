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
import java.io.IOException;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.K8sObserver.ResponseType;
import org.jdrupes.vmoperator.common.K8sV1Secret;
import org.jdrupes.vmoperator.manager.events.DisplaySecretChanged;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jgrapes.core.Channel;

/**
 * Watches for changes of display secrets.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class DisplaySecretsMonitor
        extends AbstractMonitor<V1Secret, V1SecretList, VmChannel> {

    /**
     * Instantiates a new display secrets monitor.
     *
     * @param componentChannel the component channel
     */
    public DisplaySecretsMonitor(Channel componentChannel) {
        super(componentChannel, V1Secret.class, V1SecretList.class);
    }

    @Override
    protected void prepareMonitoring() throws IOException, ApiException {
        client(new K8sClient());
        context(K8sV1Secret.CONTEXT);
    }

    @Override
    protected void handleChange(K8sClient client, Response<V1Secret> change) {
        String vmName = change.object.getMetadata().getLabels()
            .get("app.kubernetes.io/instance");
        if (vmName == null) {
            return;
        }
        VmChannel channel = channel(vmName);
        if (channel == null || channel.vmDefinition() == null) {
            return;
        }
        channel.pipeline().fire(new DisplaySecretChanged(
            ResponseType.valueOf(change.type), change.object), channel);
    }

}
