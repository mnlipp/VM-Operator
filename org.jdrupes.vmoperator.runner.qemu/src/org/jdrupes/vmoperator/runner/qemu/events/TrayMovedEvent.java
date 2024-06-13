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
import org.jdrupes.vmoperator.runner.qemu.CdMediaController.TrayState;

/**
 * Signals a change of tray state of one of the VM's CDROM drives.
 */
public class TrayMovedEvent extends MonitorEvent {

    /**
     * Instantiates a new tray moved.
     *
     * @param kind the kind
     * @param data the data
     */
    public TrayMovedEvent(Kind kind, JsonNode data) {
        super(kind, data);
    }

    /**
     * returns the drive id.
     *
     * @return the string
     */
    public String driveId() {
        return data().get("id").asText();
    }

    /**
     * Returns the tray state.
     *
     * @return the tray state
     */
    public TrayState trayState() {
        return data().get("tray-open").asBoolean()
            ? TrayState.OPEN
            : TrayState.CLOSED;
    }
}
