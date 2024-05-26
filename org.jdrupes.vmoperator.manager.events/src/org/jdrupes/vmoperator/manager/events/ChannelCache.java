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
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jgrapes.core.Channel;

/**
 * A channel manager that tracks mappings from a key to a channel using
 * "add/remove" (or "open/close") events and the channels on which they
 * are delivered.
 *
 * @param <K> the key type
 * @param <C> the channel type
 * @param <A> the type of the associated data
 */
public class ChannelCache<K, C extends Channel, A> {

    private final Map<K, Data<C, A>> channels = new ConcurrentHashMap<>();

    /**
     * Helper
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

    /**
     * Combines the channel and the associated data.
     *
     * @param <C> the generic type
     * @param <A> the generic type
     */
    @SuppressWarnings("PMD.ShortClassName")
    public static class Both<C extends Channel, A> {

        /** The channel. */
        public C channel;

        /** The associated. */
        public A associated;

        /**
         * Instantiates a new both.
         *
         * @param channel the channel
         * @param associated the associated
         */
        public Both(C channel, A associated) {
            super();
            this.channel = channel;
            this.associated = associated;
        }
    }

    /**
     * Returns the channel and associates data registered for the key
     * or an empty optional if no mapping exists.
     * 
     * @param key the key
     * @return the result
     */
    public Optional<Both<C, A>> both(K key) {
        synchronized (channels) {
            var value = channels.get(key);
            if (value == null) {
                return Optional.empty();
            }
            var channel = value.channel.get();
            if (channel == null) {
                // Cleanup old reference
                channels.remove(key);
                return Optional.empty();
            }
            return Optional.of(new Both<>(channel, value.associated));
        }
    }

    /**
     * Store the given data.
     *
     * @param key the key
     * @param channel the channel
     * @param associated the associated
     * @return the channel manager
     */
    public ChannelCache<K, C, A> put(K key, C channel, A associated) {
        Data<C, A> data = new Data<>(channel);
        data.associated = associated;
        channels.put(key, data);
        return this;
    }

    /**
     * Store the given data.
     *
     * @param key the key
     * @param channel the channel
     * @return the channel manager
     */
    public ChannelCache<K, C, A> put(K key, C channel) {
        put(key, channel, null);
        return this;
    }

    /**
     * Returns the channel registered for the key or an empty optional
     * if no mapping exists.
     *
     * @param key the key
     * @return the optional
     */
    public Optional<C> channel(K key) {
        return both(key).map(b -> b.channel);
    }

    /**
     * Associate the entry for the channel with the given data. The entry
     * for the channel must already exist.
     *
     * @param key the key
     * @param data the data
     * @return the channel manager
     */
    public ChannelCache<K, C, A> associate(K key, A data) {
        synchronized (channels) {
            Optional.ofNullable(channels.get(key))
                .ifPresent(v -> v.associated = data);
        }
        return this;
    }

    /**
     * Return the data associated with the entry for the channel.
     *
     * @param key the key
     * @return the data
     */
    public Optional<A> associated(K key) {
        return both(key).map(b -> b.associated);
    }

    /**
     * Returns all associated data.
     *
     * @return the collection
     */
    public Collection<A> associated() {
        synchronized (channels) {
            return channels.values().stream()
                .filter(v -> v.channel.get() != null && v.associated != null)
                .map(v -> v.associated).toList();
        }
    }

    /**
     * Removes the channel with the given name.
     *
     * @param name the name
     */
    public void remove(String name) {
        synchronized (channels) {
            channels.remove(name);
        }
    }

    /**
     * Returns all known keys.
     *
     * @return the sets the
     */
    public Set<K> keys() {
        return channels.keySet();
    }
}
