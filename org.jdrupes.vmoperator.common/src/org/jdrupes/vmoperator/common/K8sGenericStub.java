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
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import io.kubernetes.client.util.generic.options.GetOptions;
import io.kubernetes.client.util.generic.options.ListOptions;
import io.kubernetes.client.util.generic.options.PatchOptions;
import io.kubernetes.client.util.generic.options.UpdateOptions;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
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
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis", "PMD.TooManyMethods" })
public class K8sGenericStub<O extends KubernetesObject,
        L extends KubernetesListObject> {
    protected final K8sClient client;
    private final GenericKubernetesApi<O, L> api;
    protected final APIResource context;
    protected final String namespace;
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
     * @param namespace the namespace
     * @param name the name
     */
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    protected K8sGenericStub(Class<O> objectClass, Class<L> objectListClass,
            K8sClient client, APIResource context, String namespace,
            String name) {
        this.client = client;
        this.namespace = namespace;
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
            if (testApi.get(namespace, name, mdOpts)
                .isSuccess()) {
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
     * Updates the object's status. Does not retry in case of conflict.
     *
     * @param object the current state of the object (passed to `status`)
     * @param updater function that returns the new status
     * @return the updated model or empty if the object was not found
     * @throws ApiException the api exception
     */
    @SuppressWarnings("PMD.AssignmentInOperand")
    public Optional<O> updateStatus(O object, Function<O, Object> updater)
            throws ApiException {
        return K8s.optional(api.updateStatus(object, updater));
    }

    /**
     * Updates the status of the given object. In case of conflict,
     * get the current version of the object and tries again. Retries
     * up to `retries` times.
     *
     * @param updater the function updating the status
     * @param current the current state of the object, used for the first
     * attempt to update
     * @param retries the retries in case of conflict
     * @return the updated model or empty if the object was not found
     * @throws ApiException the api exception
     */
    @SuppressWarnings({ "PMD.AssignmentInOperand", "PMD.UnusedAssignment" })
    public Optional<O> updateStatus(Function<O, Object> updater, O current,
            int retries) throws ApiException {
        while (true) {
            try {
                if (current == null) {
                    current = api.get(namespace, name)
                        .throwsApiException().getObject();
                }
                return updateStatus(current, updater);
            } catch (ApiException e) {
                if (HttpURLConnection.HTTP_CONFLICT != e.getCode()
                    || retries-- <= 0) {
                    throw e;
                }
                // Get current version for new attempt
                current = null;
            }
        }
    }

    /**
     * Gets the object and updates the status. In case of conflict, retries
     * up to `retries` times.
     *
     * @param updater the function updating the status
     * @param retries the retries in case of conflict
     * @return the updated model or empty if the object was not found
     * @throws ApiException the api exception
     */
    @SuppressWarnings({ "PMD.AssignmentInOperand", "PMD.UnusedAssignment" })
    public Optional<O> updateStatus(Function<O, Object> updater, int retries)
            throws ApiException {
        return updateStatus(updater, null, retries);
    }

    /**
     * Updates the status of the given object. In case of conflict,
     * get the current version of the object and tries again. Retries
     * up to `retries` times.
     *
     * @param updater the function updating the status
     * @param current the current
     * @return the kubernetes api response
     * the updated model or empty if not successful
     * @throws ApiException the api exception
     */
    public Optional<O> updateStatus(Function<O, Object> updater, O current)
            throws ApiException {
        return updateStatus(updater, current, 16);
    }

    /**
     * Updates the status. In case of conflict, retries up to 16 times.
     *
     * @param updater the function updating the status
     * @return the kubernetes api response
     * the updated model or empty if not successful
     * @throws ApiException the api exception
     */
    public Optional<O> updateStatus(Function<O, Object> updater)
            throws ApiException {
        return updateStatus(updater, null);
    }

    /**
     * Patch the object.
     *
     * @param patchType the patch type
     * @param patch the patch
     * @param options the options
     * @return the kubernetes api response if successful
     * @throws ApiException the api exception
     */
    public Optional<O> patch(String patchType, V1Patch patch,
            PatchOptions options) throws ApiException {
        return K8s
            .optional(api.patch(namespace, name, patchType, patch, options)
                .throwsApiException());
    }

    /**
     * Patch the object using default options.
     *
     * @param patchType the patch type
     * @param patch the patch
     * @return the kubernetes api response if successful
     * @throws ApiException the api exception
     */
    public Optional<O>
            patch(String patchType, V1Patch patch) throws ApiException {
        PatchOptions opts = new PatchOptions();
        return patch(patchType, patch, opts);
    }

    /**
     * Apply the given definition. 
     *
     * @param def the def
     * @return the kubernetes api response if successful
     * @throws ApiException the api exception
     */
    public Optional<O> apply(DynamicKubernetesObject def) throws ApiException {
        PatchOptions opts = new PatchOptions();
        opts.setForce(true);
        opts.setFieldManager("kubernetes-java-kubectl-apply");
        return patch(V1Patch.PATCH_FORMAT_APPLY_YAML,
            new V1Patch(client.getJSON().serialize(def)), opts);
    }

    /**
     * Update the object.
     *
     * @param object the object
     * @return the kubernetes api response
     * @throws ApiException the api exception
     */
    public KubernetesApiResponse<O> update(O object) throws ApiException {
        return api.update(object).throwsApiException();
    }

    /**
     * Update the object.
     *
     * @param object the object
     * @param options the options
     * @return the kubernetes api response
     * @throws ApiException the api exception
     */
    public KubernetesApiResponse<O> update(O object, UpdateOptions options)
            throws ApiException {
        return api.update(object, options).throwsApiException();
    }

    /**
     * A supplier for generic stubs.
     *
     * @param <O> the object type
     * @param <L> the object list type
     * @param <R> the result type
     */
    @FunctionalInterface
    public interface GenericSupplier<O extends KubernetesObject,
            L extends KubernetesListObject, R extends K8sGenericStub<O, L>> {

        /**
         * Gets a new stub.
         *
         * @param client the client
         * @param namespace the namespace
         * @param name the name
         * @return the result
         */
        @SuppressWarnings("PMD.UseObjectForClearerAPI")
        R get(K8sClient client, String namespace, String name);
    }

    @Override
    @SuppressWarnings("PMD.UseLocaleWithCaseConversions")
    public String toString() {
        return (Strings.isNullOrEmpty(group()) ? "" : group() + "/")
            + version().toUpperCase() + kind() + " " + namespace + ":" + name;
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
        return provider.get(client, model.getMetadata().getNamespace(),
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
                    ListOptions options, GenericSupplier<O, L, R> provider)
                    throws ApiException {
        var result = new ArrayList<R>();
        for (var version : candidateVersions(context)) {
            @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
            var api = new GenericKubernetesApi<>(objectClass, objectListClass,
                context.getGroup(), version, context.getResourcePlural(),
                client);
            var objs = api.list(namespace, options).throwsApiException();
            for (var item : objs.getObject().getItems()) {
                result.add(provider.get(client, namespace,
                    item.getMetadata().getName()));
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

    /**
     * Api resource.
     *
     * @param client the client
     * @param gvk the gvk
     * @return the API resource
     * @throws ApiException the api exception
     */
    public static APIResource apiResource(K8sClient client,
            GroupVersionKind gvk) throws ApiException {
        var context = K8s.context(client, gvk.getGroup(), gvk.getVersion(),
            gvk.getKind());
        if (context.isEmpty()) {
            throw new ApiException("No known API for " + gvk.getGroup()
                + "/" + gvk.getVersion() + " " + gvk.getKind());
        }
        return context.get();
    }

}
