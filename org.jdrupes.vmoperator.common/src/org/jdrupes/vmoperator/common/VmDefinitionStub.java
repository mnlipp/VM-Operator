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
import io.kubernetes.client.apimachinery.GroupVersionKind;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.generic.options.ListOptions;
import java.io.Reader;
import java.util.Collection;

/**
 * A stub for namespaced custom objects. It uses a dynamic model
 * (see {@link K8sDynamicModel}) for representing the object's
 * state and can therefore be used for any kind of object, especially
 * custom objects.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class VmDefinitionStub
        extends K8sDynamicStubBase<VmDefinitionModel, VmDefinitionModels> {

    private static DynamicTypeAdapterFactory<VmDefinitionModel,
            VmDefinitionModels> taf = new VmDefintionModelTypeAdapterFactory();

    /**
     * Instantiates a new stub for VM defintions.
     *
     * @param client the client
     * @param context the context
     * @param namespace the namespace
     * @param name the name
     */
    public VmDefinitionStub(K8sClient client, APIResource context,
            String namespace, String name) {
        super(VmDefinitionModel.class, VmDefinitionModels.class, taf, client,
            context, namespace, name);
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
    public static VmDefinitionStub get(K8sClient client,
            GroupVersionKind gvk, String namespace, String name)
            throws ApiException {
        return new VmDefinitionStub(client, apiResource(client, gvk), namespace,
            name);
    }

    /**
     * Get a dynamic object stub.
     *
     * @param client the client
     * @param context the context
     * @param namespace the namespace
     * @param name the name
     * @return the stub if the object exists
     * @throws ApiException the api exception
     */
    @SuppressWarnings({ "PMD.AvoidBranchingStatementAsLastInLoop",
        "PMD.AvoidInstantiatingObjectsInLoops", "PMD.UseObjectForClearerAPI" })
    public static VmDefinitionStub get(K8sClient client,
            APIResource context, String namespace, String name) {
        return new VmDefinitionStub(client, context, namespace, name);
    }

    /**
     * Creates a stub from yaml.
     *
     * @param client the client
     * @param context the context
     * @param yaml the yaml
     * @return the k 8 s dynamic stub
     * @throws ApiException the api exception
     */
    public static VmDefinitionStub createFromYaml(K8sClient client,
            APIResource context, Reader yaml) throws ApiException {
        var model = new VmDefinitionModel(client.getJSON().getGson(),
            K8s.yamlToJson(client, yaml));
        return K8sGenericStub.create(VmDefinitionModel.class,
            VmDefinitionModels.class, client, context, model,
            (c, ns, n) -> new VmDefinitionStub(c, context, ns, n));
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
    public static Collection<VmDefinitionStub> list(K8sClient client,
            APIResource context, String namespace, ListOptions options)
            throws ApiException {
        return K8sGenericStub.list(VmDefinitionModel.class,
            VmDefinitionModels.class, client, context, namespace, options,
            (c, ns, n) -> new VmDefinitionStub(c, context, ns, n));
    }

    /**
     * Get the stubs for the objects in the given namespace.
     *
     * @param client the client
     * @param namespace the namespace
     * @return the collection
     * @throws ApiException the api exception
     */
    public static Collection<VmDefinitionStub> list(K8sClient client,
            APIResource context, String namespace)
            throws ApiException {
        return list(client, context, namespace, new ListOptions());
    }

    /**
     * A factory for creating VmDefinitionModel(s) objects.
     */
    public static class VmDefintionModelTypeAdapterFactory extends
            DynamicTypeAdapterFactory<VmDefinitionModel, VmDefinitionModels> {

        /**
         * Instantiates a new dynamic model type adapter factory.
         */
        public VmDefintionModelTypeAdapterFactory() {
            super(VmDefinitionModel.class, VmDefinitionModels.class);
        }
    }

}