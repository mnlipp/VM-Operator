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
import io.kubernetes.client.common.KubernetesListObject;

/**
 * Represents a list of Kubernetes objects each of which is
 * represented using a JSON data structure.
 * Some information that is common to all Kubernetes objects,
 * notably the metadata, is made available through the methods
 * defined by {@link KubernetesListObject}.
 */
public class K8sDynamicModels extends K8sDynamicModelsBase<K8sDynamicModel> {

    /**
     * Initialize the object list using the given JSON data.
     *
     * @param delegate the gson instance to use for extracting structured data
     * @param data the data
     */
    public K8sDynamicModels(Gson delegate, JsonObject data) {
        super(K8sDynamicModel.class, delegate, data);
    }

}
