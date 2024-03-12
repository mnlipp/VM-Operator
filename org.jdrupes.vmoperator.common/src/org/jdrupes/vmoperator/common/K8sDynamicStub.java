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

import io.kubernetes.client.apimachinery.GroupVersionKind;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import java.util.Optional;

/**
 * A stub for namespaced custom objects.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class K8sDynamicStub
        extends K8sGenericStub<K8sObjectState, K8sObjectStates> {

    /**
     * Instantiates a new dynamic stub.
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
     */
    protected K8sDynamicStub(Class<K8sObjectState> objectClass,
            Class<K8sObjectStates> objectListClass, ApiClient client,
            String group, String version, String kind, String plural,
            String namespace, String name) {
        super(objectClass, objectListClass, client, group, version, kind,
            plural, namespace, name);
    }

    /**
     * Get a dynamic object stub. If the version in parameter
     * `gvk` is an empty string, the stub refers to the first object with
     * matching group and kind. 
     *
     * @param client the client
     * @param gvk the group, version and kind
     * @param namespace the namespace
     * @param name the name
     * @return the stub if the object exists
     * @throws ApiException the api exception
     */
    @SuppressWarnings({ "PMD.AvoidBranchingStatementAsLastInLoop",
        "PMD.AvoidInstantiatingObjectsInLoops", "PMD.UseObjectForClearerAPI" })
    public static Optional<K8sDynamicStub> get(ApiClient client,
            GroupVersionKind gvk, String namespace, String name)
            throws ApiException {
        return K8sGenericStub.get(K8sObjectState.class, K8sObjectStates.class,
            client, gvk, namespace, name, K8sDynamicStub::new);
    }
}