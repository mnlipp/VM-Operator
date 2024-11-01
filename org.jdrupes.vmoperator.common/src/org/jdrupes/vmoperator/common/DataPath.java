/*
 * VM-Operator
 * Copyright (C) 2023 Michael N. Lipp
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public class DataPath {

    protected static final Logger logger
        = Logger.getLogger(DataPath.class.getName());

    private DataPath() {
    }

    @SuppressWarnings("PMD.UseLocaleWithCaseConversions")
    public static <T> Optional<T> get(Object from, Object... selectors) {
        Object cur = from;
        for (var selector : selectors) {
            if (selector instanceof String && cur instanceof Map map) {
                cur = map.get(selector);
                continue;
            }
            if (selector instanceof Number index && cur instanceof List list) {
                cur = list.get(index.intValue());
                continue;
            }
            if (selector instanceof String property) {
                var retrieved = tryAccess(cur, property);
                if (retrieved.isEmpty()) {
                    return Optional.empty();
                }
                cur = retrieved.get();
            }
        }
        return Optional.ofNullable((T) cur);
    }

    private static Optional<Object> tryAccess(Object obj, String property) {
        Method acc = null;
        try {
            // Try getter
            acc = obj.getClass().getMethod("get" + property.substring(0, 1)
                .toUpperCase() + property.substring(1));
        } catch (SecurityException e) {
            return Optional.empty();
        } catch (NoSuchMethodException e) { // NOPMD
            // Can happen...
        }
        if (acc == null) {
            try {
                // Try method
                acc = obj.getClass().getMethod(property);
            } catch (SecurityException | NoSuchMethodException e) {
                return Optional.empty();
            }
        }
        if (acc != null) {
            try {
                return Optional.ofNullable(acc.invoke(obj));
            } catch (IllegalAccessException
                    | InvocationTargetException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Attempts to make a as-deep-as-possible copy of the given
     * container. New containers will be created for Maps, Lists and
     * Arrays. The method is invoked recursively for the entries/items.
     * 
     * If invoked with an object that is neither a map, list or array,
     * the methods checks if the object implements {@link Cloneable}
     * and if it does, invokes its {@link Object#clone()} method.
     * Else the method return the object.
     *
     * @param <T> the generic type
     * @param object the container
     * @return the t
     */
    @SuppressWarnings({ "PMD.CognitiveComplexity", "unchecked" })
    public static <T> T deepCopy(T object) {
        if (object instanceof Map map) {
            @SuppressWarnings("PMD.UseConcurrentHashMap")
            Map<Object, Object> copy;
            try {
                copy = (Map<Object, Object>) object.getClass().getConstructor()
                    .newInstance();
            } catch (InstantiationException | IllegalAccessException
                    | IllegalArgumentException | InvocationTargetException
                    | NoSuchMethodException | SecurityException e) {
                logger.severe(
                    () -> "Cannot create new instance of " + object.getClass());
                return null;
            }
            for (var entry : ((Map<?, ?>) map).entrySet()) {
                copy.put(entry.getKey(),
                    deepCopy(entry.getValue()));
            }
            return (T) copy;
        }
        if (object instanceof List list) {
            List<Object> copy = new ArrayList<>();
            for (var item : list) {
                copy.add(deepCopy(item));
            }
            return (T) copy;
        }
        if (object.getClass().isArray()) {
            var copy = new ArrayList<>();
            for (var item : (Object[]) object) {
                copy.add(deepCopy(item));
            }
            return (T) copy.toArray();
        }
        if (object instanceof Cloneable) {
            try {
                return (T) object.getClass().getMethod("clone")
                    .invoke(object);
            } catch (IllegalAccessException | InvocationTargetException
                    | NoSuchMethodException | SecurityException e) {
                return object;
            }
        }
        return object;
    }
}
