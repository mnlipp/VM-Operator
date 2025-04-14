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

import com.google.gson.JsonObject;
import io.kubernetes.client.apimachinery.GroupVersionKind;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.util.Watch;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jdrupes.vmoperator.common.Constants.Crd;
import org.jdrupes.vmoperator.common.Constants.Status;
import org.jdrupes.vmoperator.common.K8s;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.K8sDynamicModel;
import org.jdrupes.vmoperator.common.K8sDynamicModels;
import org.jdrupes.vmoperator.common.K8sDynamicStub;
import org.jdrupes.vmoperator.common.K8sObserver.ResponseType;
import org.jdrupes.vmoperator.common.VmDefinition.Assignment;
import org.jdrupes.vmoperator.common.VmDefinitionStub;
import org.jdrupes.vmoperator.common.VmPool;
import org.jdrupes.vmoperator.manager.events.GetPools;
import org.jdrupes.vmoperator.manager.events.VmPoolChanged;
import org.jdrupes.vmoperator.manager.events.VmResourceChanged;
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

    private final Map<String, VmPool> pools = new ConcurrentHashMap<>();
    private EventPipeline poolPipeline;

    /**
     * Instantiates a new VM pool manager.
     *
     * @param componentChannel the component channel
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
        var ctx = K8s.context(client(), Crd.GROUP, "", Crd.KIND_VM_POOL);
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
            Optional.ofNullable(pools.get(poolName)).ifPresent(pool -> {
                pool.setUndefined();
                if (pool.vms().isEmpty()) {
                    pools.remove(poolName);
                }
                poolPipeline.fire(new VmPoolChanged(pool, true));
            });
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

        // Get pool and merge changes
        var vmPool = pools.computeIfAbsent(poolName, k -> new VmPool(poolName));
        vmPool.defineFrom(JSON.getGson().fromJson(
            GsonPtr.to(poolModel.data()).to("spec").get(), VmPool.class));
        poolPipeline.fire(new VmPoolChanged(vmPool));
    }

    /**
     * Track VM definition changes.
     *
     * @param event the event
     * @throws ApiException 
     */
    @Handler
    public void onVmResourceChanged(VmResourceChanged event)
            throws ApiException {
        final var vmDef = event.vmDefinition();
        final String vmName = vmDef.name();
        switch (event.type()) {
        case ADDED:
            vmDef.<List<String>> fromSpec("pools")
                .orElse(Collections.emptyList()).stream().forEach(p -> {
                    pools.computeIfAbsent(p, k -> new VmPool(p))
                        .vms().add(vmName);
                    poolPipeline.fire(new VmPoolChanged(pools.get(p)));
                });
            break;
        case DELETED:
            pools.values().stream().forEach(p -> {
                if (p.vms().remove(vmName)) {
                    poolPipeline.fire(new VmPoolChanged(p));
                }
            });
            return;
        default:
            break;
        }

        // Sync last usage to console state change if user matches
        if (vmDef.assignment().map(Assignment::user)
            .map(at -> at.equals(vmDef.consoleUser().orElse(null)))
            .orElse(true)) {
            return;
        }

        var ccChange = vmDef.condition("ConsoleConnected")
            .map(cc -> cc.getLastTransitionTime().toInstant());
        if (ccChange
            .map(tt -> vmDef.assignment().map(Assignment::lastUsed)
                .map(alu -> alu.isAfter(tt)).orElse(true))
            .orElse(true)) {
            return;
        }
        var vmStub = VmDefinitionStub.get(client(),
            new GroupVersionKind(Crd.GROUP, "", Crd.KIND_VM),
            vmDef.namespace(), vmDef.name());
        vmStub.updateStatus(from -> {
            // TODO
            JsonObject status = from.statusJson();
            var assignment = GsonPtr.to(status).to(Status.ASSIGNMENT);
            assignment.set("lastUsed", ccChange.get().toString());
            return status;
        });
    }

    /**
     * Return the requested pools.
     *
     * @param event the event
     */
    @Handler
    public void onGetPools(GetPools event) {
        event.setResult(pools.values().stream().filter(VmPool::isDefined)
            .filter(p -> event.name().isEmpty()
                || p.name().equals(event.name().get()))
            .filter(p -> event.forUser().isEmpty() && event.forRoles().isEmpty()
                || !p.permissionsFor(event.forUser().orElse(null),
                    event.forRoles()).isEmpty())
            .toList());
    }
}
