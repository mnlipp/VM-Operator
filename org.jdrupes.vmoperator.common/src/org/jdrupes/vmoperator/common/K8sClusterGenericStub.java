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
import io.kubernetes.client.apimachinery.GroupVersionKind;
import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.Strings;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.options.GetOptions;
import io.kubernetes.client.util.generic.options.ListOptions;
import io.kubernetes.client.util.generic.options.PatchOptions;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * A stub for cluster scoped objects. This stub provides the
 * functions common to all Kubernetes objects, but uses variables
 * for all types. This class should be used as base class only.
 *
 * @param <O> the generic type
 * @param <L> the generic type
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis",
    "PMD.CouplingBetweenObjects" })
public class K8sClusterGenericStub<O extends KubernetesObject,
        L extends KubernetesListObject> {
    protected final K8sClient client;
    private final GenericKubernetesApi<O, L> api;
    protected final APIResource context;
    protected final String name;

    /**
     * Instantiates a new stub for the object specified. If the object
     * exists in the context specified, the version (see
     * {@link #version()} is bound to the existing object's version.
     * Else the stub is dangling with the version set to the context's
     * preferred version.
     *
     * @param objectClass the object class
     * @param objectListClass the object list class
     * @param client the client
     * @param context the context
     * @param name the name
     */
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    protected K8sClusterGenericStub(Class<O> objectClass,
            Class<L> objectListClass, K8sClient client, APIResource context,
            String name) {
        this.client = client;
        this.name = name;

        // Bind version
        var foundVersion = context.getPreferredVersion();
        GenericKubernetesApi<O, L> testApi = null;
        GetOptions mdOpts
            = new GetOptions().isPartialObjectMetadataRequest(true);
        for (var version : candidateVersions(context)) {
            testApi = new GenericKubernetesApi<>(objectClass, objectListClass,
                context.getGroup(), version, context.getResourcePlural(),
                client);
            if (testApi.get(name, mdOpts).isSuccess()) {
                foundVersion = version;
                break;
            }
        }
        if (foundVersion.equals(context.getPreferredVersion())) {
            this.context = context;
        } else {
            this.context = K8s.preferred(context, foundVersion);
        }

        api = Optional.ofNullable(testApi)
            .orElseGet(() -> new GenericKubernetesApi<>(objectClass,
                objectListClass, group(), version(), plural(), client));
    }

    /**
     * Gets the context.
     *
     * @return the context
     */
    public APIResource context() {
        return context;
    }

    /**
     * Gets the group.
     *
     * @return the group
     */
    public String group() {
        return context.getGroup();
    }

    /**
     * Gets the version.
     *
     * @return the version
     */
    public String version() {
        return context.getPreferredVersion();
    }

    /**
     * Gets the kind.
     *
     * @return the kind
     */
    public String kind() {
        return context.getKind();
    }

    /**
     * Gets the plural.
     *
     * @return the plural
     */
    public String plural() {
        return context.getResourcePlural();
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
        var result = api.delete(name);
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
        return K8s.optional(api.get(name));
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
        return updateStatus(api.get(name).throwsApiException().getObject(),
            status);
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
            .optional(api.patch(name, patchType, patch, options));
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
            L extends KubernetesListObject,
            R extends K8sClusterGenericStub<O, L>> {

        /**
         * Gets a new stub.
         *
         * @param objectClass the object class
         * @param objectListClass the object list class
         * @param client the client
         * @param context the API resource
         * @param name the name
         * @return the result
         */
        @SuppressWarnings("PMD.UseObjectForClearerAPI")
        R get(Class<O> objectClass, Class<L> objectListClass, K8sClient client,
                APIResource context, String name);
    }

    @Override
    @SuppressWarnings("PMD.UseLocaleWithCaseConversions")
    public String toString() {
        return (Strings.isNullOrEmpty(group()) ? "" : group() + "/")
            + version().toUpperCase() + kind() + " " + name;
    }

    /**
     * Get an object stub. If the version in parameter
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
     * @param name the name
     * @param provider the provider
     * @return the stub if the object exists
     * @throws ApiException the api exception
     */
    @SuppressWarnings({ "PMD.AvoidBranchingStatementAsLastInLoop" })
    public static <O extends KubernetesObject, L extends KubernetesListObject,
            R extends K8sClusterGenericStub<O, L>>
            R get(Class<O> objectClass, Class<L> objectListClass,
                    K8sClient client, GroupVersionKind gvk, String name,
                    GenericSupplier<O, L, R> provider) throws ApiException {
        var context = K8s.context(client, gvk.getGroup(), gvk.getVersion(),
            gvk.getKind());
        if (context.isEmpty()) {
            throw new ApiException("No known API for " + gvk.getGroup()
                + "/" + gvk.getVersion() + " " + gvk.getKind());
        }
        return provider.get(objectClass, objectListClass, client, context.get(),
            name);
    }

    /**
     * Get an object stub.
     *
     * @param <O> the object type
     * @param <L> the object list type
     * @param <R> the stub type
     * @param objectClass the object class
     * @param objectListClass the object list class
     * @param client the client
     * @param context the context
     * @param name the name
     * @param provider the provider
     * @return the stub if the object exists
     * @throws ApiException the api exception
     */
    @SuppressWarnings({ "PMD.AvoidBranchingStatementAsLastInLoop",
        "PMD.UseObjectForClearerAPI" })
    public static <O extends KubernetesObject, L extends KubernetesListObject,
            R extends K8sClusterGenericStub<O, L>>
            R get(Class<O> objectClass, Class<L> objectListClass,
                    K8sClient client, APIResource context, String name,
                    GenericSupplier<O, L, R> provider) {
        return provider.get(objectClass, objectListClass, client, context,
            name);
    }

    /**
     * Get an object stub for a newly created object.
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
            R extends K8sClusterGenericStub<O, L>>
            R create(Class<O> objectClass, Class<L> objectListClass,
                    K8sClient client, APIResource context, O model,
                    GenericSupplier<O, L, R> provider) throws ApiException {
        var api = new GenericKubernetesApi<>(objectClass, objectListClass,
            context.getGroup(), context.getPreferredVersion(),
            context.getResourcePlural(), client);
        api.create(model).throwsApiException();
        return provider.get(objectClass, objectListClass, client,
            context, model.getMetadata().getName());
    }

    /**
     * Get the stubs for the objects that match
     * the criteria from the given options.
     *
     * @param <O> the object type
     * @param <L> the object list type
     * @param <R> the stub type
     * @param objectClass the object class
     * @param objectListClass the object list class
     * @param client the client
     * @param context the context
     * @param options the options
     * @param provider the provider
     * @return the collection
     * @throws ApiException the api exception
     */
    public static <O extends KubernetesObject, L extends KubernetesListObject,
            R extends K8sClusterGenericStub<O, L>>
            Collection<R> list(Class<O> objectClass, Class<L> objectListClass,
                    K8sClient client, APIResource context,
                    ListOptions options, GenericSupplier<O, L, R> provider)
                    throws ApiException {
        var result = new ArrayList<R>();
        for (var version : candidateVersions(context)) {
            @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
            var api = new GenericKubernetesApi<>(objectClass, objectListClass,
                context.getGroup(), version, context.getResourcePlural(),
                client);
            var objs = api.list(options).throwsApiException();
            for (var item : objs.getObject().getItems()) {
                result.add(provider.get(objectClass, objectListClass, client,
                    context, item.getMetadata().getName()));
            }
        }
        return result;
    }

    private static List<String> candidateVersions(APIResource context) {
        var result = new LinkedList<>(context.getVersions());
        result.remove(context.getPreferredVersion());
        result.add(0, context.getPreferredVersion());
        return result;
    }

}
