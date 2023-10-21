/*
 * VM-Operator
 * Copyright (C) 2023 Michael N. Lipp
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

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.ApisApi;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1APIGroup;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1GroupVersionForDiscovery;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.options.DeleteOptions;
import io.kubernetes.client.util.generic.options.PatchOptions;
import java.util.Optional;

/**
 * Helpers for K8s API.
 */
@SuppressWarnings({ "PMD.ShortClassName", "PMD.UseUtilityClass",
    "PMD.DataflowAnomalyAnalysis" })
public class K8s {

    /**
     * Given a groupVersion, returns only the version.
     *
     * @param groupVersion the group version
     * @return the string
     */
    public static String version(String groupVersion) {
        return groupVersion.substring(groupVersion.lastIndexOf('/') + 1);
    }

    /**
     * Get PVC API.
     *
     * @param client the client
     * @return the generic kubernetes api
     */
    public static GenericKubernetesApi<V1PersistentVolumeClaim,
            V1PersistentVolumeClaimList> pvcApi(ApiClient client) {
        return new GenericKubernetesApi<>(V1PersistentVolumeClaim.class,
            V1PersistentVolumeClaimList.class, "", "v1",
            "persistentvolumeclaims", client);
    }

    /**
     * Get config map API.
     *
     * @param client the client
     * @return the generic kubernetes api
     */
    public static GenericKubernetesApi<V1ConfigMap,
            V1ConfigMapList> cmApi(ApiClient client) {
        return new GenericKubernetesApi<>(V1ConfigMap.class,
            V1ConfigMapList.class, "", "v1", "configmaps", client);
    }

    /**
     * Get pod API.
     *
     * @param client the client
     * @return the generic kubernetes api
     */
    public static GenericKubernetesApi<V1Pod, V1PodList>
            podApi(ApiClient client) {
        return new GenericKubernetesApi<>(V1Pod.class, V1PodList.class, "",
            "v1", "pods", client);
    }

    /**
     * Get the API for a custom resource.
     *
     * @param client the client
     * @param group the group
     * @param kind the kind
     * @param namespace the namespace
     * @param name the name
     * @return the dynamic kubernetes api
     * @throws ApiException the api exception
     */
    @SuppressWarnings("PMD.UseObjectForClearerAPI")
    public static Optional<DynamicKubernetesApi> crApi(ApiClient client,
            String group, String kind, String namespace, String name)
            throws ApiException {
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
            @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
            var crApi = new DynamicKubernetesApi(group,
                crdVersion, crdApiRes.get().getName(), client);
            var customResource = crApi.get(namespace, name);
            if (customResource.isSuccess()) {
                return Optional.of(crApi);
            }
        }
        return Optional.empty();
    }

    /**
     * Get an object from its metadata.
     *
     * @param <T> the generic type
     * @param <LT> the generic type
     * @param api the api
     * @param meta the meta
     * @return the object
     */
    public static <T extends KubernetesObject, LT extends KubernetesListObject>
            Optional<T>
            get(GenericKubernetesApi<T, LT> api, V1ObjectMeta meta) {
        var response = api.get(meta.getNamespace(), meta.getName());
        if (response.isSuccess()) {
            return Optional.of(response.getObject());
        }
        return Optional.empty();
    }

    /**
     * Delete an object.
     *
     * @param <T> the generic type
     * @param <LT> the generic type
     * @param api the api
     * @param object the object
     */
    public static <T extends KubernetesObject, LT extends KubernetesListObject>
            void delete(GenericKubernetesApi<T, LT> api, T object)
                    throws ApiException {
        api.delete(object.getMetadata().getNamespace(),
            object.getMetadata().getName()).throwsApiException();
    }

    /**
     * Delete an object.
     *
     * @param <T> the generic type
     * @param <LT> the generic type
     * @param api the api
     * @param object the object
     */
    public static <T extends KubernetesObject, LT extends KubernetesListObject>
            void delete(GenericKubernetesApi<T, LT> api, T object,
                    DeleteOptions options) throws ApiException {
        api.delete(object.getMetadata().getNamespace(),
            object.getMetadata().getName(), options).throwsApiException();
    }

    /**
     * Apply the given patch data.
     *
     * @param <T> the generic type
     * @param <LT> the generic type
     * @param api the api
     * @param existing the existing
     * @param update the update
     * @throws ApiException the api exception
     */
    public static <T extends KubernetesObject, LT extends KubernetesListObject>
            T apply(GenericKubernetesApi<T, LT> api, T existing, String update)
                    throws ApiException {
        PatchOptions opts = new PatchOptions();
        opts.setForce(true);
        opts.setFieldManager("kubernetes-java-kubectl-apply");
        var response = api.patch(existing.getMetadata().getNamespace(),
            existing.getMetadata().getName(), V1Patch.PATCH_FORMAT_APPLY_YAML,
            new V1Patch(update), opts).throwsApiException();
        return response.getObject();
    }

}
