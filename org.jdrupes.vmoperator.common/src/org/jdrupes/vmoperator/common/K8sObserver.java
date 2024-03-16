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

package org.jdrupes.vmoperator.common;

import io.kubernetes.client.Discovery.APIResource;
import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.Watch.Response;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.options.ListOptions;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An observer that watches namespaced resources in a given context and
 * invokes a handler on changes.
 *
 * @param <O> the object type for the context
 * @param <L> the object list type for the context
 */
public class K8sObserver<O extends KubernetesObject,
        L extends KubernetesListObject> {

    /**
     * The type of change reported by {@link Response} as enum.
     */
    public enum ResponseType {
        ADDED, MODIFIED, DELETED
    }

    @SuppressWarnings("PMD.FieldNamingConventions")
    protected final Logger logger = Logger.getLogger(getClass().getName());

    protected final K8sClient client;
    protected final GenericKubernetesApi<O, L> api;
    protected final APIResource context;
    protected final String namespace;
    protected final ListOptions options;
    protected final Thread thread;
    protected BiConsumer<K8sClient, Response<O>> handler;
    protected BiConsumer<K8sObserver<O, L>, Throwable> onTerminated;

    /**
     * Create and start a new observer for objects in the given context 
     * (using preferred version) and namespace with the given options.
     *
     * @param objectClass the object class
     * @param objectListClass the object list class
     * @param client the client
     * @param context the context
     * @param namespace the namespace
     * @param options the options
     * @return the stub if the object exists
     */
    @SuppressWarnings({ "PMD.AvoidBranchingStatementAsLastInLoop",
        "PMD.UseObjectForClearerAPI", "PMD.AvoidCatchingThrowable" })
    public K8sObserver(Class<O> objectClass, Class<L> objectListClass,
            K8sClient client, APIResource context, String namespace,
            ListOptions options) {
        this.client = client;
        this.context = context;
        this.namespace = namespace;
        this.options = options;

        api = new GenericKubernetesApi<>(objectClass, objectListClass,
            context.getGroup(), context.getPreferredVersion(),
            context.getResourcePlural(), client);
        thread = new Thread(() -> {
            try {
                logger.info(() -> "Watching " + context.getResourcePlural()
                    + " (" + context.getPreferredVersion() + ")"
                    + " in " + namespace);

                // Watch sometimes terminates without apparent reason.
                while (true) {
                    Instant startedAt = Instant.now();
                    try {
                        @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
                        var changed = api.watch(namespace, options).iterator();
                        while (changed.hasNext()) {
                            handler.accept(client, changed.next());
                        }
                    } catch (ApiException e) {
                        logger.log(Level.FINE, e, () -> "Problem watching"
                            + " (will retry): " + e.getMessage());
                        delayRestart(startedAt);
                    }
                }
            } catch (Throwable e) {
                logger.log(Level.SEVERE, e, () -> "Probem watching: "
                    + e.getMessage());
                if (onTerminated != null) {
                    onTerminated.accept(this, e);
                }
            }
        });
        thread.setDaemon(true);
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
     * Sets the handler.
     *
     * @param handler the handler
     * @return the observer
     */
    public K8sObserver<O, L>
            handler(BiConsumer<K8sClient, Response<O>> handler) {
        this.handler = handler;
        return this;
    }

    /**
     * Sets a function to invoke if the observer terminates. First argument
     * is this observer, the second is the throwable that caused the
     * termination.
     *
     * @param onTerminated the on terminated
     * @return the observer
     */
    public K8sObserver<O, L> onTerminated(
            BiConsumer<K8sObserver<O, L>, Throwable> onTerminated) {
        this.onTerminated = onTerminated;
        return this;
    }

    /**
     * Start the observer.
     *
     * @return the observer
     */
    public K8sObserver<O, L> start() {
        if (handler == null) {
            throw new IllegalStateException("No handler defined");
        }
        thread.start();
        return this;
    }

    /**
     * Returns the client.
     *
     * @return the client
     */
    public K8sClient client() {
        return client;
    }

    /**
     * Returns the context.
     * 
     * @return the context
     */
    public APIResource context() {
        return context;
    }

    /**
     * Returns the observed namespace.
     * 
     * @return the namespace
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Returns the options for object selection.
     *
     * @return the list options
     */
    public ListOptions options() {
        return options;
    }

    @Override
    @SuppressWarnings("PMD.UseLocaleWithCaseConversions")
    public String toString() {
        return "Observer for " + K8s.toString(context) + " " + namespace;
    }

}
