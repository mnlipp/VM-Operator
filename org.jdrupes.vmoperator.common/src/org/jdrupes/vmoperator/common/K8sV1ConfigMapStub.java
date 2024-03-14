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
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import java.util.List;

/**
 * A stub for config maps (v1).
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class K8sV1ConfigMapStub
        extends K8sGenericStub<V1ConfigMap, V1ConfigMapList> {

    /**
     * Instantiates a new stub.
     *
     * @param client the client
     * @param namespace the namespace
     * @param name the name
     */
    protected K8sV1ConfigMapStub(K8sClient client, String namespace,
            String name) {
        super(V1ConfigMap.class, V1ConfigMapList.class, client,
            new APIResource("", List.of("v1"), "v1", "ConfigMap", true,
                "configmaps", "configmap"),
            namespace, name);
    }

    /**
     * Gets the stub for the given namespace and name.
     *
     * @param client the client
     * @param namespace the namespace
     * @param name the name
     * @return the config map stub
     */
    public static K8sV1ConfigMapStub get(K8sClient client, String namespace,
            String name) {
        return new K8sV1ConfigMapStub(client, namespace, name);
    }
}