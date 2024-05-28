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
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.generic.options.ListOptions;
import java.util.Collection;
import java.util.List;

/**
 * A stub for pods (v1).
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class K8sV1PodStub extends K8sGenericStub<V1Pod, V1PodList> {

    /** The pods' context. */
    public static final APIResource CONTEXT
        = new APIResource("", List.of("v1"), "v1", "Pod", true, "pods", "pod");

    /**
     * Instantiates a new stub.
     *
     * @param client the client
     * @param namespace the namespace
     * @param name the name
     */
    protected K8sV1PodStub(K8sClient client, String namespace, String name) {
        super(V1Pod.class, V1PodList.class, client, CONTEXT, namespace, name);
    }

    /**
     * Gets the stub for the given namespace and name.
     *
     * @param client the client
     * @param namespace the namespace
     * @param name the name
     * @return the kpod stub
     */
    public static K8sV1PodStub get(K8sClient client, String namespace,
            String name) {
        return new K8sV1PodStub(client, namespace, name);
    }

    /**
     * Get the stubs for the objects in the given namespace that match
     * the criteria from the given options.
     *
     * @param client the client
     * @param namespace the namespace
     * @param options the options
     * @return the collection
     * @throws ApiException the api exception
     */
    public static Collection<K8sV1PodStub> list(K8sClient client,
            String namespace, ListOptions options) throws ApiException {
        return K8sGenericStub.list(V1Pod.class, V1PodList.class, client,
            CONTEXT, namespace, options, (clnt, context, nscp,
                    name) -> new K8sV1PodStub(clnt, nscp, name));
    }
}