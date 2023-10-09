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

package org.jdrupes.vmoperator.runner.qemu;

import com.google.gson.JsonObject;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.custom.Quantity.Format;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.ApisApi;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1APIGroup;
import io.kubernetes.client.openapi.models.V1GroupVersionForDiscovery;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import org.jdrupes.vmoperator.runner.qemu.events.BalloonChangeEvent;
import org.jdrupes.vmoperator.runner.qemu.events.HotpluggableCpuStatus;
import org.jdrupes.vmoperator.runner.qemu.events.RunnerConfigurationUpdate;
import org.jdrupes.vmoperator.runner.qemu.events.RunnerStateChange;
import org.jdrupes.vmoperator.runner.qemu.events.RunnerStateChange.State;
import static org.jdrupes.vmoperator.util.Constants.VM_OP_GROUP;
import static org.jdrupes.vmoperator.util.Constants.VM_OP_KIND_VM;
import org.jdrupes.vmoperator.util.GsonPtr;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.HandlingError;
import org.jgrapes.core.events.Start;
import org.jgrapes.util.events.ConfigurationUpdate;
import org.jgrapes.util.events.InitialConfiguration;

/**
 * Updates the CR status.
 */
public class StatusUpdater extends Component {

    private static final Set<State> RUNNING_STATES
        = Set.of(State.RUNNING, State.TERMINATING);

    private String namespace;
    private String vmName;
    private DynamicKubernetesApi vmCrApi;
    private long observedGeneration;

    /**
     * Instantiates a new status updater.
     *
     * @param componentChannel the component channel
     */
    public StatusUpdater(Channel componentChannel) {
        super(componentChannel);
    }

    /**
     * On handling error.
     *
     * @param event the event
     */
    @Handler(channels = Channel.class)
    public void onHandlingError(HandlingError event) {
        if (event.throwable() instanceof ApiException exc) {
            logger.log(Level.WARNING, exc,
                () -> "Problem accessing kubernetes: " + exc.getResponseBody());
            event.stop();
        }
    }

    /**
     * On configuration update.
     *
     * @param event the event
     */
    @Handler
    @SuppressWarnings("unchecked")
    public void onConfigurationUpdate(ConfigurationUpdate event) {
        event.structured("/Runner").ifPresent(c -> {
            if (event instanceof InitialConfiguration) {
                namespace = (String) c.get("namespace");
                updateNamespace();
                vmName = Optional.ofNullable((Map<String, String>) c.get("vm"))
                    .map(vm -> vm.get("name")).orElse(null);
            }
        });
    }

    private void updateNamespace() {
        if (namespace == null) {
            var path = Path
                .of("/var/run/secrets/kubernetes.io/serviceaccount/namespace");
            if (Files.isReadable(path)) {
                try {
                    namespace = Files.lines(path).findFirst().orElse(null);
                } catch (IOException e) {
                    logger.log(Level.WARNING, e,
                        () -> "Cannot read namespace.");
                }
            }
        }
        if (namespace == null) {
            logger.warning(() -> "Namespace is unknown, some functions"
                + " won't be available.");
        }
    }

    /**
     * Handle the start event.
     *
     * @param event the event
     * @throws IOException 
     * @throws ApiException 
     */
    @Handler
    @SuppressWarnings({ "PMD.DataflowAnomalyAnalysis",
        "PMD.AvoidInstantiatingObjectsInLoops", "PMD.AvoidDuplicateLiterals" })
    public void onStart(Start event) throws IOException, ApiException {
        if (namespace == null) {
            return;
        }
        var client = Config.defaultClient();
        var apis = new ApisApi(client).getAPIVersions();
        var crdVersions = apis.getGroups().stream()
            .filter(g -> g.getName().equals(VM_OP_GROUP)).findFirst()
            .map(V1APIGroup::getVersions).stream().flatMap(l -> l.stream())
            .map(V1GroupVersionForDiscovery::getVersion).toList();
        var coa = new CustomObjectsApi(client);
        for (var crdVersion : crdVersions) {
            var crdApiRes = coa.getAPIResources(VM_OP_GROUP,
                crdVersion).getResources().stream()
                .filter(r -> VM_OP_KIND_VM.equals(r.getKind())).findFirst();
            if (crdApiRes.isEmpty()) {
                continue;
            }
            var crApi = new DynamicKubernetesApi(VM_OP_GROUP,
                crdVersion, crdApiRes.get().getName(), client);
            var vmCr = crApi.get(namespace, vmName);
            if (vmCr.isSuccess()) {
                vmCrApi = crApi;
                observedGeneration
                    = vmCr.getObject().getMetadata().getGeneration();
                break;
            }
        }
        if (vmCrApi == null) {
            logger.warning(() -> "Cannot find VM's CR, status will not"
                + " be updated.");
        }
    }

    @SuppressWarnings("PMD.AvoidDuplicateLiterals")
    private JsonObject currentStatus(DynamicKubernetesObject vmCr) {
        return vmCr.getRaw().getAsJsonObject("status").deepCopy();
    }

    /**
     * On runner configuration update.
     *
     * @param event the event
     * @throws ApiException 
     */
    @Handler
    public void onRunnerConfigurationUpdate(RunnerConfigurationUpdate event)
            throws ApiException {
        if (vmCrApi == null) {
            return;
        }
        // A change of the runner configuration is typically caused
        // by a new version of the CR. So we observe the new CR.
        var vmCr = vmCrApi.get(namespace, vmName).throwsApiException()
            .getObject();
        if (vmCr.getMetadata().getGeneration() == observedGeneration) {
            return;
        }
        vmCrApi.updateStatus(vmCr, from -> {
            JsonObject status = currentStatus(from);
            status.getAsJsonArray("conditions").asList().stream()
                .map(cond -> (JsonObject) cond).filter(cond -> "Running"
                    .equals(cond.get("type").getAsString()))
                .forEach(cond -> cond.addProperty("observedGeneration",
                    from.getMetadata().getGeneration()));
            return status;
        });
    }

    /**
     * On runner state changed.
     *
     * @param event the event
     * @throws ApiException 
     */
    @Handler
    public void onRunnerStateChanged(RunnerStateChange event)
            throws ApiException {
        if (vmCrApi == null) {
            return;
        }
        var vmCr = vmCrApi.get(namespace, vmName).throwsApiException()
            .getObject();
        vmCrApi.updateStatus(vmCr, from -> {
            JsonObject status = currentStatus(from);
            status.getAsJsonArray("conditions").asList().stream()
                .map(cond -> (JsonObject) cond)
                .forEach(cond -> {
                    if ("Running".equals(cond.get("type").getAsString())) {
                        updateRunningCondition(event, from, cond);
                    }
                });
            if (event.state() == State.STARTING) {
                status.addProperty("ram", GsonPtr.to(from.getRaw())
                    .getAsString("spec", "vm", "maximumRam").orElse("0"));
                status.addProperty("cpus", 1);
            } else if (event.state() == State.STOPPED) {
                status.addProperty("ram", "0");
                status.addProperty("cpus", 0);
            }
            return status;
        }).throwsApiException();
    }

    private void updateRunningCondition(RunnerStateChange event,
            DynamicKubernetesObject from, JsonObject cond) {
        boolean reportedRunning
            = "True".equals(cond.get("status").getAsString());
        if (RUNNING_STATES.contains(event.state())
            && !reportedRunning) {
            cond.addProperty("status", "True");
            cond.addProperty("lastTransitionTime",
                Instant.now().toString());
        }
        if (!RUNNING_STATES.contains(event.state())
            && reportedRunning) {
            cond.addProperty("status", "False");
            cond.addProperty("lastTransitionTime",
                Instant.now().toString());
        }
        cond.addProperty("reason", event.reason());
        cond.addProperty("message", event.message());
        cond.addProperty("observedGeneration",
            from.getMetadata().getGeneration());
    }

    /**
     * On ballon change.
     *
     * @param event the event
     * @throws ApiException 
     */
    @Handler
    public void onBallonChange(BalloonChangeEvent event) throws ApiException {
        if (vmCrApi == null) {
            return;
        }
        var vmCr = vmCrApi.get(namespace, vmName).throwsApiException()
            .getObject();
        vmCrApi.updateStatus(vmCr, from -> {
            JsonObject status = currentStatus(from);
            status.addProperty("ram",
                new Quantity(new BigDecimal(event.size()), Format.BINARY_SI)
                    .toSuffixedString());
            return status;
        }).throwsApiException();
    }

    /**
     * On ballon change.
     *
     * @param event the event
     * @throws ApiException 
     */
    @Handler
    public void onCpuChange(HotpluggableCpuStatus event) throws ApiException {
        if (vmCrApi == null) {
            return;
        }
        var vmCr = vmCrApi.get(namespace, vmName).throwsApiException()
            .getObject();
        vmCrApi.updateStatus(vmCr, from -> {
            JsonObject status = currentStatus(from);
            status.addProperty("cpus", event.usedCpus().size());
            return status;
        }).throwsApiException();
    }
}
