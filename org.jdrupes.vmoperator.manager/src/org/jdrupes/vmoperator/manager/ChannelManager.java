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

package org.jdrupes.vmoperator.manager;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.jgrapes.core.Channel;

/**
 * A channel manager that maintains mappings from a key to channels
 * using weak references. 
 *
 * @param <K> the key type
 * @param <C> the generic type
 */
public class ChannelManager<K, C extends Channel> {

    @SuppressWarnings({ "PMD.FieldNamingConventions",
        "PMD.VariableNamingConventions" })
    private final Map<K, WeakReference<C>> channels = new ConcurrentHashMap<>();
    private final Function<K, C> supplier;
    private ChannelManager<K, C> readOnly;

    /**
     * Instantiates a new channel manager.
     *
     * @param supplier the supplier that reates new channels
     */
    public ChannelManager(Function<K, C> supplier) {
        this.supplier = supplier;
    }

    /**
     * Returns the channel registered for the key or an empty optional
     * if no mapping exists.
     *
     * @param key the key
     * @return the optional
     */
    public Optional<C> get(K key) {
        synchronized (channels) {
            var ref = channels.get(key);
            if (ref == null) {
                return Optional.empty();
            }
            var channel = ref.get();
            if (channel == null) {
                // Cleanup old reference
                channels.remove(key);
                return Optional.empty();
            }
            return Optional.of(channel);
        }
    }

    /**
     * Returns the {@link Channel} for the given name, creating it using 
     * the supplier passed to the constructor if it doesn't exist yet.
     *
     * @param key the key
     * @return the channel
     */
    public Optional<C> channel(K key) {
        return channel(key, supplier);
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
    public Optional<C> channel(K key, Function<K, C> supplier) {
        synchronized (channels) {
            return Optional
                .of(Optional.ofNullable(channels.get(key)).map(r -> r.get())
                    .orElseGet(() -> {
                        var channel = supplier.apply(key);
                        channels.put(key, new WeakReference<C>(channel));
                        return channel;
                    }));
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
     * Returns a read only view of this channel manager. The `channel`
     * methods behave similar to the {@link #get(Object)} method and
     * the {@link #remove(String)} method does nothing. 
     *
     * @return the channel manager
     */
    public ChannelManager<K, C> readOnly() {
        if (readOnly == null) {
            readOnly = new ChannelManager<>(supplier) {

                @Override
                public Optional<C> get(K key) {
                    return ChannelManager.this.get(key);
                }

                @Override
                public Optional<C> channel(K key) {
                    return ChannelManager.this.get(key);
                }

                @Override
                public Optional<C> channel(K key, Function<K, C> supplier) {
                    return ChannelManager.this.get(key);
                }

                @Override
                public void remove(String name) {
                    // Do nothing
                }

                @Override
                public ChannelManager<K, C> readOnly() {
                    return ChannelManager.this.readOnly();
                }
            };
        }
        return readOnly;
    }
}
