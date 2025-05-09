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

import org.jdrupes.vmoperator.common.K8sClient;
import org.jdrupes.vmoperator.common.VmDefinition;
import org.jgrapes.core.Channel;
import org.jgrapes.core.Event;
import org.jgrapes.core.EventPipeline;
import org.jgrapes.core.Subchannel.DefaultSubchannel;

/**
 * A subchannel used to send the events related to a specific VM.
 */
@SuppressWarnings("PMD.DataClass")
public class VmChannel extends DefaultSubchannel {

    private final EventPipeline pipeline;
    private final K8sClient client;
    private VmDefinition definition;
    private long generation = -1;

    /**
     * Instantiates a new watch channel.
     *
     * @param mainChannel the main channel
     * @param pipeline the pipeline
     * @param client the client
     */
    public VmChannel(Channel mainChannel, EventPipeline pipeline,
            K8sClient client) {
        super(mainChannel);
        this.pipeline = pipeline;
        this.client = client;
    }

    /**
     * Sets the last known definition of the resource.
     *
     * @param definition the definition
     * @return the watch channel
     */
    @SuppressWarnings("PMD.LinguisticNaming")
    public VmChannel setVmDefinition(VmDefinition definition) {
        this.definition = definition;
        return this;
    }

    /**
     * Returns the last known definition of the VM.
     *
     * @return the defintion
     */
    public VmDefinition vmDefinition() {
        return definition;
    }

    /**
     * Gets the last processed generation. Returns -1 if no
     * definition has been processed yet.
     *
     * @return the generation
     */
    public long generation() {
        return generation;
    }

    /**
     * Sets the last processed generation.
     *
     * @param generation the generation to set
     * @return true if value has changed
     */
    @SuppressWarnings("PMD.LinguisticNaming")
    public boolean setGeneration(long generation) {
        if (this.generation == generation) {
            return false;
        }
        this.generation = generation;
        return true;
    }

    /**
     * Returns the pipeline.
     *
     * @return the event pipeline
     */
    public EventPipeline pipeline() {
        return pipeline;
    }

    /**
     * Fire the given event on this channel, using the associated
     * {@link #pipeline()}.
     *
     * @param <T> the generic type
     * @param event the event
     * @return the t
     */
    public <T extends Event<?>> T fire(T event) {
        pipeline.fire(event, this);
        return event;
    }

    /**
     * Returns the API client.
     *
     * @return the API client
     */
    public K8sClient client() {
        return client;
    }
}
