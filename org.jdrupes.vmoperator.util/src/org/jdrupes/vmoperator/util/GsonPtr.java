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

package org.jdrupes.vmoperator.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Utility class for pointing to elements on a Gson (Json) tree.
 */
@SuppressWarnings({ "PMD.DataflowAnomalyAnalysis",
    "PMD.ClassWithOnlyPrivateConstructorsShouldBeFinal", "PMD.GodClass" })
public class GsonPtr {

    private final JsonElement position;

    private GsonPtr(JsonElement root) {
        this.position = root;
    }

    /**
     * Create a new instance pointing to the given element.
     *
     * @param root the root
     * @return the Gson pointer
     */
    @SuppressWarnings("PMD.ShortMethodName")
    public static GsonPtr to(JsonElement root) {
        return new GsonPtr(root);
    }

    /**
     * Create a new instance pointing to the {@link JsonElement} 
     * selected by the given selectors. If a selector of type 
     * {@link String} denotes a non-existant member of a
     * {@link JsonObject}, a new member (of type {@link JsonObject}
     * is added.
     *
     * @param selectors the selectors
     * @return the Gson pointer
     */
    @SuppressWarnings({ "PMD.ShortMethodName", "PMD.PreserveStackTrace",
        "PMD.AvoidDuplicateLiterals" })
    public GsonPtr to(Object... selectors) {
        JsonElement element = position;
        for (Object sel : selectors) {
            if (element instanceof JsonObject obj
                && sel instanceof String member) {
                element = Optional.ofNullable(obj.get(member)).orElseGet(() -> {
                    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
                    var child = new JsonObject();
                    obj.add(member, child);
                    return child;
                });
                continue;
            }
            if (element instanceof JsonArray arr
                && sel instanceof Integer index) {
                try {
                    element = arr.get(index);
                } catch (IndexOutOfBoundsException e) {
                    throw new IllegalStateException("Selected array index"
                        + " may not be empty.");
                }
                continue;
            }
            throw new IllegalStateException("Invalid selection");
        }
        return new GsonPtr(element);
    }

    /**
     * Create a new instance pointing to the {@link JsonElement} 
     * selected by the given selectors. If a selector of type 
     * {@link String} denotes a non-existant member of a
     * {@link JsonObject} the result is empty.
     *
     * @param selectors the selectors
     * @return the Gson pointer
     */
    @SuppressWarnings({ "PMD.ShortMethodName", "PMD.PreserveStackTrace" })
    public Optional<GsonPtr> get(Object... selectors) {
        JsonElement element = position;
        for (Object sel : selectors) {
            if (element instanceof JsonObject obj
                && sel instanceof String member) {
                element = obj.get(member);
                if (element == null) {
                    return Optional.empty();
                }
                continue;
            }
            if (element instanceof JsonArray arr
                && sel instanceof Integer index) {
                try {
                    element = arr.get(index);
                } catch (IndexOutOfBoundsException e) {
                    throw new IllegalStateException("Selected array index"
                        + " may not be empty.");
                }
                continue;
            }
            throw new IllegalStateException("Invalid selection");
        }
        return Optional.of(new GsonPtr(element));
    }

    /**
     * Returns {@link JsonElement} that the pointer points to.
     *
     * @return the result
     */
    public JsonElement get() {
        return position;
    }

    /**
     * Returns {@link JsonElement} that the pointer points to,
     * casted to the given type.
     *
     * @param <T> the generic type
     * @param cls the cls
     * @return the result
     */
    @SuppressWarnings({ "PMD.AvoidBranchingStatementAsLastInLoop" })
    public <T extends JsonElement> T getAs(Class<T> cls) {
        if (cls.isAssignableFrom(position.getClass())) {
            return cls.cast(position);
        }
        throw new IllegalArgumentException("Not positioned at element"
            + " of desired type.");
    }

    /**
     * Returns the selected {@link JsonElement}, cast to the class
     * specified.
     *
     * @param <T> the generic type
     * @param cls the cls
     * @param selectors the selectors
     * @return the optional
     */
    @SuppressWarnings({ "PMD.AvoidBranchingStatementAsLastInLoop" })
    public <T extends JsonElement> Optional<T>
            getAs(Class<T> cls, Object... selectors) {
        JsonElement element = position;
        for (Object sel : selectors) {
            if (element instanceof JsonObject obj
                && sel instanceof String member) {
                element = obj.get(member);
                if (element == null) {
                    return Optional.empty();
                }
                continue;
            }
            if (element instanceof JsonArray arr
                && sel instanceof Integer index) {
                try {
                    element = arr.get(index);
                } catch (IndexOutOfBoundsException e) {
                    return Optional.empty();
                }
                continue;
            }
            return Optional.empty();
        }
        if (cls.isAssignableFrom(element.getClass())) {
            return Optional.of(cls.cast(element));
        }
        return Optional.empty();
    }

    /**
     * Returns the String value of the selected {@link JsonPrimitive}.
     *
     * @param selectors the selectors
     * @return the as string
     */
    public Optional<String> getAsString(Object... selectors) {
        return getAs(JsonPrimitive.class, selectors)
            .map(JsonPrimitive::getAsString);
    }

    /**
     * Returns the Integer value of the selected {@link JsonPrimitive}.
     *
     * @param selectors the selectors
     * @return the as string
     */
    public Optional<Integer> getAsInt(Object... selectors) {
        return getAs(JsonPrimitive.class, selectors)
            .map(JsonPrimitive::getAsInt);
    }

    /**
     * Returns the Integer value of the selected {@link JsonPrimitive}.
     *
     * @param selectors the selectors
     * @return the as string
     */
    public Optional<BigInteger> getAsBigInteger(Object... selectors) {
        return getAs(JsonPrimitive.class, selectors)
            .map(JsonPrimitive::getAsBigInteger);
    }

    /**
     * Returns the Long value of the selected {@link JsonPrimitive}.
     *
     * @param selectors the selectors
     * @return the as string
     */
    public Optional<Long> getAsLong(Object... selectors) {
        return getAs(JsonPrimitive.class, selectors)
            .map(JsonPrimitive::getAsLong);
    }

    /**
     * Returns the boolean value of the selected {@link JsonPrimitive}.
     *
     * @param selectors the selectors
     * @return the boolean
     */
    public Optional<Boolean> getAsBoolean(Object... selectors) {
        return getAs(JsonPrimitive.class, selectors)
            .map(JsonPrimitive::getAsBoolean);
    }

    /**
     * Returns the elements of the selected {@link JsonArray} as list.
     *
     * @param <T> the generic type
     * @param cls the cls
     * @param selectors the selectors
     * @return the list
     */
    @SuppressWarnings("unchecked")
    public <T extends JsonElement> List<T> getAsListOf(Class<T> cls,
            Object... selectors) {
        return getAs(JsonArray.class, selectors).map(a -> (List<T>) a.asList())
            .orElse(Collections.emptyList());
    }

    /**
     * Sets the selected value. This pointer must point to a
     * {@link JsonObject} or {@link JsonArray}. The selector must
     * be a {@link String} or an integer respectively.
     *
     * @param selector the selector
     * @param value the value
     * @return the Gson pointer
     */
    public GsonPtr set(Object selector, JsonElement value) {
        if (position instanceof JsonObject obj
            && selector instanceof String member) {
            obj.add(member, value);
            return this;
        }
        if (position instanceof JsonArray arr
            && selector instanceof Integer index) {
            if (index >= arr.size()) {
                arr.add(value);
            } else {
                arr.set(index, value);
            }
            return this;
        }
        throw new IllegalStateException("Invalid selection");
    }

    /**
     * Short for `set(selector, new JsonPrimitive(value))`.
     *
     * @param selector the selector
     * @param value the value
     * @return the gson ptr
     * @see #set(Object, JsonElement)
     */
    public GsonPtr set(Object selector, String value) {
        return set(selector, new JsonPrimitive(value));
    }

    /**
     * Short for `set(selector, new JsonPrimitive(value))`.
     *
     * @param selector the selector
     * @param value the value
     * @return the gson ptr
     * @see #set(Object, JsonElement)
     */
    public GsonPtr set(Object selector, Long value) {
        return set(selector, new JsonPrimitive(value));
    }

    /**
     * Short for `set(selector, new JsonPrimitive(value))`.
     *
     * @param selector the selector
     * @param value the value
     * @return the gson ptr
     * @see #set(Object, JsonElement)
     */
    public GsonPtr set(Object selector, BigInteger value) {
        return set(selector, new JsonPrimitive(value));
    }

    /**
     * Same as {@link #set(Object, JsonElement)}, but sets the value
     * only if it doesn't exist yet, else returns the existing value.
     * If this pointer points to a {@link JsonArray} and the selector
     * if larger than or equal to the size of the array, the supplied
     * value will be appended.
     *
     * @param <T> the generic type
     * @param selector the selector
     * @param supplier the supplier of the missing value
     * @return the existing or supplied value
     */
    @SuppressWarnings("unchecked")
    public <T extends JsonElement> T
            computeIfAbsent(Object selector, Supplier<T> supplier) {
        if (position instanceof JsonObject obj
            && selector instanceof String member) {
            return Optional.ofNullable((T) obj.get(member)).orElseGet(() -> {
                var res = supplier.get();
                obj.add(member, res);
                return res;
            });
        }
        if (position instanceof JsonArray arr
            && selector instanceof Integer index) {
            if (index >= arr.size()) {
                var res = supplier.get();
                arr.add(res);
                return res;
            }
            return (T) arr.get(index);
        }
        throw new IllegalStateException("Invalid selection");
    }

    /**
     * Short for `computeIfAbsent(selector, () -> new JsonPrimitive(value))`.
     *
     * @param selector the selector
     * @param value the value
     * @return the Gson pointer
     */
    public GsonPtr getOrSet(Object selector, String value) {
        computeIfAbsent(selector, () -> new JsonPrimitive(value));
        return this;
    }

    /**
     * Removes all properties except the specified ones.
     *
     * @param properties the properties
     */
    public void removeExcept(String... properties) {
        if (!position.isJsonObject()) {
            return;
        }
        for (var itr = ((JsonObject) position).entrySet().iterator();
                itr.hasNext();) {
            var entry = itr.next();
            if (Arrays.asList(properties).contains(entry.getKey())) {
                continue;
            }
            itr.remove();
        }
    }
}
