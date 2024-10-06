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

package org.jdrupes.vmoperator.manager.events;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.jgrapes.core.Channel;

/**
 * Supports the lookup of a channel by a name (an id). As a convenience,
 * it is possible to additionally associate arbitrary data with the entry
 * (and thus with the channel). Note that this interface defines a
 * read-only view of the dictionary.
 *
 * @param <K> the key type
 * @param <C> the channel type
 * @param <A> the type of the associated data
 */
public interface ChannelDictionary<K, C extends Channel, A> {

    /**
     * Combines the channel and the associated data.
     *
     * @param <C> the channel type
     * @param <A> the type of the associated data
     * @param channel the channel
     * @param associated the associated
     */
    @SuppressWarnings("PMD.ShortClassName")
    public record Value<C extends Channel, A>(C channel, A associated) {
    }

    /**
     * Returns all known keys.
     *
     * @return the keys
     */
    Set<K> keys();

    /**
     * Return all known values.
     *
     * @return the collection
     */
    Collection<Value<C, A>> values();

    /**
     * Returns the channel and associates data registered for the key
     * or an empty optional if no entry exists.
     * 
     * @param key the key
     * @return the result
     */
    Optional<Value<C, A>> value(K key);

    /**
     * Return all known channels.
     *
     * @return the collection
     */
    default Collection<C> channels() {
        return values().stream().map(v -> v.channel).toList();
    }

    /**
     * Returns the channel registered for the key or an empty optional
     * if no mapping exists.
     *
     * @param key the key
     * @return the optional
     */
    default Optional<C> channel(K key) {
        return value(key).map(b -> b.channel);
    }

    /**
     * Returns all known associated data.
     *
     * @return the collection
     */
    default Collection<A> associated() {
        return values().stream()
            .filter(v -> v.associated() != null)
            .map(v -> v.associated).toList();
    }

    /**
     * Return the data associated with the entry for the channel.
     *
     * @param key the key
     * @return the data
     */
    default Optional<A> associated(K key) {
        return value(key).map(b -> b.associated);
    }
}
