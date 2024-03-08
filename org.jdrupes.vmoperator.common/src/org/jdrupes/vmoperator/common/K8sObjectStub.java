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

import io.kubernetes.client.Discovery;
import io.kubernetes.client.apimachinery.GroupVersionKind;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.Strings;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import io.kubernetes.client.util.generic.options.PatchOptions;
import java.util.Optional;
import java.util.function.Function;

/**
 * A stub for namespaced custom objects.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class K8sObjectStub {
    private final DynamicKubernetesApi api;
    private final String group;
    private final String version;
    private final String namespace;
    private final String plural;
    private final String name;

    /**
     * Instantiates a new namespaced custom object stub.
     *
     * @param group the group
     * @param version the version
     * @param namespace the namespace
     * @param plural the plural
     * @param name the name
     */
    protected K8sObjectStub(ApiClient client, String group,
            String version, String namespace, String plural, String name) {
        this.group = group;
        this.version = version;
        this.namespace = namespace;
        this.plural = plural;
        this.name = name;
        api = new DynamicKubernetesApi(group, version, plural, client);
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
     * Get a namespaced custom object stub. If the version in parameter
     * `gvk` is an empty string, the stub refers to the first object with
     * matching group and kind. 
     *
     * @param client the client
     * @param gvk the group, version and kind
     * @param namespace the namespace
     * @param name the name
     * @return the dynamic kubernetes api
     * @throws ApiException the api exception
     */
    @SuppressWarnings({ "PMD.AvoidBranchingStatementAsLastInLoop",
        "PMD.AvoidInstantiatingObjectsInLoops", "PMD.UseObjectForClearerAPI" })
    public static Optional<K8sObjectStub>
            get(ApiClient client, GroupVersionKind gvk,
                    String namespace, String name) throws ApiException {
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
            .of(new K8sObjectStub(client, group, finalVersion,
                namespace, apiRes.getResourcePlural(), name));
    }

    /**
     * Retrieves and returns the current state of the object.
     *
     * @return the dynamic kubernetes object
     * @throws ApiException the api exception
     */
    public DynamicKubernetesObject state() throws ApiException {
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
    public KubernetesApiResponse<DynamicKubernetesObject>
            updateStatus(DynamicKubernetesObject object,
                    Function<DynamicKubernetesObject, Object> status)
                    throws ApiException {
        return api.updateStatus(object, status).throwsApiException();
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
        return updateStatus(state(), status);
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
    public KubernetesApiResponse<DynamicKubernetesObject>
            patch(String patchType, V1Patch patch) throws ApiException {
        PatchOptions opts = new PatchOptions();
        return patch(patchType, patch, opts);
    }
}