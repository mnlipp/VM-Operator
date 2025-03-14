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

package org.jdrupes.vmoperator.runner.qemu;

/**
 * Some constants.
 */
@SuppressWarnings("PMD.DataClass")
public class Constants extends org.jdrupes.vmoperator.common.Constants {

    /**
     * Process names.
     */
    public static class ProcessName {

        /** The Constant QEMU. */
        public static final String QEMU = "qemu";

        /** The Constant SWTPM. */
        public static final String SWTPM = "swtpm";

        /** The Constant CLOUD_INIT_IMG. */
        public static final String CLOUD_INIT_IMG = "cloudInitImg";
    }

}
