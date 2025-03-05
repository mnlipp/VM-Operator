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

package org.jdrupes.vmoperator.runner.qemu;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.K8sGenericStub;
import org.jdrupes.vmoperator.common.VmDefinition;
import org.jdrupes.vmoperator.runner.qemu.events.Exit;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.util.events.ConfigurationUpdate;
import org.jgrapes.util.events.InitialConfiguration;

/**
 * Updates the CR status.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class VmDefUpdater extends Component {

    protected String namespace;
    protected String vmName;
    protected K8sClient apiClient;

    /**
     * Instantiates a new status updater.
     *
     * @param componentChannel the component channel
     * @throws IOException 
     */
    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
    public VmDefUpdater(Channel componentChannel) {
        super(componentChannel);
        if (apiClient == null) {
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
     * Update condition. The `from` VM definition is used to determine the
     * observed generation and the current status. This method is intended
     * to be called in the function passed to
     * {@link K8sGenericStub#updateStatus}.
     *
     * @param from the VM definition
     * @param type the condition type
     * @param state the new state
     * @param reason the reason for the change
     * @param message the message
     * @return the updated status
     */
    protected JsonObject updateCondition(VmDefinition from, String type,
            boolean state, String reason, String message) {
        JsonObject status = from.statusJson();
        // Optimize, as we can get this several times
        var current = status.getAsJsonArray("conditions").asList().stream()
            .map(cond -> (JsonObject) cond)
            .filter(cond -> type.equals(cond.get("type").getAsString()))
            .findFirst();
        if (current.isPresent()
            && current.map(c -> c.get("status").getAsString())
                .map("True"::equals).map(s -> s == state).orElse(false)
            && current.map(c -> c.get("reason").getAsString())
                .map(reason::equals).orElse(false)) {
            return status;
        }

        // Do update
        final var condition = new HashMap<>(Map.of("type", type,
            "status", state ? "True" : "False",
            "observedGeneration", from.getMetadata().getGeneration(),
            "reason", reason,
            "lastTransitionTime", Instant.now().toString()));
        if (message != null) {
            condition.put("message", message);
        }
        List<Object> toReplace = new ArrayList<>(List.of(condition));
        List<Object> newConds
            = status.getAsJsonArray("conditions").asList().stream()
                .map(cond -> (JsonObject) cond)
                .map(cond -> type.equals(cond.get("type").getAsString())
                    ? toReplace.remove(0)
                    : cond)
                .collect(Collectors.toCollection(() -> new ArrayList<>()));
        newConds.addAll(toReplace);
        status.add("conditions",
            apiClient.getJSON().getGson().toJsonTree(newConds));
        return status;
    }
}
