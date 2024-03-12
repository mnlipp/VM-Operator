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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a list of Kubernetes objects states.
 */
public class K8sObjectStates implements KubernetesListObject {

    private final JsonObject data;
    private final V1ListMeta metadata;
    private final List<K8sObjectState> items;

    /**
     * Initialize the object states using the given JSON data.
     *
     * @param delegate the delegate
     * @param data the data
     */
    public K8sObjectStates(Gson delegate, JsonObject data) {
        this.data = data;
        metadata = delegate.fromJson(data.get("metadata"), V1ListMeta.class);
        items = new ArrayList<>();
        for (JsonElement e : data.get("items").getAsJsonArray()) {
            items.add(new K8sObjectState(delegate, e.getAsJsonObject()));
        }
    }

    /**
     * Returns the JSON representation of this object.
     *
     * @return the json object
     */
    public JsonObject data() {
        return data;
    }

    @Override
    public V1ListMeta getMetadata() {
        return metadata;
    }

    @Override
    public String getApiVersion() {
        return this.data.get("apiVersion").getAsString();
    }

    @Override
    public String getKind() {
        return this.data.get("kind").getAsString();
    }

    @Override
    public List<K8sObjectState> getItems() {
        return items;
    }

    /**
     * Sets the api version.
     *
     * @param apiVersion the new api version
     */
    public void setApiVersion(String apiVersion) {
        this.data.addProperty("apiVersion", apiVersion);
    }

    /**
     * Sets the kind.
     *
     * @param kind the new kind
     */
    public void setKind(String kind) {
        this.data.addProperty("kind", kind);
    }

    /**
     * Sets the metadata.
     *
     * @param objectMeta the new metadata
     */
    public void setMetadata(V1ListMeta objectMeta) {
        this.data.add("metadata",
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
        K8sObjectStates other = (K8sObjectStates) obj;
        return Objects.equals(data, other.data);
    }
}
