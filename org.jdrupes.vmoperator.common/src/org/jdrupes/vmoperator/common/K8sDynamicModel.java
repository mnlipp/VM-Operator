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
import com.google.gson.JsonObject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;

/**
 * Represents a Kubernetes object using a JSON data structure.
 * Some information that is common to all Kubernetes objects,
 * notably the metadata, is made available through the methods
 * defined by {@link KubernetesObject}.
 */
@SuppressWarnings("PMD.DataClass")
public class K8sDynamicModel implements KubernetesObject {

    private final V1ObjectMeta metadata;
    private final JsonObject data;

    /**
     * Instantiates a new model from the JSON representation.
     *
     * @param delegate the gson instance to use for extracting structured data
     * @param json the JSON
     */
    public K8sDynamicModel(Gson delegate, JsonObject json) {
        this.data = json;
        metadata = delegate.fromJson(data.get("metadata"), V1ObjectMeta.class);
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
    public V1ObjectMeta getMetadata() {
        return metadata;
    }

    /**
     * Gets the metadata. (Abbreviated method name for convenience.)
     *
     * @return the metadata
     */
    public V1ObjectMeta metadata() {
        return metadata;
    }

    /**
     * Gets the data.
     *
     * @return the data
     */
    public JsonObject data() {
        return data;
    }

    /**
     * Convenience method for getting the status. 
     *
     * @return the JSON object describing the status
     */
    public JsonObject statusJson() {
        return data.getAsJsonObject("status");
    }

    @Override
    public String toString() {
        return data.toString();
    }

}
