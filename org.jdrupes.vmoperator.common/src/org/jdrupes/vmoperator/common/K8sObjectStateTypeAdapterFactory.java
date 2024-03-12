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
import com.google.gson.InstanceCreator;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.Type;

// TODO: Auto-generated Javadoc
/**
 * A factory for creating K8sObjectStates objects.
 */
public class K8sObjectStateTypeAdapterFactory implements TypeAdapterFactory {

    /**
     * Creates a type adapter for the given type.
     *
     * @param <T> the generic type
     * @param gson the gson
     * @param typeToken the type token
     * @return the type adapter or null if the type is not handles by
     * this factory
     */
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        if (TypeToken.get(K8sObjectState.class).equals(typeToken)) {
            return (TypeAdapter<T>) (new K8sObjectStateCreator(gson));
        }
        if (TypeToken.get(K8sObjectStates.class).equals(typeToken)) {
            return (TypeAdapter<T>) (new K8sObjectStatesCreator(gson));
        }
        return null;
    }

    /**
     * The Class K8sObjectStateCreator.
     */
    /* default */ class K8sObjectStateCreator
            extends TypeAdapter<K8sObjectState>
            implements InstanceCreator<K8sObjectState> {
        private final Gson delegate;

        /**
         * Instantiates a new object state creator.
         *
         * @param delegate the delegate
         */
        public K8sObjectStateCreator(Gson delegate) {
            this.delegate = delegate;
        }

        @Override
        public K8sObjectState createInstance(Type type) {
            return new K8sObjectState(delegate, null);
        }

        @Override
        public void write(JsonWriter jsonWriter, K8sObjectState state)
                throws IOException {
            jsonWriter.jsonValue(delegate.toJson(state.data()));
        }

        @Override
        public K8sObjectState read(JsonReader jsonReader)
                throws IOException {
            return new K8sObjectState(delegate,
                delegate.fromJson(jsonReader, JsonObject.class));
        }
    }

    /**
     * The Class K8sObjectStatesCreator.
     */
    /* default */class K8sObjectStatesCreator
            extends TypeAdapter<K8sObjectStates>
            implements InstanceCreator<K8sObjectStates> {

        private final Gson delegate;

        /**
         * Instantiates a new object states creator.
         *
         * @param delegate the delegate
         */
        public K8sObjectStatesCreator(Gson delegate) {
            this.delegate = delegate;
        }

        @Override
        public K8sObjectStates createInstance(Type type) {
            return new K8sObjectStates(delegate, null);
        }

        @Override
        public void write(JsonWriter jsonWriter, K8sObjectStates states)
                throws IOException {
            jsonWriter.jsonValue(delegate.toJson(states.data()));
        }

        @Override
        public K8sObjectStates read(JsonReader jsonReader)
                throws IOException {
            return new K8sObjectStates(delegate,
                delegate.fromJson(jsonReader, JsonObject.class));
        }
    }

}
