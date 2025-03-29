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

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.Watch.Response;
import io.kubernetes.client.util.generic.options.ListOptions;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import static org.jdrupes.vmoperator.common.Constants.APP_NAME;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_NAME;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.K8sObserver.ResponseType;
import org.jdrupes.vmoperator.common.K8sV1PodStub;
import org.jdrupes.vmoperator.manager.events.ChannelDictionary;
import org.jdrupes.vmoperator.manager.events.PodChanged;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmResourceChanged;
import org.jgrapes.core.Channel;
import org.jgrapes.core.annotation.Handler;

/**
 * Watches for changes of pods that run VMs.
 */
public class PodMonitor extends AbstractMonitor<V1Pod, V1PodList, VmChannel> {

    private final ChannelDictionary<String, VmChannel, ?> channelDictionary;

    private final Map<String, PendingChange> pendingChanges
        = new ConcurrentHashMap<>();

    /**
     * Instantiates a new pod monitor.
     *
     * @param componentChannel the component channel
     * @param channelDictionary the channel dictionary
     */
    public PodMonitor(Channel componentChannel,
            ChannelDictionary<String, VmChannel, ?> channelDictionary) {
        super(componentChannel, V1Pod.class, V1PodList.class);
        this.channelDictionary = channelDictionary;
        context(K8sV1PodStub.CONTEXT);
        ListOptions options = new ListOptions();
        options.setLabelSelector("app.kubernetes.io/name=" + APP_NAME + ","
            + "app.kubernetes.io/component=" + APP_NAME + ","
            + "app.kubernetes.io/managed-by=" + VM_OP_NAME);
        options(options);
    }

    @Override
    protected void prepareMonitoring() throws IOException, ApiException {
        client(new K8sClient());
    }

    @Override
    protected void handleChange(K8sClient client, Response<V1Pod> change) {
        String vmName = change.object.getMetadata().getLabels()
            .get("app.kubernetes.io/instance");
        if (vmName == null) {
            return;
        }
        var channel = channelDictionary.channel(vmName).orElse(null);
        var responseType = ResponseType.valueOf(change.type);
        if (channel != null && channel.vmDefinition() != null) {
            pendingChanges.remove(vmName);
            channel.fire(new PodChanged(change.object, responseType));
            return;
        }

        // VM definition not available yet, may happen during startup
        if (responseType == ResponseType.DELETED) {
            return;
        }
        purgePendingChanges();
        logger.finer(() -> "Add pending pod change for " + vmName);
        pendingChanges.put(vmName, new PendingChange(Instant.now(), change));
    }

    private void purgePendingChanges() {
        Instant tooOld = Instant.now().minus(Duration.ofMinutes(15));
        for (var itr = pendingChanges.entrySet().iterator(); itr.hasNext();) {
            var change = itr.next();
            if (change.getValue().from().isBefore(tooOld)) {
                itr.remove();
                logger.finer(
                    () -> "Cleaned pending pod change for " + change.getKey());
            }
        }
    }

    /**
     * Check for pending changes.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler
    public void onVmResourceChanged(VmResourceChanged event,
            VmChannel channel) {
        Optional.ofNullable(pendingChanges.remove(event.vmDefinition().name()))
            .map(PendingChange::change).ifPresent(change -> {
                logger.finer(() -> "Firing pending pod change for "
                    + event.vmDefinition().name());
                channel.fire(new PodChanged(change.object,
                    ResponseType.valueOf(change.type)));
                if (logger.isLoggable(Level.FINER)
                    && pendingChanges.isEmpty()) {
                    logger.finer("No pending pod changes left.");
                }
            });
    }

    private record PendingChange(Instant from, Response<V1Pod> change) {
    }

}
