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

package org.jdrupes.vmoperator.util;

import java.util.logging.LogManager;

/**
 * A logging manager that isn't disabled by a shutdown hook.
 */
public class LongLoggingManager extends LogManager {
    private static LongLoggingManager instance;

    /**
     * Instantiates a new long logging manager.
     */
    @SuppressWarnings("PMD.AssignmentToNonFinalStatic")
    public LongLoggingManager() {
        instance = this;
    }

    @Override
    public void reset() {
        /* don't reset yet. */
    }

    private void reset0() {
        super.reset();
    }

    /**
     * Reset finally.
     */
    public static void resetFinally() {
        instance.reset0();
    }
}