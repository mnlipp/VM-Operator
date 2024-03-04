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
import io.kubernetes.client.openapi.models.V1APIResource;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.manager.events.Exit;
import org.jdrupes.vmoperator.manager.events.VmChannel;
import org.jdrupes.vmoperator.manager.events.VmDefChanged;
import org.jdrupes.vmoperator.manager.events.VmDefChanged.Type;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.Components;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.Start;
import org.jgrapes.core.events.Stop;
import org.jgrapes.util.events.ConfigurationUpdate;

/**
 * A base class for watching VM related resources.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public abstract class AbstractWatcher extends Component {

    private String namespaceToWatch;
    @SuppressWarnings({ "PMD.FieldNamingConventions",
        "PMD.VariableNamingConventions" })
    private static final Map<String, VmChannel> channels
        = new ConcurrentHashMap<>();

    /**
     * Initializes the instance.
     *
     * @param componentChannel the component channel
     */
    public AbstractWatcher(Channel componentChannel) {
        super(componentChannel);
    }

    /**
     * Configure the component.
     *
     * @param event the event
     */
    @Handler
    public void onConfigurationUpdate(ConfigurationUpdate event) {
        event.structured(Components.manager(parent()).componentPath())
            .ifPresent(c -> {
                if (c.containsKey("namespace")) {
                    namespaceToWatch = (String) c.get("namespace");
                }
            });
    }

    /**
     * Handle the start event. Configures the namespace and invokes
     * {@link #startWatching()}.
     *
     * @param event the event
     * @throws IOException 
     * @throws ApiException 
     */
    @Handler(priority = 10)
    public void onStart(Start event) {
        try {
            // Get namespace
            if (namespaceToWatch == null) {
                var path = Path
                    .of("/var/run/secrets/kubernetes.io/serviceaccount/namespace");
                if (Files.isReadable(path)) {
                    namespaceToWatch
                        = Files.lines(path).findFirst().orElse(null);
                }
            }
            // Availability already checked by Controller.onStart
            logger
                .fine(() -> "Watching namespace \"" + namespaceToWatch + "\".");

            startWatching();
        } catch (IOException | ApiException e) {
            logger.log(Level.SEVERE, e,
                () -> "Cannot watch VMs, terminating.");
            event.cancel(true);
            fire(new Exit(1));
        }
    }

    /**
     * Returns the namespace to use when watching watch.
     *
     * @return the string
     */
    protected String namespaceToWatch() {
        return namespaceToWatch;
    }

    /**
     * Invoked by {@link #onStart(Start)} after the namespace has
     * been configured.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws ApiException the api exception
     */
    protected abstract void startWatching() throws IOException, ApiException;

    /**
     * Starts a new daemon thread that watches the given resources,
     * using the given call that must return responses of the specified
     * type. Changes are reported by calling the given changeHandler
     * with each response from the call.
     *
     * @param <T> the resource type
     * @param resource the resource
     * @param call the call
     * @param responseType the type token
     * @param changeHandler the change handler
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws ApiException the API exception
     */
    protected <T> void startWatcher(V1APIResource resource, okhttp3.Call call,
            java.lang.reflect.Type responseType,
            BiConsumer<V1APIResource, Watch.Response<T>> changeHandler)
            throws IOException, ApiException {
        var client = Config.defaultClient();

        @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
            "PMD.AvoidCatchingThrowable" })
        var watcher = new Thread(() -> {
            try {
                logger.info(() -> "Watching resource " + resource.getName()
                    + "/" + resource.getVersion());
                // Watch sometimes terminates without apparent reason.
                while (true) {
                    Instant startedAt = Instant.now();
                    try (Watch<T> watch = Watch.createWatch(client, call,
                        responseType)) {
                        for (Watch.Response<T> item : watch) {
                            changeHandler.accept(resource, item);
                        }
                    } catch (IOException | ApiException | RuntimeException e) {
                        logger.log(Level.FINE, e, () -> "Problem watching"
                            + " display secrets (will retry): "
                            + e.getMessage());
                        delayRestart(startedAt);
                    }
                }
            } catch (Throwable e) {
                logger.log(Level.SEVERE, e, () -> "Probem watching: "
                    + e.getMessage());
            }
            fire(new Stop());
        });
        watcher.setDaemon(true);
        watcher.start();
    }

    @SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
    private void delayRestart(Instant started) {
        var runningFor = Duration
            .between(started, Instant.now()).toMillis();
        if (runningFor < 5000) {
            logger.log(Level.FINE, () -> "Waiting... ");
            try {
                Thread.sleep(5000 - runningFor);
            } catch (InterruptedException e1) { // NOPMD
                // Retry
            }
            logger.log(Level.FINE, () -> "Retrying");
        }
    }

    /**
     * Returns the {@link Channel} for the given VM.
     *
     * @param vmName the VM's name
     * @param create whether to create the channel if it doesn't exist
     * @return the channel used for events related to the specified VM
     */
    protected VmChannel channel(String vmName, boolean create) {
        if (!create) {
            return channels.get(vmName);
        }
        return channels.computeIfAbsent(vmName,
            k -> {
                try {
                    return new VmChannel(channel(), newEventPipeline(),
                        new K8sClient());
                } catch (IOException e) {
                    logger.log(Level.SEVERE, e, () -> "Failed to create client"
                        + " for handling changes: " + e.getMessage());
                    return null;
                }
            });
    }

    /**
     * Returns the {@link Channel} for the given VM.
     *
     * @param name the VM's name
     * @return the channel used for events related to the specified VM
     */
    protected VmChannel channel(String name) {
        return channel(name, true);
    }

    /**
     * Removes the VM channel when the VM is deleted.
     *
     * @param event the event
     * @param channel the channel
     */
    @Handler(priority = -10_000)
    public void onVmDefChanged(VmDefChanged event, VmChannel channel) {
        if (event.type() == Type.DELETED) {
            channels.remove(event.vmDefinition().getMetadata().getName());
        }
    }
}
