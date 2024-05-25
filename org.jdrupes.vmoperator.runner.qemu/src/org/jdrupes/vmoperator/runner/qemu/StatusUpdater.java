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

package org.jdrupes.vmoperator.runner.qemu;

import com.google.gson.JsonObject;
import io.kubernetes.client.apimachinery.GroupVersionKind;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.custom.Quantity.Format;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.EventsV1Event;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import static org.jdrupes.vmoperator.common.Constants.APP_NAME;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_GROUP;
import static org.jdrupes.vmoperator.common.Constants.VM_OP_KIND_VM;
import org.jdrupes.vmoperator.common.K8s;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.K8sDynamicModel;
import org.jdrupes.vmoperator.common.K8sDynamicStub;
import org.jdrupes.vmoperator.runner.qemu.events.BalloonChangeEvent;
import org.jdrupes.vmoperator.runner.qemu.events.ConfigureQemu;
import org.jdrupes.vmoperator.runner.qemu.events.DisplayPasswordChanged;
import org.jdrupes.vmoperator.runner.qemu.events.Exit;
import org.jdrupes.vmoperator.runner.qemu.events.HotpluggableCpuStatus;
import org.jdrupes.vmoperator.runner.qemu.events.RunnerStateChange;
import org.jdrupes.vmoperator.runner.qemu.events.RunnerStateChange.State;
import org.jdrupes.vmoperator.runner.qemu.events.ShutdownEvent;
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
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class StatusUpdater extends Component {

    private static final Set<State> RUNNING_STATES
        = Set.of(State.RUNNING, State.TERMINATING);

    private String namespace;
    private String vmName;
    private K8sClient apiClient;
    private long observedGeneration;
    private boolean guestShutdownStops;
    private boolean shutdownByGuest;
    private K8sDynamicStub vmStub;

    /**
     * Instantiates a new status updater.
     *
     * @param componentChannel the component channel
     */
    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
    public StatusUpdater(Channel componentChannel) {
        super(componentChannel);
        try {
            apiClient = new K8sClient();
            io.kubernetes.client.openapi.Configuration
                .setDefaultApiClient(apiClient);
        } catch (IOException e) {
            logger.log(Level.SEVERE, e,
                () -> "Cannot access events API, terminating.");
            fire(new Exit(1));
        }
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
    public void onStart(Start event) {
        if (namespace == null) {
            return;
        }
        try {
            vmStub = K8sDynamicStub.get(apiClient,
                new GroupVersionKind(VM_OP_GROUP, "", VM_OP_KIND_VM),
                namespace, vmName);
            vmStub.model().ifPresent(model -> {
                observedGeneration = model.getMetadata().getGeneration();
            });
        } catch (ApiException e) {
            logger.log(Level.SEVERE, e,
                () -> "Cannot access VM object, terminating.");
            event.cancel(true);
            fire(new Exit(1));
        }
    }

    /**
     * On runner configuration update.
     *
     * @param event the event
     * @throws ApiException 
     */
    @Handler
    @SuppressWarnings("PMD.AvoidDuplicateLiterals")
    public void onConfigureQemu(ConfigureQemu event)
            throws ApiException {
        guestShutdownStops = event.configuration().guestShutdownStops;

        // Remainder applies only if we have a connection to k8s.
        if (vmStub == null) {
            return;
        }

        // A change of the runner configuration is typically caused
        // by a new version of the CR. So we update only if we have
        // a new version of the CR. There's one exception: the display
        // password is configured by a file, not by the CR.
        var vmDef = vmStub.model();
        if (vmDef.isPresent()
            && vmDef.get().metadata().getGeneration() == observedGeneration
            && (event.configuration().hasDisplayPassword
                || vmDef.get().status().getAsJsonPrimitive(
                    "displayPasswordSerial").getAsInt() == -1)) {
            return;
        }
        vmStub.updateStatus(vmDef.get(), from -> {
            JsonObject status = from.status();
            if (!event.configuration().hasDisplayPassword) {
                status.addProperty("displayPasswordSerial", -1);
            }
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
    @SuppressWarnings({ "PMD.AssignmentInOperand",
        "PMD.AvoidLiteralsInIfCondition" })
    public void onRunnerStateChanged(RunnerStateChange event)
            throws ApiException {
        K8sDynamicModel vmDef;
        if (vmStub == null || (vmDef = vmStub.model().orElse(null)) == null) {
            return;
        }
        vmStub.updateStatus(vmDef, from -> {
            JsonObject status = from.status();
            status.getAsJsonArray("conditions").asList().stream()
                .map(cond -> (JsonObject) cond)
                .forEach(cond -> {
                    if ("Running".equals(cond.get("type").getAsString())) {
                        updateRunningCondition(event, from, cond);
                    }
                });
            if (event.state() == State.STARTING) {
                status.addProperty("ram", GsonPtr.to(from.data())
                    .getAsString("spec", "vm", "maximumRam").orElse("0"));
                status.addProperty("cpus", 1);
            } else if (event.state() == State.STOPPED) {
                status.addProperty("ram", "0");
                status.addProperty("cpus", 0);
            }
            return status;
        });

        // Maybe stop VM
        if (event.state() == State.TERMINATING && !event.failed()
            && guestShutdownStops && shutdownByGuest) {
            logger.info(() -> "Stopping VM because of shutdown by guest.");
            var res = vmStub.patch(V1Patch.PATCH_FORMAT_JSON_PATCH,
                new V1Patch("[{\"op\": \"replace\", \"path\": \"/spec/vm/state"
                    + "\", \"value\": \"Stopped\"}]"),
                apiClient.defaultPatchOptions());
            if (!res.isPresent()) {
                logger.warning(
                    () -> "Cannot patch pod annotations for: " + vmStub.name());
            }
        }

        // Log event
        var evt = new EventsV1Event()
            .reportingController(VM_OP_GROUP + "/" + APP_NAME)
            .action("StatusUpdate").reason(event.reason())
            .note(event.message());
        K8s.createEvent(apiClient, vmDef, evt);
    }

    private void updateRunningCondition(RunnerStateChange event,
            K8sDynamicModel from, JsonObject cond) {
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
        if (vmStub == null) {
            return;
        }
        vmStub.updateStatus(from -> {
            JsonObject status = from.status();
            status.addProperty("ram",
                new Quantity(new BigDecimal(event.size()), Format.BINARY_SI)
                    .toSuffixedString());
            return status;
        });
    }

    /**
     * On ballon change.
     *
     * @param event the event
     * @throws ApiException 
     */
    @Handler
    public void onCpuChange(HotpluggableCpuStatus event) throws ApiException {
        if (vmStub == null) {
            return;
        }
        vmStub.updateStatus(from -> {
            JsonObject status = from.status();
            status.addProperty("cpus", event.usedCpus().size());
            return status;
        });
    }

    /**
     * On ballon change.
     *
     * @param event the event
     * @throws ApiException 
     */
    @Handler
    public void onDisplayPasswordChanged(DisplayPasswordChanged event)
            throws ApiException {
        if (vmStub == null) {
            return;
        }
        vmStub.updateStatus(from -> {
            JsonObject status = from.status();
            status.addProperty("displayPasswordSerial",
                status.get("displayPasswordSerial").getAsLong() + 1);
            return status;
        });
    }

    /**
     * On shutdown.
     *
     * @param event the event
     * @throws ApiException the api exception
     */
    @Handler
    public void onShutdown(ShutdownEvent event) throws ApiException {
        shutdownByGuest = event.byGuest();
    }
}
