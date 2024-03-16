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

import io.kubernetes.client.Discovery.APIResource;
import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.Watch.Response;
import io.kubernetes.client.util.generic.options.ListOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Level;
import org.jdrupes.vmoperator.common.K8s;
import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.K8sObserver;
import org.jdrupes.vmoperator.common.K8sObserver.ResponseType;
import org.jdrupes.vmoperator.manager.events.Exit;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Component;
import org.jgrapes.core.Components;
import org.jgrapes.core.annotation.Handler;
import org.jgrapes.core.events.Start;
import org.jgrapes.core.events.Stop;
import org.jgrapes.util.events.ConfigurationUpdate;

/**
 * A base class for monitoring VM related resources.
 * 
 * @param <O> the object type for the context
 * @param <L> the object list type for the context
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis" })
public abstract class AbstractMonitor<O extends KubernetesObject,
        L extends KubernetesListObject, C extends Channel> extends Component {

    private final Class<O> objectClass;
    private final Class<L> objectListClass;
    private K8sClient client;
    private APIResource context;
    private String namespace;
    private ListOptions options = new ListOptions();
    private final AtomicInteger observerCounter = new AtomicInteger(0);
    @SuppressWarnings({ "PMD.FieldNamingConventions",
        "PMD.VariableNamingConventions" })
    private final Map<String, C> channels = new ConcurrentHashMap<>();

    /**
     * Initializes the instance.
     *
     * @param componentChannel the component channel
     */
    protected AbstractMonitor(Channel componentChannel, Class<O> objectClass,
            Class<L> objectListClass) {
        super(componentChannel);
        this.objectClass = objectClass;
        this.objectListClass = objectListClass;
    }

    /**
     * Return the client.
     * 
     * @return the client
     */
    public K8sClient client() {
        return client;
    }

    /**
     * Sets the client to be used.
     *
     * @param client the client
     * @return the abstract monitor
     */
    public AbstractMonitor<O, L, C> client(K8sClient client) {
        this.client = client;
        return this;
    }

    /**
     * Return the observed namespace.
     * 
     * @return the namespace
     */
    public String namespace() {
        return namespace;
    }

    /**
     * Sets the namespace to be observed.
     *
     * @param namespace the namespaceToWatch to set
     * @return the abstract monitor
     */
    public AbstractMonitor<O, L, C> namespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    /**
     * Returns the options for selecting the objects to observe.
     * 
     * @return the options
     */
    public ListOptions options() {
        return options;
    }

    /**
     * Sets the options for selecting the objects to observe.
     *
     * @param options the options to set
     * @return the abstract monitor
     */
    public AbstractMonitor<O, L, C> options(ListOptions options) {
        this.options = options;
        return this;
    }

    /**
     * Returns the observed context.
     * 
     * @return the context
     */
    public APIResource context() {
        return context;
    }

    /**
     * Sets the context to observe.
     *
     * @param context the context
     * @return the abstract monitor
     */
    public AbstractMonitor<O, L, C> context(APIResource context) {
        this.context = context;
        return this;
    }

    /**
     * Looks for a key "namespace" in the configuration and, if found,
     * sets the namespace to its value.
     *
     * @param event the event
     */
    @Handler
    public void onConfigurationUpdate(ConfigurationUpdate event) {
        event.structured(Components.manager(parent()).componentPath())
            .ifPresent(c -> {
                if (c.containsKey("namespace")) {
                    namespace = (String) c.get("namespace");
                }
            });
    }

    /**
     * Handle the start event. Configures the namespace invokes
     * {@link #prepareMonitoring()} and starts the observers.
     *
     * @param event the event
     */
    @Handler(priority = 10)
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public void onStart(Start event) {
        try {
            // Get namespace
            if (namespace == null) {
                var path = Path
                    .of("/var/run/secrets/kubernetes.io/serviceaccount/namespace");
                if (Files.isReadable(path)) {
                    namespace
                        = Files.lines(path).findFirst().orElse(null);
                }
            }

            // Additional preparations by derived class
            prepareMonitoring();
            assert client != null;
            assert context != null;
            assert namespace != null;
            logger.fine(() -> "Observing " + K8s.toString(context)
                + " objects in " + namespace);

            // Monitor all versions
            for (var version : context.getVersions()) {
                createObserver(version);
            }
            registerAsGenerator();
        } catch (IOException | ApiException e) {
            logger.log(Level.SEVERE, e,
                () -> "Cannot watch VMs, terminating.");
            event.cancel(true);
            fire(new Exit(1));
        }
    }

    private void createObserver(String version) {
        observerCounter.incrementAndGet();
        new K8sObserver<>(objectClass, objectListClass, client,
            K8s.preferred(context, version), namespace, options)
                .handler((c, r) -> {
                    handleChange(c, r);
                    if (ResponseType.valueOf(r.type) == ResponseType.DELETED) {
                        channels.remove(r.object.getMetadata().getName());
                    }
                }).onTerminated((o, t) -> {
                    // Exception has been logged already
                    if (observerCounter.decrementAndGet() == 0) {
                        unregisterAsGenerator();
                    }
                    fire(new Stop());
                }).start();
    }

    /**
     * Invoked by {@link #onStart(Start)} after the namespace has
     * been configured and before starting the observer.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws ApiException the api exception
     */
    @SuppressWarnings("PMD.EmptyMethodInAbstractClassShouldBeAbstract")
    protected void prepareMonitoring() throws IOException, ApiException {
        // To be overridden by derived class.
    }

    /**
     * Handle an observed change.
     *
     * @param client the client
     * @param change the change
     */
    protected abstract void handleChange(K8sClient client, Response<O> change);

    /**
     * Returns the {@link Channel} for the given name.
     *
     * @param name the name
     * @param supplier used to create the channel if it doesn't exist 
     * @return the channel used for events related to the specified object
     */
    protected C channel(String name, Function<String, C> supplier) {
        return channels.computeIfAbsent(name, supplier);
    }

    /**
     * Returns the {@link Channel} for the given name.
     *
     * @param name the name
     * @return the channel used for events related to the specified object
     */
    protected C channel(String name) {
        return channels.get(name);
    }
}
