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
import io.kubernetes.client.openapi.ApiClient;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;

/**
 * A factory for creating objects.
 *
 * @param <O> the generic type
 * @param <L> the generic type
 */
public class DynamicTypeAdapterFactory<O extends K8sDynamicModel,
        L extends K8sDynamicModelsBase<O>> implements TypeAdapterFactory {

    private final Class<O> objectClass;
    private final Class<L> objectListClass;

    /**
     * Make sure that this adapter is registered.
     *
     * @param client the client
     */
    public void register(ApiClient client) {
        if (!ModelCreator.class
            .equals(client.getJSON().getGson().getAdapter(objectClass)
                .getClass())
            || !ModelsCreator.class.equals(client.getJSON().getGson()
                .getAdapter(objectListClass).getClass())) {
            Gson gson = client.getJSON().getGson();
            client.getJSON().setGson(gson.newBuilder()
                .registerTypeAdapterFactory(this).create());
        }
    }

    /**
     * Instantiates a new generic type adapter factory.
     *
     * @param objectClass the object class
     * @param objectListClass the object list class
     */
    public DynamicTypeAdapterFactory(Class<O> objectClass,
            Class<L> objectListClass) {
        this.objectClass = objectClass;
        this.objectListClass = objectListClass;
    }

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
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        if (TypeToken.get(objectClass).equals(typeToken)) {
            return (TypeAdapter<T>) new ModelCreator(gson);
        }
        if (TypeToken.get(objectListClass).equals(typeToken)) {
            return (TypeAdapter<T>) new ModelsCreator(gson);
        }
        return null;
    }

    /**
     * The Class ModelCreator.
     */
    private class ModelCreator extends TypeAdapter<O>
            implements InstanceCreator<O> {
        private final Gson delegate;

        /**
         * Instantiates a new object state creator.
         *
         * @param delegate the delegate
         */
        public ModelCreator(Gson delegate) {
            this.delegate = delegate;
        }

        @Override
        public O createInstance(Type type) {
            try {
                return objectClass.getConstructor(Gson.class, JsonObject.class)
                    .newInstance(delegate, null);
            } catch (InstantiationException | IllegalAccessException
                    | IllegalArgumentException | InvocationTargetException
                    | NoSuchMethodException | SecurityException e) {
                return null;
            }
        }

        @Override
        public void write(JsonWriter jsonWriter, O state)
                throws IOException {
            jsonWriter.jsonValue(delegate.toJson(state.data()));
        }

        @Override
        public O read(JsonReader jsonReader)
                throws IOException {
            try {
                return objectClass.getConstructor(Gson.class, JsonObject.class)
                    .newInstance(delegate,
                        delegate.fromJson(jsonReader, JsonObject.class));
            } catch (InstantiationException | IllegalAccessException
                    | IllegalArgumentException | InvocationTargetException
                    | NoSuchMethodException | SecurityException e) {
                return null;
            }
        }
    }

    /**
     * The Class ModelsCreator.
     */
    private class ModelsCreator extends TypeAdapter<L>
            implements InstanceCreator<L> {

        private final Gson delegate;

        /**
         * Instantiates a new object states creator.
         *
         * @param delegate the delegate
         */
        public ModelsCreator(Gson delegate) {
            this.delegate = delegate;
        }

        @Override
        public L createInstance(Type type) {
            try {
                return objectListClass
                    .getConstructor(Gson.class, JsonObject.class)
                    .newInstance(delegate, null);
            } catch (InstantiationException | IllegalAccessException
                    | IllegalArgumentException | InvocationTargetException
                    | NoSuchMethodException | SecurityException e) {
                return null;
            }
        }

        @Override
        public void write(JsonWriter jsonWriter, L states)
                throws IOException {
            jsonWriter.jsonValue(delegate.toJson(states.data()));
        }

        @Override
        public L read(JsonReader jsonReader)
                throws IOException {
            try {
                return objectListClass
                    .getConstructor(Gson.class, JsonObject.class)
                    .newInstance(delegate,
                        delegate.fromJson(jsonReader, JsonObject.class));
            } catch (InstantiationException | IllegalAccessException
                    | IllegalArgumentException | InvocationTargetException
                    | NoSuchMethodException | SecurityException e) {
                return null;
            }
        }
    }

}
