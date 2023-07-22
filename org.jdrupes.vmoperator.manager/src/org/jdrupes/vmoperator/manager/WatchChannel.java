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

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import org.jgrapes.core.Channel;
import org.jgrapes.core.EventPipeline;
import org.jgrapes.core.Subchannel.DefaultSubchannel;

/**
 * A subchannel used to send the events related to a specific
 * VM.
 */
public class WatchChannel extends DefaultSubchannel {

    private final EventPipeline pipeline;
    private final CoreV1Api api;
    private final CustomObjectsApi coa;

    /**
     * Instantiates a new watch channel.
     *
     * @param mainChannel the main channel
     * @param pipeline the pipeline
     */
    public WatchChannel(Channel mainChannel, EventPipeline pipeline,
            CoreV1Api api, CustomObjectsApi coa) {
        super(mainChannel);
        this.pipeline = pipeline;
        this.api = api;
        this.coa = coa;
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
     * Returns the API object for invoking kubernetes functions.
     *
     * @return the API object
     */
    public CoreV1Api api() {
        return api;
    }

    /**
     * Returns the API object for invoking kubernetes custom object
     * functions.
     *
     * @return the API object
     */
    public CustomObjectsApi coa() {
        return coa;
    }
}
