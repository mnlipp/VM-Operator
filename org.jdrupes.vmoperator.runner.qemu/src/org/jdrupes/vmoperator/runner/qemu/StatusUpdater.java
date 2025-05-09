/*
 * VM-Operator
 * Copyright (C) 2023,2025 Michael N. Lipp
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.kubernetes.client.apimachinery.GroupVersionKind;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.custom.Quantity.Format;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.EventsV1Event;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Level;
import static org.jdrupes.vmoperator.common.Constants.APP_NAME;
import org.jdrupes.vmoperator.common.Constants.Crd;
import org.jdrupes.vmoperator.common.Constants.Status;
import org.jdrupes.vmoperator.common.Constants.Status.Condition;
import org.jdrupes.vmoperator.common.Constants.Status.Condition.Reason;
import org.jdrupes.vmoperator.common.K8s;
import org.jdrupes.vmoperator.common.VmDefinition;
import org.jdrupes.vmoperator.common.VmDefinitionStub;
import org.jdrupes.vmoperator.runner.qemu.events.BalloonChangeEvent;
import org.jdrupes.vmoperator.runner.qemu.events.ConfigureQemu;
import org.jdrupes.vmoperator.runner.qemu.events.DisplayPasswordChanged;
import org.jdrupes.vmoperator.runner.qemu.events.Exit;
import org.jdrupes.vmoperator.runner.qemu.events.HotpluggableCpuStatus;
import org.jdrupes.vmoperator.runner.qemu.events.OsinfoEvent;
import org.jdrupes.vmoperator.runner.qemu.events.RunnerStateChange;
import org.jdrupes.vmoperator.runner.qemu.events.RunnerStateChange.RunState;
import org.jdrupes.vmoperator.runner.qemu.events.ShutdownEvent;
import org.jdrupes.vmoperator.runner.qemu.events.VmopAgentConnected;
import org.jdrupes.vmoperator.runner.qemu.events.VmopAgentLoggedIn;
import org.jdrupes.vmoperator.runner.qemu.events.VmopAgentLoggedOut;
import org.jdrupes.vmoperator.util.GsonPtr;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Components;
import org.jgrapes.core.Components.Timer;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.HandlingError;
import org.jgrapes.core.events.Start;

/**
 * Updates the CR status.
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis",
    "PMD.CouplingBetweenObjects" })
public class StatusUpdater extends VmDefUpdater {

    @SuppressWarnings("PMD.FieldNamingConventions")
    private static final Gson gson = new JSON().getGson();
    @SuppressWarnings("PMD.FieldNamingConventions")
    private static final ObjectMapper objectMapper
        = new ObjectMapper().registerModule(new JavaTimeModule());

    private boolean guestShutdownStops;
    private boolean shutdownByGuest;
    private VmDefinitionStub vmStub;
    private String loggedInUser;
    private BigInteger lastRamValue;
    private Instant lastRamChange;
    private Timer balloonTimer;
    private BigInteger targetRamValue;

    /**
     * Instantiates a new status updater.
     *
     * @param componentChannel the component channel
     */
    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
    public StatusUpdater(Channel componentChannel) {
        super(componentChannel);
        attach(new ConsoleTracker(componentChannel));
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
            vmStub = VmDefinitionStub.get(apiClient,
                new GroupVersionKind(Crd.GROUP, "", Crd.KIND_VM),
                namespace, vmName);
            var vmDef = vmStub.model().orElse(null);
            if (vmDef == null) {
                return;
            }
            vmStub.updateStatus(from -> {
                JsonObject status = from.statusJson();
                status.addProperty(Status.RUNNER_VERSION, Optional.ofNullable(
                    Runner.class.getPackage().getImplementationVersion())
                    .orElse("(unknown)"));
                status.remove(Status.LOGGED_IN_USER);
                return status;
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
        loggedInUser = event.configuration().vm.display.loggedInUser;
        targetRamValue = event.configuration().vm.currentRam;

        // Remainder applies only if we have a connection to k8s.
        if (vmStub == null) {
            return;
        }
        vmStub.updateStatus(from -> {
            JsonObject status = from.statusJson();
            if (!event.configuration().hasDisplayPassword) {
                status.addProperty(Status.DISPLAY_PASSWORD_SERIAL, -1);
            }
            status.getAsJsonArray("conditions").asList().stream()
                .map(cond -> (JsonObject) cond)
                .filter(cond -> Condition.RUNNING
                    .equals(cond.get("type").getAsString()))
                .forEach(cond -> cond.addProperty("observedGeneration",
                    from.getMetadata().getGeneration()));
            updateUserLoggedIn(from);
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
    @SuppressWarnings({ "PMD.AvoidLiteralsInIfCondition",
        "PMD.AssignmentInOperand", "PMD.AvoidDuplicateLiterals" })
    public void onRunnerStateChanged(RunnerStateChange event)
            throws ApiException {
        VmDefinition vmDef;
        if (vmStub == null || (vmDef = vmStub.model().orElse(null)) == null) {
            return;
        }
        vmStub.updateStatus(from -> {
            boolean running = event.runState().vmRunning();
            updateCondition(vmDef, Condition.RUNNING, running, event.reason(),
                event.message());
            JsonObject status = updateCondition(vmDef, Condition.BOOTED,
                event.runState() == RunState.BOOTED, event.reason(),
                event.message());
            if (event.runState() == RunState.STARTING) {
                status.addProperty(Status.RAM, GsonPtr.to(from.data())
                    .getAsString("spec", "vm", "maximumRam").orElse("0"));
                status.addProperty(Status.CPUS, 1);
            } else if (event.runState() == RunState.STOPPED) {
                status.addProperty(Status.RAM, "0");
                status.addProperty(Status.CPUS, 0);
                status.remove(Status.LOGGED_IN_USER);
            }

            if (!running) {
                // In case console connection was still present
                status.addProperty(Status.CONSOLE_CLIENT, "");
                updateCondition(from, Condition.CONSOLE_CONNECTED, false,
                    "VmStopped",
                    "The VM is not running");

                // In case we had an irregular shutdown
                updateCondition(from, Condition.USER_LOGGED_IN, false,
                    "VmStopped", "The VM is not running");
                status.remove(Status.OSINFO);
                updateCondition(vmDef, "VmopAgentConnected", false, "VmStopped",
                    "The VM is not running");
            }
            return status;
        }, vmDef);

        // Maybe stop VM
        if (event.runState() == RunState.TERMINATING && !event.failed()
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
            .reportingController(Crd.GROUP + "/" + APP_NAME)
            .action("StatusUpdate").reason(event.reason())
            .note(event.message());
        K8s.createEvent(apiClient, vmDef, evt);
    }

    private void updateUserLoggedIn(VmDefinition from) {
        if (loggedInUser == null) {
            updateCondition(from, Condition.USER_LOGGED_IN, false,
                Reason.NOT_REQUESTED, "No user to be logged in");
            return;
        }
        if (!from.conditionStatus(Condition.VMOP_AGENT).orElse(false)) {
            updateCondition(from, Condition.USER_LOGGED_IN, false,
                "VmopAgentDisconnected", "Waiting for VMOP agent to connect");
            return;
        }
        if (!from.fromStatus(Status.LOGGED_IN_USER).map(loggedInUser::equals)
            .orElse(false)) {
            updateCondition(from, Condition.USER_LOGGED_IN, false,
                "Processing", "Waiting for user to be logged in");
        }
        updateCondition(from, Condition.USER_LOGGED_IN, true,
            Reason.LOGGED_IN, "User is logged in");
    }

    /**
     * Update the current RAM size in the status. Balloon changes happen
     * more than once every second during changes. While this is nice
     * to watch, this puts a heavy load on the system. Therefore we
     * only update the status once every 15 seconds or when the target
     * value is reached.
     *
     * @param event the event
     * @throws ApiException 
     */
    @Handler
    public void onBallonChange(BalloonChangeEvent event) throws ApiException {
        if (vmStub == null) {
            return;
        }
        Instant now = Instant.now();
        if (lastRamChange == null
            || lastRamChange.isBefore(now.minusSeconds(15))
            || event.size().equals(targetRamValue)) {
            if (balloonTimer != null) {
                balloonTimer.cancel();
                balloonTimer = null;
            }
            lastRamChange = now;
            lastRamValue = event.size();
            updateRam();
            return;
        }

        // Save for later processing and maybe start timer
        lastRamChange = now;
        lastRamValue = event.size();
        if (balloonTimer != null) {
            return;
        }
        final var pipeline = activeEventPipeline();
        balloonTimer = Components.schedule(t -> {
            pipeline.submit("Update RAM size", () -> {
                try {
                    updateRam();
                } catch (ApiException e) {
                    logger.log(Level.WARNING, e,
                        () -> "Failed to update ram size: " + e.getMessage());
                }
                balloonTimer = null;
            });
        }, now.plusSeconds(15));
    }

    private void updateRam() throws ApiException {
        vmStub.updateStatus(from -> {
            JsonObject status = from.statusJson();
            status.addProperty(Status.RAM,
                new Quantity(new BigDecimal(lastRamValue), Format.BINARY_SI)
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
            JsonObject status = from.statusJson();
            status.addProperty(Status.CPUS, event.usedCpus().size());
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
            JsonObject status = from.statusJson();
            status.addProperty(Status.DISPLAY_PASSWORD_SERIAL,
                status.get(Status.DISPLAY_PASSWORD_SERIAL).getAsLong() + 1);
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

    /**
     * On osinfo.
     *
     * @param event the event
     * @throws ApiException 
     */
    @Handler
    public void onOsinfo(OsinfoEvent event) throws ApiException {
        if (vmStub == null) {
            return;
        }
        var asGson = gson.toJsonTree(
            objectMapper.convertValue(event.osinfo(), Object.class));
        vmStub.updateStatus(from -> {
            JsonObject status = from.statusJson();
            status.add(Status.OSINFO, asGson);
            return status;
        });

    }

    /**
     * @param event the event
     * @throws ApiException 
     */
    @Handler
    @SuppressWarnings("PMD.AssignmentInOperand")
    public void onVmopAgentConnected(VmopAgentConnected event)
            throws ApiException {
        VmDefinition vmDef;
        if (vmStub == null || (vmDef = vmStub.model().orElse(null)) == null) {
            return;
        }
        vmStub.updateStatus(from -> {
            var status = updateCondition(vmDef, "VmopAgentConnected",
                true, "VmopAgentStarted", "The VM operator agent is running");
            updateUserLoggedIn(from);
            return status;
        }, vmDef);
    }

    /**
     * @param event the event
     * @throws ApiException 
     */
    @Handler
    @SuppressWarnings("PMD.AssignmentInOperand")
    public void onVmopAgentLoggedIn(VmopAgentLoggedIn event)
            throws ApiException {
        vmStub.updateStatus(from -> {
            JsonObject status = from.statusJson();
            status.addProperty(Status.LOGGED_IN_USER,
                event.triggering().user());
            updateUserLoggedIn(from);
            return status;
        });
    }

    /**
     * @param event the event
     * @throws ApiException 
     */
    @Handler
    @SuppressWarnings("PMD.AssignmentInOperand")
    public void onVmopAgentLoggedOut(VmopAgentLoggedOut event)
            throws ApiException {
        vmStub.updateStatus(from -> {
            JsonObject status = from.statusJson();
            status.remove(Status.LOGGED_IN_USER);
            updateUserLoggedIn(from);
            return status;
        });
    }
}
