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

import io.kubernetes.client.openapi.ApiClient;
import org.jgrapes.core.Channel;
import org.jgrapes.core.EventPipeline;
import org.jgrapes.core.Subchannel.DefaultSubchannel;

/**
 * A subchannel used to send the events related to a specific
 * VM.
 */
public class WatchChannel extends DefaultSubchannel {

    private final EventPipeline pipeline;
    private final ApiClient client;

    /**
     * Instantiates a new watch channel.
     *
     * @param mainChannel the main channel
     * @param pipeline the pipeline
     * @param client 
     */
    public WatchChannel(Channel mainChannel, EventPipeline pipeline,
            ApiClient client) {
        super(mainChannel);
        this.pipeline = pipeline;
        this.client = client;
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
     * Returns the API client.
     *
     * @return the API client
     */
    public ApiClient client() {
        return client;
    }
}
