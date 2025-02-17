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

package org.jdrupes.vmoperator.runner.qemu.events;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import org.jgrapes.core.Event;

/**
 * An {@link Event} that signals the reception of a QMP event from 
 * the Qemu process.
 */
public class MonitorEvent extends Event<Void> {

    private static final String EVENT_DATA = "data";

    /**
     * The kind of monitor event.
     */
    public enum Kind {
        READY, POWERDOWN, DEVICE_TRAY_MOVED, BALLOON_CHANGE, SHUTDOWN,
        SPICE_CONNECTED, SPICE_INITIALIZED, SPICE_DISCONNECTED, VSERPORT_CHANGE
    }

    private final Kind kind;
    private final JsonNode data;

    /**
     * Create event from response.
     *
     * @param response the response
     * @return the optional
     */
    @SuppressWarnings("PMD.TooFewBranchesForASwitchStatement")
    public static Optional<MonitorEvent> from(JsonNode response) {
        try {
            var kind = Kind.valueOf(response.get("event").asText());
            switch (kind) {
            case POWERDOWN:
                return Optional.of(new PowerdownEvent(kind, null));
            case DEVICE_TRAY_MOVED:
                return Optional
                    .of(new TrayMovedEvent(kind, response.get(EVENT_DATA)));
            case BALLOON_CHANGE:
                return Optional.of(
                    new BalloonChangeEvent(kind, response.get(EVENT_DATA)));
            case SHUTDOWN:
                return Optional
                    .of(new ShutdownEvent(kind, response.get(EVENT_DATA)));
            case SPICE_CONNECTED:
                return Optional.of(new SpiceConnectedEvent(kind,
                    response.get(EVENT_DATA)));
            case SPICE_INITIALIZED:
                return Optional.of(new SpiceInitializedEvent(kind,
                    response.get(EVENT_DATA)));
            case SPICE_DISCONNECTED:
                return Optional.of(new SpiceDisconnectedEvent(kind,
                    response.get(EVENT_DATA)));
            case VSERPORT_CHANGE:
                return Optional.of(new VserportChangeEvent(kind,
                    response.get(EVENT_DATA)));
            default:
                return Optional
                    .of(new MonitorEvent(kind, response.get(EVENT_DATA)));
            }
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Instantiates a new monitor event.
     *
     * @param kind the kind
     * @param data the data
     */
    protected MonitorEvent(Kind kind, JsonNode data) {
        this.kind = kind;
        this.data = data;
    }

    /**
     * Returns the kind of event.
     *
     * @return the kind
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Returns the data associated with the event.
     *
     * @return the object[]
     */
    public JsonNode data() {
        return data;
    }
}
