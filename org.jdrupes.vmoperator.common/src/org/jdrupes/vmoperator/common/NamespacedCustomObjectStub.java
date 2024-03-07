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

import com.google.gson.JsonObject;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.ApisApi;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1APIGroup;
import io.kubernetes.client.openapi.models.V1GroupVersionForDiscovery;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesListObject;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import io.kubernetes.client.util.generic.options.PatchOptions;
import java.util.Optional;
import java.util.function.Function;

/**
 * A stub for namespaced custom objects.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class NamespacedCustomObjectStub {
    private final CustomObjectsApi api;
    private final String group;
    private final String version;
    private final String namespace;
    private final String plural;
    private final String name;
    private final GenericKubernetesApi<DynamicKubernetesObject,
            DynamicKubernetesListObject> gkApi;

    /**
     * Instantiates a new namespaced custom object stub.
     *
     * @param api the api
     * @param group the group
     * @param version the version
     * @param namespace the namespace
     * @param plural the plural
     * @param name the name
     */
    protected NamespacedCustomObjectStub(CustomObjectsApi api, String group,
            String version, String namespace, String plural, String name) {
        this.api = api;
        this.group = group;
        this.version = version;
        this.namespace = namespace;
        this.plural = plural;
        this.name = name;
        gkApi = new GenericKubernetesApi<>(DynamicKubernetesObject.class,
            DynamicKubernetesListObject.class, group, version, plural, api);
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
     * Gets the namespace.
     *
     * @return the namespace
     */
    public String namespace() {
        return namespace;
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
     * Gets the name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Get a namespaced custom object stub.
     *
     * @param client the client
     * @param group the group
     * @param kind the kind
     * @param namespace the namespace
     * @param name the name
     * @return the dynamic kubernetes api
     * @throws ApiException the api exception
     */
    @SuppressWarnings({ "PMD.AvoidBranchingStatementAsLastInLoop",
        "PMD.AvoidInstantiatingObjectsInLoops", "PMD.UseObjectForClearerAPI" })
    public static Optional<NamespacedCustomObjectStub>
            get(ApiClient client, String group, String kind,
                    String namespace, String name) throws ApiException {
        var apis = new ApisApi(client).getAPIVersions();
        var crdVersions = apis.getGroups().stream()
            .filter(g -> g.getName().equals(group)).findFirst()
            .map(V1APIGroup::getVersions).stream().flatMap(l -> l.stream())
            .map(V1GroupVersionForDiscovery::getVersion).toList();
        var coa = new CustomObjectsApi(client);
        for (var crdVersion : crdVersions) {
            var crdApiRes = coa.getAPIResources(group, crdVersion)
                .getResources().stream().filter(r -> kind.equals(r.getKind()))
                .findFirst();
            if (crdApiRes.isEmpty()) {
                continue;
            }
            return Optional
                .of(new NamespacedCustomObjectStub(coa, group, crdVersion,
                    namespace, crdApiRes.get().getName(), name));
        }
        return Optional.empty();
    }

    /**
     * Retrieves and returns the current state of the object.
     *
     * @return the dynamic kubernetes object
     * @throws ApiException the api exception
     */
    public DynamicKubernetesObject object() throws ApiException {
        var call = api.getNamespacedCustomObjectCall(group, version,
            namespace, plural, name, null);
        var data = api.getApiClient().<JsonObject> execute(call,
            JsonObject.class).getData();
        return new DynamicKubernetesObject(data);
    }

    /**
     * Updates the object's status.
     *
     * @param object the current state of the object (passed to `status`)
     * @param status function that returns the new status
     * @return the kubernetes api response
     * @throws ApiException the api exception
     */
    public KubernetesApiResponse<DynamicKubernetesObject>
            updateStatus(DynamicKubernetesObject object,
                    Function<DynamicKubernetesObject, Object> status)
                    throws ApiException {
        return gkApi.updateStatus(object, status).throwsApiException();
    }

    /**
     * Updates the status.
     *
     * @param status the status
     * @return the kubernetes api response
     * @throws ApiException the api exception
     */
    public KubernetesApiResponse<DynamicKubernetesObject>
            updateStatus(Function<DynamicKubernetesObject, Object> status)
                    throws ApiException {
        return updateStatus(object(), status);
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
    public KubernetesApiResponse<DynamicKubernetesObject> patch(
            String patchType, V1Patch patch, PatchOptions options)
            throws ApiException {
        return gkApi.patch(namespace, name, patchType, patch, options)
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
    public KubernetesApiResponse<DynamicKubernetesObject>
            patch(String patchType, V1Patch patch) throws ApiException {
        PatchOptions opts = new PatchOptions();
        return patch(patchType, patch, opts);
    }
}