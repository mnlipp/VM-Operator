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

/**
 * Some constants.
 */
public class Constants extends org.jdrupes.vmoperator.common.Constants {

    /** The Constant STATE_RUNNING. */
    public static final String STATE_RUNNING = "Running";

    /** The Constant STATE_STOPPED. */
    public static final String STATE_STOPPED = "Stopped";

    /** The Constant IMAGE_REPO_PATH. */
    public static final String IMAGE_REPO_PATH
        = "/var/local/vmop-image-repository";
}
