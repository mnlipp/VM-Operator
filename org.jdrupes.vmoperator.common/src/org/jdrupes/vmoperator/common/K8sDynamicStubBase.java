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

/**
 * A stub for namespaced custom objects. It uses a dynamic model
 * (see {@link K8sDynamicModel}) for representing the object's
 * state and can therefore be used for any kind of object, especially
 * custom objects.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public abstract class K8sDynamicStubBase<O extends K8sDynamicModel,
        L extends K8sDynamicModelsBase<O>> extends K8sGenericStub<O, L> {

    /**
     * Instantiates a new dynamic stub.
     *
     * @param objectClass the object class
     * @param objectListClass the object list class
     * @param client the client
     * @param context the context
     * @param namespace the namespace
     * @param name the name
     */
    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
    public K8sDynamicStubBase(Class<O> objectClass,
            Class<L> objectListClass, DynamicTypeAdapterFactory<O, L> taf,
            K8sClient client, APIResource context, String namespace,
            String name) {
        super(objectClass, objectListClass, client, context, namespace, name);
        taf.register(client);
    }
}