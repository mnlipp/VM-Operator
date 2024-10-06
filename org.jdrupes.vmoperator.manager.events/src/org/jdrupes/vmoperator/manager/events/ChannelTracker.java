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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jgrapes.core.Channel;

/**
 * Used to track mapping from a key to a channel. Entries must
 * be maintained by handlers for "add/remove" (or "open/close")
 * events delivered on the channels that are to be
 * made available by the tracker.
 * 
 * The channels are stored in the dictionary using {@link WeakReference}s.
 * Removing entries is therefore best practice but not an absolute necessity
 * as entries for cleared references are removed when one of the methods
 * {@link #values()}, {@link #channels()} or {@link #associated()} is called.
 *
 * @param <K> the key type
 * @param <C> the channel type
 * @param <A> the type of the associated data
 */
public class ChannelTracker<K, C extends Channel, A>
        implements ChannelDictionary<K, C, A> {

    private final Map<K, Data<C, A>> entries = new ConcurrentHashMap<>();

    /**
     * Combines the channel and associated data.
     *
     * @param <C> the generic type
     * @param <A> the generic type
     */
    @SuppressWarnings("PMD.ShortClassName")
    private static class Data<C extends Channel, A> {
        public final WeakReference<C> channel;
        public A associated;

        /**
         * Instantiates a new value.
         *
         * @param channel the channel
         */
        public Data(C channel) {
            this.channel = new WeakReference<>(channel);
        }
    }

    @Override
    public Set<K> keys() {
        return entries.keySet();
    }

    @Override
    public Collection<Value<C, A>> values() {
        var result = new ArrayList<Value<C, A>>();
        for (var itr = entries.entrySet().iterator(); itr.hasNext();) {
            var value = itr.next().getValue();
            var channel = value.channel.get();
            if (channel == null) {
                itr.remove();
                continue;
            }
            result.add(new Value<>(channel, value.associated));
        }
        return result;
    }

    /**
     * Returns the channel and associates data registered for the key
     * or an empty optional if no mapping exists.
     * 
     * @param key the key
     * @return the result
     */
    public Optional<Value<C, A>> value(K key) {
        var value = entries.get(key);
        if (value == null) {
            return Optional.empty();
        }
        var channel = value.channel.get();
        if (channel == null) {
            // Cleanup old reference
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(new Value<>(channel, value.associated));
    }

    /**
     * Store the given data.
     *
     * @param key the key
     * @param channel the channel
     * @param associated the associated
     * @return the channel manager
     */
    public ChannelTracker<K, C, A> put(K key, C channel, A associated) {
        Data<C, A> data = new Data<>(channel);
        data.associated = associated;
        entries.put(key, data);
        return this;
    }

    /**
     * Store the given data.
     *
     * @param key the key
     * @param channel the channel
     * @return the channel manager
     */
    public ChannelTracker<K, C, A> put(K key, C channel) {
        put(key, channel, null);
        return this;
    }

    /**
     * Associate the entry for the channel with the given data. The entry
     * for the channel must already exist.
     *
     * @param key the key
     * @param data the data
     * @return the channel manager
     */
    public ChannelTracker<K, C, A> associate(K key, A data) {
        Optional.ofNullable(entries.get(key))
            .ifPresent(v -> v.associated = data);
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
