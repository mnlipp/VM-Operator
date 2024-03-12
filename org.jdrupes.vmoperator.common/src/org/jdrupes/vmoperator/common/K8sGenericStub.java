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
import io.kubernetes.client.Discovery;
import io.kubernetes.client.apimachinery.GroupVersionKind;
import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.Strings;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.options.PatchOptions;
import java.util.Optional;
import java.util.function.Function;

/**
 * A stub for namespaced custom objects.
 *
 * @param <O> the generic type
 * @param <L> the generic type
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class K8sGenericStub<O extends KubernetesObject,
        L extends KubernetesListObject> {
    private final GenericKubernetesApi<O, L> api;
    private final String group;
    private final String version;
    private final String kind;
    private final String plural;
    private final String namespace;
    private final String name;

    /**
     * Instantiates a new namespaced custom object stub.
     * @param group the group
     * @param version the version
     * @param plural the plural
     * @param namespace the namespace
     * @param name the name
     */
    protected K8sGenericStub(Class<O> objectClass, Class<L> objectListClass,
            ApiClient client, String group, String version, String kind,
            String plural, String namespace, String name) {
        this.group = group;
        this.version = version;
        this.kind = kind;
        this.plural = plural;
        this.namespace = namespace;
        this.name = name;

        Gson gson = client.getJSON().getGson();
        if (!checkAdapters(client)) {
            client.getJSON().setGson(gson.newBuilder()
                .registerTypeAdapterFactory(
                    new K8sObjectStateTypeAdapterFactory())
                .create());
        }
        api = new GenericKubernetesApi<>(objectClass,
            objectListClass, group, version, plural, client);
    }

    private boolean checkAdapters(ApiClient client) {
        return K8sObjectStateTypeAdapterFactory.K8sObjectStateCreator.class
            .equals(client.getJSON().getGson().getAdapter(K8sObjectState.class)
                .getClass())
            && K8sObjectStateTypeAdapterFactory.K8sObjectStatesCreator.class
                .equals(client.getJSON().getGson()
                    .getAdapter(K8sObjectStates.class).getClass());
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
     * Get a namespaced object stub. If the version in parameter
     * `gvk` is an empty string, the stub refers to the first object with
     * matching group and kind. 
     *
     * @param <O> the generic type
     * @param <L> the generic type
     * @param <R> the generic type
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
        "PMD.AvoidInstantiatingObjectsInLoops", "PMD.UseObjectForClearerAPI" })
    public static <O extends KubernetesObject, L extends KubernetesListObject,
            R extends K8sGenericStub<O, L>>
            Optional<R> get(Class<O> objectClass, Class<L> objectListClass,
                    ApiClient client, GroupVersionKind gvk, String namespace,
                    String name, StubSupplier<O, L, R> provider)
                    throws ApiException {
        var group = gvk.getGroup();
        var kind = gvk.getKind();
        var version = gvk.getVersion();
        var apiMatch = new Discovery(client).findAll().stream()
            .filter(r -> r.getGroup().equals(group) && r.getKind().equals(kind)
                && (Strings.isNullOrEmpty(version)
                    || r.getVersions().contains(version)))
            .findFirst();
        if (apiMatch.isEmpty()) {
            return Optional.empty();
        }
        var apiRes = apiMatch.get();
        var finalVersion = Strings.isNullOrEmpty(version)
            ? apiRes.getVersions().get(0)
            : version;
        return Optional
            .of(provider.get(objectClass, objectListClass,
                client, group, finalVersion, apiRes.getKind(),
                apiRes.getResourcePlural(), namespace, name));
    }

    /**
     * Retrieves and returns the current state of the object.
     *
     * @return the object's state
     * @throws ApiException the api exception
     */
    public O state() throws ApiException {
        var response = api.get(namespace, name).throwsApiException();
        return response.getObject();
    }

    /**
     * Updates the object's status.
     *
     * @param object the current state of the object (passed to `status`)
     * @param status function that returns the new status
     * @return the kubernetes api response
     * @throws ApiException the api exception
     */
    public KubernetesApiResponse<O> updateStatus(O object,
            Function<O, Object> status) throws ApiException {
        return api.updateStatus(object, status).throwsApiException();
    }

    /**
     * Updates the status.
     *
     * @param status the status
     * @return the kubernetes api response
     * @throws ApiException the api exception
     */
    public KubernetesApiResponse<O> updateStatus(Function<O, Object> status)
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
    public KubernetesApiResponse<O> patch(String patchType, V1Patch patch,
            PatchOptions options) throws ApiException {
        return api.patch(namespace, name, patchType, patch, options)
            .throwsApiException();
    }

    /**
     * Patch the object using default options.
     *
     * @param patchType the patch type
     * @param patch the patch
     * @return the kubernetes api response
     * @throws ApiException the api exception
     */
    public KubernetesApiResponse<O>
            patch(String patchType, V1Patch patch) throws ApiException {
        PatchOptions opts = new PatchOptions();
        return patch(patchType, patch, opts);
    }

    /**
     * A supplier for Stubs.
     *
     * @param <O> the object type
     * @param <L> the object list type
     * @param <R> the result type
     */
    public interface StubSupplier<O extends KubernetesObject,
            L extends KubernetesListObject, R extends K8sGenericStub<O, L>> {

        /**
         * Gets the stub.
         *
         * @param objectClass the object class
         * @param objectListClass the object list class
         * @param client the client
         * @param group the group
         * @param version the version
         * @param kind the kind
         * @param plural the plural
         * @param namespace the namespace
         * @param name the name
         * @return the r
         */
        @SuppressWarnings("PMD.UseObjectForClearerAPI")
        R get(Class<O> objectClass, Class<L> objectListClass,
                ApiClient client, String group, String version, String kind,
                String plural, String namespace, String name);
    }
}
