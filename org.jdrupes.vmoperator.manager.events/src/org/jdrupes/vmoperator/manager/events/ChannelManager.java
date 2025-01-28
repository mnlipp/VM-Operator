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

package org.jdrupes.vmoperator.manager.events;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.jgrapes.core.Channel;

/**
 * Provides an actively managed implementation of the {@link ChannelDictionary}.
 * 
 * The {@link ChannelManager} can be used for housekeeping by any component
 * that creates channels. It can be shared between this component and
 * some other component, preferably passing it as {@link ChannelDictionary}
 * (the read-only view) to the second component. Alternatively, the other
 * component can use a {@link ChannelTracker} to track the mappings using
 * events.
 *
 * @param <K> the key type
 * @param <C> the channel type
 * @param <A> the type of the associated data
 */
public class ChannelManager<K, C extends Channel, A>
        implements ChannelDictionary<K, C, A> {

    private final Map<K, Value<C, A>> entries = new ConcurrentHashMap<>();
    private final Function<K, C> supplier;

    /**
     * Instantiates a new channel manager.
     *
     * @param supplier the supplier that creates new channels
     */
    public ChannelManager(Function<K, C> supplier) {
        this.supplier = supplier;
    }

    /**
     * Instantiates a new channel manager without a default supplier.
     */
    public ChannelManager() {
        this(k -> null);
    }

    /**
     * Return all keys.
     *
     * @return the keys.
     */
    @Override
    public Set<K> keys() {
        return entries.keySet();
    }

    /**
     * Return all known values.
     *
     * @return the collection
     */
    @Override
    public Collection<Value<C, A>> values() {
        return entries.values();
    }

    /**
     * Returns the channel and associates data registered for the key
     * or an empty optional if no mapping exists.
     * 
     * @param key the key
     * @return the result
     */
    public Optional<Value<C, A>> value(K key) {
        return Optional.ofNullable(entries.get(key));
    }

    /**
     * Store the given data.
     *
     * @param key the key
     * @param channel the channel
     * @param associated the associated
     * @return the channel manager
     */
    public ChannelManager<K, C, A> put(K key, C channel, A associated) {
        entries.put(key, new Value<>(channel, associated));
        return this;
    }

    /**
     * Store the given data.
     *
     * @param key the key
     * @param channel the channel
     * @return the channel manager
     */
    public ChannelManager<K, C, A> put(K key, C channel) {
        put(key, channel, null);
        return this;
    }

    /**
     * Creates a new channel without adding it to the channel manager.
     * After fully initializing the channel, it should be added to the 
     * manager using {@link #put(K, C)}.
     *
     * @param key the key
     * @return the c
     */
    public C createChannel(K key) {
        return supplier.apply(key);
    }

    /**
     * Returns the {@link Channel} for the given name, creating it using 
     * the supplier passed to the constructor if it doesn't exist yet.
     *
     * @param key the key
     * @return the channel
     */
    public C channelGet(K key) {
        return computeIfAbsent(key, supplier);
    }

    /**
     * Returns the {@link Channel} for the given name, creating it using
     * the given supplier if it doesn't exist yet. 
     *
     * @param key the key
     * @param supplier the supplier
     * @return the channel
     */
    @SuppressWarnings({ "PMD.AssignmentInOperand",
        "PMD.DataflowAnomalyAnalysis" })
    public C computeIfAbsent(K key, Function<K, C> supplier) {
        return entries.computeIfAbsent(key,
            k -> new Value<>(supplier.apply(k), null)).channel();
    }

    /**
     * Associate the entry for the channel with the given data. The entry
     * for the channel must already exist.
     *
     * @param key the key
     * @param data the data
     * @return the channel manager
     */
    public ChannelManager<K, C, A> associate(K key, A data) {
        Optional.ofNullable(entries.computeIfPresent(key,
            (k, existing) -> new Value<>(existing.channel(), data)));
        return this;
    }

    /**
     * Removes the channel with the given name.
     *
     * @param name the name
     */
    public void remove(String name) {
        entries.remove(name);
    }
}
