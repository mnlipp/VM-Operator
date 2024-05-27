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
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.util.generic.options.ListOptions;
import java.util.Collection;
import java.util.List;
import org.jdrupes.vmoperator.common.K8sGenericStub.GenericSupplier;

/**
 * A stub for secrets (v1).
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class K8sV1ServiceStub extends K8sGenericStub<V1Service, V1ServiceList> {

    public static final APIResource CONTEXT = new APIResource("", List.of("v1"),
        "v1", "Service", true, "services", "service");

    /**
     * Instantiates a new stub.
     *
     * @param client the client
     * @param namespace the namespace
     * @param name the name
     */
    protected K8sV1ServiceStub(K8sClient client, String namespace,
            String name) {
        super(V1Service.class, V1ServiceList.class, client, CONTEXT, namespace,
            name);
    }

    /**
     * Gets the stub for the given namespace and name.
     *
     * @param client the client
     * @param namespace the namespace
     * @param name the name
     * @return the config map stub
     */
    public static K8sV1ServiceStub get(K8sClient client, String namespace,
            String name) {
        return new K8sV1ServiceStub(client, namespace, name);
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
    public static Collection<K8sV1ServiceStub> list(K8sClient client,
            String namespace, ListOptions options) throws ApiException {
        return K8sGenericStub.list(V1Service.class, V1ServiceList.class, client,
            CONTEXT, namespace, options, K8sV1ServiceStub::getGeneric);
    }

    /**
     * Provide {@link GenericSupplier}.
     */
    @SuppressWarnings({ "PMD.UnusedFormalParameter",
        "PMD.UnusedPrivateMethod" })
    private static K8sV1ServiceStub getGeneric(Class<V1Service> objectClass,
            Class<V1ServiceList> objectListClass, K8sClient client,
            APIResource context, String namespace, String name) {
        return new K8sV1ServiceStub(client, namespace, name);
    }

}