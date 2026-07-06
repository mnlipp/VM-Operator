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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.models.V1ListMeta;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a list of Kubernetes objects each of which is
 * represented using a JSON data structure.
 * Some information that is common to all Kubernetes objects,
 * notably the metadata, is made available through the methods
 * defined by {@link KubernetesListObject}.
 */
public class K8sDynamicModelsBase<T extends K8sDynamicModel>
        implements KubernetesListObject {

    private final JsonObject data;
    private final V1ListMeta metadata;
    private final List<T> items;

    /**
     * Initialize the object list using the given JSON data.
     *
     * @param itemClass the item class
     * @param delegate the gson instance to use for extracting structured data
     * @param data the data
     */
    public K8sDynamicModelsBase(Class<T> itemClass, Gson delegate,
            JsonObject data) {
        this.data = data;
        metadata = delegate.fromJson(data.get("metadata"), V1ListMeta.class);
        items = new ArrayList<>();
        for (JsonElement e : data.get("items").getAsJsonArray()) {
            try {
                items.add(itemClass.getConstructor(Gson.class, JsonObject.class)
                    .newInstance(delegate, e.getAsJsonObject()));
            } catch (InstantiationException | IllegalAccessException
                    | IllegalArgumentException | InvocationTargetException
                    | NoSuchMethodException | SecurityException exc) {
                throw new IllegalArgumentException(exc);
            }
        }
    }

    @Override
    public String getApiVersion() {
        return apiVersion();
    }

    /**
     * Gets the API version. (Abbreviated method name for convenience.)
     *
     * @return the API version
     */
    public String apiVersion() {
        return data.get("apiVersion").getAsString();
    }

    @Override
    public String getKind() {
        return kind();
    }

    /**
     * Gets the kind. (Abbreviated method name for convenience.)
     *
     * @return the kind
     */
    public String kind() {
        return data.get("kind").getAsString();
    }

    @Override
    public V1ListMeta getMetadata() {
        return metadata;
    }

    /**
     * Gets the metadata. (Abbreviated method name for convenience.)
     *
     * @return the metadata
     */
    public V1ListMeta metadata() {
        return metadata;
    }

    /**
     * Returns the JSON representation of this object.
     *
     * @return the JOSN representation
     */
    public JsonObject data() {
        return data;
    }

    @Override
    public List<T> getItems() {
        return items;
    }

    /**
     * Sets the api version.
     *
     * @param apiVersion the new api version
     */
    public void setApiVersion(String apiVersion) {
        data.addProperty("apiVersion", apiVersion);
    }

    /**
     * Sets the kind.
     *
     * @param kind the new kind
     */
    public void setKind(String kind) {
        data.addProperty("kind", kind);
    }

    /**
     * Sets the metadata.
     *
     * @param objectMeta the new metadata
     */
    public void setMetadata(V1ListMeta objectMeta) {
        data.add("metadata",
            Configuration.getDefaultApiClient().getJSON().getGson()
                .toJsonTree(objectMeta));
    }

    @Override
    public int hashCode() {
        return Objects.hash(data);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        K8sDynamicModelsBase<?> other = (K8sDynamicModelsBase<?>) obj;
        return Objects.equals(data, other.data);
    }
}
