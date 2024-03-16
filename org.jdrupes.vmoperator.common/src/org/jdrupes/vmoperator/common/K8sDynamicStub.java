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

import com.google.gson.Gson;
import io.kubernetes.client.Discovery.APIResource;
import io.kubernetes.client.apimachinery.GroupVersionKind;
import io.kubernetes.client.openapi.ApiClient;
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
public class K8sDynamicStub
        extends K8sGenericStub<K8sDynamicModel, K8sDynamicModels> {

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
    public K8sDynamicStub(Class<K8sDynamicModel> objectClass,
            Class<K8sDynamicModels> objectListClass, K8sClient client,
            APIResource context, String namespace, String name) {
        super(objectClass, objectListClass, client, context, namespace, name);

        // Make sure that we have an adapter for our type
        Gson gson = client.getJSON().getGson();
        if (!checkAdapters(client)) {
            client.getJSON().setGson(gson.newBuilder()
                .registerTypeAdapterFactory(
                    new K8sDynamicModelTypeAdapterFactory())
                .create());
        }
    }

    private boolean checkAdapters(ApiClient client) {
        return K8sDynamicModelTypeAdapterFactory.K8sDynamicModelCreator.class
            .equals(client.getJSON().getGson().getAdapter(K8sDynamicModel.class)
                .getClass())
            && K8sDynamicModelTypeAdapterFactory.K8sDynamicModelsCreator.class
                .equals(client.getJSON().getGson()
                    .getAdapter(K8sDynamicModels.class).getClass());
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
    public static K8sDynamicStub get(K8sClient client,
            GroupVersionKind gvk, String namespace, String name)
            throws ApiException {
        return K8sGenericStub.get(K8sDynamicModel.class, K8sDynamicModels.class,
            client, gvk, namespace, name, K8sDynamicStub::new);
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
    public static K8sDynamicStub get(K8sClient client,
            APIResource context, String namespace, String name) {
        return K8sGenericStub.get(K8sDynamicModel.class, K8sDynamicModels.class,
            client, context, namespace, name, K8sDynamicStub::new);
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
    public static K8sDynamicStub createFromYaml(K8sClient client,
            APIResource context, Reader yaml) throws ApiException {
        var model = new K8sDynamicModel(client.getJSON().getGson(),
            K8s.yamlToJson(client, yaml));
        return K8sGenericStub.create(K8sDynamicModel.class,
            K8sDynamicModels.class, client, context, model,
            K8sDynamicStub::new);
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
    public static Collection<K8sDynamicStub> list(K8sClient client,
            APIResource context, String namespace, ListOptions options)
            throws ApiException {
        return K8sGenericStub.list(K8sDynamicModel.class,
            K8sDynamicModels.class, client, context, namespace, options,
            K8sDynamicStub::new);
    }

    /**
     * Get the stubs for the objects in the given namespace.
     *
     * @param client the client
     * @param namespace the namespace
     * @return the collection
     * @throws ApiException the api exception
     */
    public static Collection<K8sDynamicStub> list(K8sClient client,
            APIResource context, String namespace)
            throws ApiException {
        return list(client, context, namespace, new ListOptions());
    }

}