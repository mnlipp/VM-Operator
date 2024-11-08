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

/**
 * Represents a VM definition.
 */
@SuppressWarnings("PMD.DataClass")
public class VmDefinitionModel extends K8sDynamicModel {

    /**
     * Instantiates a new model from the JSON representation.
     *
     * @param delegate the gson instance to use for extracting structured data
     * @param json the JSON
     */
    public VmDefinitionModel(Gson delegate, JsonObject json) {
        super(delegate, json);
    }
}
