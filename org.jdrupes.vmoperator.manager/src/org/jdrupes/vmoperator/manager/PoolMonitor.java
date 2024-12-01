/*
 * VM-Operator
 * Copyright (C) 2023,2024 Michael N. Lipp
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
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_GROUP;
import org.jdrupes.vmoperator.common.K8s;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.K8sDynamicModel;
import org.jdrupes.vmoperator.common.K8sDynamicModels;
import org.jdrupes.vmoperator.common.K8sDynamicStub;
import org.jdrupes.vmoperator.common.K8sObserver.ResponseType;
import org.jdrupes.vmoperator.common.VmPool;
import static org.jdrupes.vmoperator.manager.Constants.VM_OP_KIND_VM_POOL;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
import org.jdrupes.vmoperator.manager.events.VmPoolChanged;
import org.jdrupes.vmoperator.util.GsonPtr;
import org.jgrapes.core.Channel;
import org.jgrapes.core.EventPipeline;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.Attached;

/**
 * Watches for changes of VM pools. Reports the changes using 
 * {@link VmPoolChanged} events fired on a special pipeline to
 * avoid concurrent change informations.
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis", "PMD.ExcessiveImports" })
public class PoolMonitor extends
        AbstractMonitor<K8sDynamicModel, K8sDynamicModels, Channel> {

    private final ReentrantLock pendingLock = new ReentrantLock();
    private final Map<String, Set<String>> pending = new ConcurrentHashMap<>();
    private final Map<String, VmPool> pools = new ConcurrentHashMap<>();
    private EventPipeline poolPipeline;

    /**
     * Instantiates a new VM pool manager.
     *
     * @param componentChannel the component channel
     * @param channelManager the channel manager
     */
    public PoolMonitor(Channel componentChannel) {
        super(componentChannel, K8sDynamicModel.class,
            K8sDynamicModels.class);
    }

    /**
     * On attached.
     *
     * @param event the event
     */
    @Handler
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    public void onAttached(Attached event) {
        if (event.node() == this) {
            poolPipeline = newEventPipeline();
        }
    }

    @Override
    protected void prepareMonitoring() throws IOException, ApiException {
        client(new K8sClient());

        // Get all our API versions
        var ctx = K8s.context(client(), VM_OP_GROUP, "", VM_OP_KIND_VM_POOL);
        if (ctx.isEmpty()) {
            logger.severe(() -> "Cannot get CRD context.");
            return;
        }
        context(ctx.get());
    }

    @Override
    protected void handleChange(K8sClient client,
            Watch.Response<K8sDynamicModel> response) {

        var type = ResponseType.valueOf(response.type);
        var poolName = response.object.metadata().getName();

        // When pool is deleted, save VMs in pending
        if (type == ResponseType.DELETED) {
            try {
                pendingLock.lock();
                Optional.ofNullable(pools.get(poolName)).ifPresent(
                    p -> {
                        pending.computeIfAbsent(poolName, k -> Collections
                            .synchronizedSet(new HashSet<>())).addAll(p.vms());
                        pools.remove(poolName);
                        poolPipeline.fire(new VmPoolChanged(p, true));
                    });
            } finally {
                pendingLock.unlock();
            }
            return;
        }

        // Get full definition
        var poolModel = response.object;
        if (poolModel.data() == null) {
            // ADDED event does not provide data, see
            // https://github.com/kubernetes-client/java/issues/3215
            try {
                poolModel = K8sDynamicStub.get(client(), context(), namespace(),
                    poolModel.metadata().getName()).model().orElse(null);
            } catch (ApiException e) {
                return;
            }
        }

        // Convert to VM pool
        var vmPool = client().getJSON().getGson().fromJson(
            GsonPtr.to(poolModel.data()).to("spec").get(),
            VmPool.class);
        V1ObjectMeta metadata = response.object.getMetadata();
        vmPool.setName(metadata.getName());

        // If modified, merge changes and notify
        if (type == ResponseType.MODIFIED && pools.containsKey(poolName)) {
            pools.get(poolName).setPermissions(vmPool.permissions());
            poolPipeline.fire(new VmPoolChanged(vmPool));
            return;
        }

        // Add new pool
        try {
            pendingLock.lock();
            Optional.ofNullable(pending.get(poolName)).ifPresent(s -> {
                vmPool.vms().addAll(s);
            });
            pending.remove(poolName);
            pools.put(poolName, vmPool);
            poolPipeline.fire(new VmPoolChanged(vmPool));
        } finally {
            pendingLock.unlock();
        }
    }

    /**
     * Track VM definition changes.
     *
     * @param event the event
     */
    @Handler
    public void onVmDefChanged(VmDefChanged event) {
        String vmName = event.vmDefinition().name();
        switch (event.type()) {
        case ADDED:
            try {
                pendingLock.lock();
                event.vmDefinition().<List<String>> fromSpec("pools")
                    .orElse(Collections.emptyList()).stream().forEach(p -> {
                        if (pools.containsKey(p)) {
                            pools.get(p).vms().add(vmName);
                        } else {
                            pending.computeIfAbsent(p, k -> Collections
                                .synchronizedSet(new HashSet<>())).add(vmName);
                        }
                        poolPipeline.fire(new VmPoolChanged(pools.get(p)));
                    });
            } finally {
                pendingLock.unlock();
            }
            break;
        case DELETED:
            try {
                pendingLock.lock();
                pools.values().stream().forEach(p -> {
                    if (p.vms().remove(vmName)) {
                        poolPipeline.fire(new VmPoolChanged(p));
                    }
                });
                // Should not be necessary, but just in case
                pending.values().stream().forEach(s -> s.remove(vmName));
            } finally {
                pendingLock.unlock();
            }
            break;
        default:
            break;
        }
    }
}
