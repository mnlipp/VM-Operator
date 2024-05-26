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
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1NodeList;
import io.kubernetes.client.util.generic.options.ListOptions;
import java.util.Collection;
import java.util.List;

/**
 * A stub for nodes (v1).
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class K8sV1NodeStub extends K8sClusterGenericStub<V1Node, V1NodeList> {

    public static final APIResource CONTEXT = new APIResource("", List.of("v1"),
        "v1", "Node", true, "nodes", "node");

    /**
     * Instantiates a new stub.
     *
     * @param client the client
     * @param name the name
     */
    protected K8sV1NodeStub(K8sClient client, String name) {
        super(V1Node.class, V1NodeList.class, client, CONTEXT, name);
    }

    /**
     * Gets the stub for the given name.
     *
     * @param client the client
     * @param name the name
     * @return the config map stub
     */
    public static K8sV1NodeStub get(K8sClient client, String name) {
        return new K8sV1NodeStub(client, name);
    }

    /**
     * Get the stubs for the objects that match
     * the criteria from the given options.
     *
     * @param client the client
     * @param options the options
     * @return the collection
     * @throws ApiException the api exception
     */
    public static Collection<K8sV1NodeStub> list(K8sClient client,
            ListOptions options) throws ApiException {
        return K8sClusterGenericStub.list(V1Node.class, V1NodeList.class,
            client, CONTEXT, options, K8sV1NodeStub::getGeneric);
    }

    /**
     * Provide {@link GenericSupplier}.
     */
    @SuppressWarnings({ "PMD.UnusedFormalParameter",
        "PMD.UnusedPrivateMethod" })
    private static K8sV1NodeStub getGeneric(Class<V1Node> objectClass,
            Class<V1NodeList> objectListClass, K8sClient client,
            APIResource context, String name) {
        return new K8sV1NodeStub(client, name);
    }

}