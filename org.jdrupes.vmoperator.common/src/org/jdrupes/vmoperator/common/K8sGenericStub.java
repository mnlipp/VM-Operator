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

import com.google.gson.Gson;
import io.kubernetes.client.Discovery.APIResource;
import io.kubernetes.client.apimachinery.GroupVersionKind;
import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.Strings;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.options.ListOptions;
import io.kubernetes.client.util.generic.options.PatchOptions;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

/**
 * A stub for namespaced custom objects. This stub provides the
 * functions common to all Kubernetes objects, but uses variables
 * for all types. This class should be used as base class only.
 *
 * @param <O> the generic type
 * @param <L> the generic type
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class K8sGenericStub<O extends KubernetesObject,
        L extends KubernetesListObject> {
    protected final K8sClient client;
    private final GenericKubernetesApi<O, L> api;
    protected final String group;
    protected final String version;
    protected final String kind;
    protected final String plural;
    protected final String namespace;
    protected final String name;

    /**
     * Get a namespaced object stub. If the version in parameter
     * `gvk` is an empty string, the stub refers to the first object 
     * found with matching group and kind. 
     *
     * @param <O> the object type
     * @param <L> the object list type
     * @param <R> the stub type
     * @param objectClass the object class
     * @param objectListClass the object list class
     * @param client the client
     * @param gvk the group, version and kind
     * @param namespace the namespace
     * @param name the name
     * @param provider the provider
     * @return the stub if the object exists
     * @throws ApiException the api exception
     */
    @SuppressWarnings({ "PMD.AvoidBranchingStatementAsLastInLoop",
        "PMD.AvoidInstantiatingObjectsInLoops" })
    public static <O extends KubernetesObject, L extends KubernetesListObject,
            R extends K8sGenericStub<O, L>>
            R get(Class<O> objectClass, Class<L> objectListClass,
                    K8sClient client, GroupVersionKind gvk, String namespace,
                    String name, GenericSupplier<O, L, R> provider)
                    throws ApiException {
        var context = K8s.context(client, gvk.getGroup(), gvk.getVersion(),
            gvk.getKind());
        if (context.isEmpty()) {
            throw new ApiException("No known API for " + gvk.getGroup()
                + "/" + gvk.getVersion() + " " + gvk.getKind());
        }
        return provider.get(objectClass, objectListClass, client, context.get(),
            namespace, name);
    }

    /**
     * Get a namespaced object stub.
     *
     * @param <O> the object type
     * @param <L> the object list type
     * @param <R> the stub type
     * @param objectClass the object class
     * @param objectListClass the object list class
     * @param client the client
     * @param context the context
     * @param namespace the namespace
     * @param name the name
     * @param provider the provider
     * @return the stub if the object exists
     * @throws ApiException the api exception
     */
    @SuppressWarnings({ "PMD.AvoidBranchingStatementAsLastInLoop",
        "PMD.AvoidInstantiatingObjectsInLoops", "PMD.UseObjectForClearerAPI" })
    public static <O extends KubernetesObject, L extends KubernetesListObject,
            R extends K8sGenericStub<O, L>>
            R get(Class<O> objectClass, Class<L> objectListClass,
                    K8sClient client, APIResource context, String namespace,
                    String name, GenericSupplier<O, L, R> provider)
                    throws ApiException {
        return provider.get(objectClass, objectListClass, client,
            context, namespace, name);
    }

    /**
     * Get a namespaced object stub for a newly created object.
     *
     * @param <O> the object type
     * @param <L> the object list type
     * @param <R> the stub type
     * @param objectClass the object class
     * @param objectListClass the object list class
     * @param client the client
     * @param context the context
     * @param model the model
     * @param provider the provider
     * @return the stub if the object exists
     * @throws ApiException the api exception
     */
    @SuppressWarnings({ "PMD.AvoidBranchingStatementAsLastInLoop",
        "PMD.AvoidInstantiatingObjectsInLoops", "PMD.UseObjectForClearerAPI" })
    public static <O extends KubernetesObject, L extends KubernetesListObject,
            R extends K8sGenericStub<O, L>>
            R create(Class<O> objectClass, Class<L> objectListClass,
                    K8sClient client, APIResource context, O model,
                    GenericSupplier<O, L, R> provider) throws ApiException {
        var api = new GenericKubernetesApi<>(objectClass, objectListClass,
            context.getGroup(), context.getPreferredVersion(),
            context.getResourcePlural(), client);
        api.create(model).throwsApiException();
        return provider.get(objectClass, objectListClass, client,
            context, model.getMetadata().getNamespace(),
            model.getMetadata().getName());
    }

    /**
     * Get the stubs for the objects in the given namespace that match
     * the criteria from the given options.
     *
     * @param <O> the object type
     * @param <L> the object list type
     * @param <R> the stub type
     * @param objectClass the object class
     * @param objectListClass the object list class
     * @param client the client
     * @param context the context
     * @param namespace the namespace
     * @param options the options
     * @param provider the provider
     * @return the collection
     * @throws ApiException the api exception
     */
    public static <O extends KubernetesObject, L extends KubernetesListObject,
            R extends K8sGenericStub<O, L>>
            Collection<R> list(Class<O> objectClass, Class<L> objectListClass,
                    K8sClient client, APIResource context, String namespace,
                    ListOptions options, SpecificSupplier<O, L, R> provider)
                    throws ApiException {
        var api = new GenericKubernetesApi<>(objectClass, objectListClass,
            context.getGroup(), context.getPreferredVersion(),
            context.getResourcePlural(), client);
        var objs = api.list(namespace, options).throwsApiException();
        var result = new ArrayList<R>();
        for (var item : objs.getObject().getItems()) {
            result.add(
                provider.get(client, namespace, item.getMetadata().getName()));
        }
        return result;
    }

    /**
     * Instantiates a new namespaced custom object stub.
     *
     * @param objectClass the object class
     * @param objectListClass the object list class
     * @param client the client
     * @param context the context
     * @param namespace the namespace
     * @param name the name
     */
    protected K8sGenericStub(Class<O> objectClass, Class<L> objectListClass,
            K8sClient client, APIResource context, String namespace,
            String name) {
        this.client = client;
        group = context.getGroup();
        version = context.getPreferredVersion();
        kind = context.getKind();
        plural = context.getResourcePlural();
        this.namespace = namespace;
        this.name = name;

        Gson gson = client.getJSON().getGson();
        if (!checkAdapters(client)) {
            client.getJSON().setGson(gson.newBuilder()
                .registerTypeAdapterFactory(
                    new K8sDynamicModelTypeAdapterFactory())
                .create());
        }
        api = new GenericKubernetesApi<>(objectClass,
            objectListClass, group, version, plural, client);
    }

    private boolean checkAdapters(ApiClient client) {
        return K8sDynamicModelTypeAdapterFactory.K8sDynamicModelCreator.class
            .equals(client.getJSON().getGson().getAdapter(K8sDynamicModel.class)
                .getClass())
            && K8sDynamicModelTypeAdapterFactory.K8sDynamicModelsCreator.class
                .equals(client.getJSON().getGson()
                    .getAdapter(K8sDynamicModels.class).getClass());
    }

    /**
     * Gets the group.
     *
     * @return the group
     */
    public String group() {
        return group;
    }

    /**
     * Gets the version.
     *
     * @return the version
     */
    public String version() {
        return version;
    }

    /**
     * Gets the kind.
     *
     * @return the kind
     */
    public String kind() {
        return kind;
    }

    /**
     * Gets the plural.
     *
     * @return the plural
     */
    public String plural() {
        return plural;
    }

    /**
     * Gets the namespace.
     *
     * @return the namespace
     */
    public String namespace() {
        return namespace;
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Delete the Kubernetes object.
     *
     * @throws ApiException the API exception
     */
    public void delete() throws ApiException {
        var result = api.delete(namespace, name);
        if (result.isSuccess()
            || result.getHttpStatusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
            return;
        }
        result.throwsApiException();
    }

    /**
     * Retrieves and returns the current state of the object.
     *
     * @return the object's state
     * @throws ApiException the api exception
     */
    public Optional<O> model() throws ApiException {
        return K8s.optional(api.get(namespace, name));
    }

    /**
     * Updates the object's status.
     *
     * @param object the current state of the object (passed to `status`)
     * @param status function that returns the new status
     * @return the updated model or empty if not successful
     * @throws ApiException the api exception
     */
    public Optional<O> updateStatus(O object,
            Function<O, Object> status) throws ApiException {
        return K8s.optional(api.updateStatus(object, status));
    }

    /**
     * Updates the status.
     *
     * @param status the status
     * @return the kubernetes api response
     * the updated model or empty if not successful
     * @throws ApiException the api exception
     */
    public Optional<O> updateStatus(Function<O, Object> status)
            throws ApiException {
        return updateStatus(
            api.get(namespace, name).throwsApiException().getObject(), status);
    }

    /**
     * Patch the object.
     *
     * @param patchType the patch type
     * @param patch the patch
     * @param options the options
     * @return the kubernetes api response
     * @throws ApiException the api exception
     */
    public Optional<O> patch(String patchType, V1Patch patch,
            PatchOptions options) throws ApiException {
        return K8s
            .optional(api.patch(namespace, name, patchType, patch, options));
    }

    /**
     * Patch the object using default options.
     *
     * @param patchType the patch type
     * @param patch the patch
     * @return the kubernetes api response
     * @throws ApiException the api exception
     */
    public Optional<O>
            patch(String patchType, V1Patch patch) throws ApiException {
        PatchOptions opts = new PatchOptions();
        return patch(patchType, patch, opts);
    }

    /**
     * A supplier for generic stubs.
     *
     * @param <O> the object type
     * @param <L> the object list type
     * @param <R> the result type
     */
    public interface GenericSupplier<O extends KubernetesObject,
            L extends KubernetesListObject, R extends K8sGenericStub<O, L>> {

        /**
         * Gets a new stub.
         *
         * @param objectClass the object class
         * @param objectListClass the object list class
         * @param client the client
         * @param context the API resource
         * @param namespace the namespace
         * @param name the name
         * @return the result
         */
        @SuppressWarnings("PMD.UseObjectForClearerAPI")
        R get(Class<O> objectClass, Class<L> objectListClass, K8sClient client,
                APIResource context, String namespace, String name);
    }

    /**
     * A supplier for specific stubs.
     *
     * @param <O> the object type
     * @param <L> the object list type
     * @param <R> the result type
     */
    public interface SpecificSupplier<O extends KubernetesObject,
            L extends KubernetesListObject, R extends K8sGenericStub<O, L>> {

        /**
         * Gets a new stub.
         *
         * @param client the client
         * @param namespace the namespace
         * @param name the name
         * @return the result
         */
        R get(K8sClient client, String namespace, String name);
    }

    @Override
    @SuppressWarnings("PMD.UseLocaleWithCaseConversions")
    public String toString() {
        return (Strings.isNullOrEmpty(group) ? "" : group + "/")
            + version.toUpperCase() + kind + " " + namespace + ":" + name;
    }

}
