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
import java.util.function.Function;
import org.jgrapes.core.Channel;

/**
 * A channel manager that maintains mappings from a key to a channel.
 * As a convenience, it is possible to additionally associate arbitrary
 * data with the entry (and thus with the channel).
 * 
 * There are several usage patterns for this class. It can be used
 * by a component that defines channels for housekeeping. It can be
 * shared between this component and another component, preferably
 * using the {@link #fixed()} view for the second component.
 * Alternatively, it can also be used to track the mapping using
 * "add/remove" events and the channels on which they are delivered. 
 *
 * @param <K> the key type
 * @param <C> the channel type
 * @param <A> the type of the associated data
 */
public class ChannelManager<K, C extends Channel, A> {

    private final Map<K, Data<C, A>> channels = new ConcurrentHashMap<>();
    private final Function<K, C> supplier;
    private ChannelManager<K, C, A> readOnly;

    /**
     * Helper
     */
    @SuppressWarnings("PMD.ShortClassName")
    private static class Data<C extends Channel, A> {
        public WeakReference<C> channel;
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
    public ChannelManager<K, C, A> put(K key, C channel, A associated) {
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
    public ChannelManager<K, C, A> put(K key, C channel) {
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
     * Returns the {@link Channel} for the given name, creating it using 
     * the supplier passed to the constructor if it doesn't exist yet.
     *
     * @param key the key
     * @return the channel
     */
    public Optional<C> getChannel(K key) {
        return getChannel(key, supplier);
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
    public Optional<C> getChannel(K key, Function<K, C> supplier) {
        synchronized (channels) {
            return Optional
                .of(Optional.ofNullable(channels.get(key))
                    .map(v -> v.channel.get())
                    .orElseGet(() -> {
                        var channel = supplier.apply(key);
                        channels.put(key, new Data<>(channel));
                        return channel;
                    }));
        }
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

    /**
     * Returns a read only view of this channel manager. The methods
     * that usually create a new entry refrain from doing so. The
     * methods that change the value of channel and {@link #remove(String)}
     * do nothing. The associated data, however, can still be changed.
     *
     * @return the channel manager
     */
    public ChannelManager<K, C, A> fixed() {
        if (readOnly == null) {
            readOnly = new ChannelManager<>(supplier) {

                @Override
                public Optional<Both<C, A>> both(K key) {
                    return ChannelManager.this.both(key);
                }

                @Override
                public ChannelManager<K, C, A> put(K key, C channel,
                        A associated) {
                    return associate(key, associated);
                }

                @Override
                public Optional<C> getChannel(K key) {
                    return ChannelManager.this.channel(key);
                }

                @Override
                public Optional<C> getChannel(K key, Function<K, C> supplier) {
                    return ChannelManager.this.channel(key);
                }

                @Override
                public ChannelManager<K, C, A> associate(K key, A data) {
                    return ChannelManager.this.associate(key, data);
                }

                @Override
                public Optional<A> associated(K key) {
                    return ChannelManager.this.associated(key);
                }

                @Override
                public Collection<A> associated() {
                    return ChannelManager.this.associated();
                }

                @Override
                public void remove(String name) {
                    // Do nothing
                }

                @Override
                public Set<K> keys() {
                    return ChannelManager.this.keys();
                }

                @Override
                public ChannelManager<K, C, A> fixed() {
                    return ChannelManager.this.fixed();
                }

            };
        }
        return readOnly;
    }
}
