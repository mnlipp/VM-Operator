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

package org.jdrupes.vmoperator.common;

/**
 * Some constants.
 */
@SuppressWarnings("PMD.DataClass")
public class Constants {

    /** The Constant APP_NAME. */
    public static final String APP_NAME = "vm-runner";

    /**
     * Constants related to the CRD.
     */
    @SuppressWarnings("PMD.ShortClassName")
    public static class Crd {

        /** The Constant NAME. */
        public static final String NAME = "vm-operator";

        /** The Constant GROUP. */
        public static final String GROUP = "vmoperator.jdrupes.org";

        /** The Constant KIND_VM. */
        public static final String KIND_VM = "VirtualMachine";

        /** The Constant KIND_VM_POOL. */
        public static final String KIND_VM_POOL = "VmPool";
    }

    /**
     * Constants for the display secret.
     */
    public static class DisplaySecret {

        /** The Constant NAME. */
        public static final String NAME = "display-secret";

        /** The Constant DISPLAY_PASSWORD. */
        public static final String DISPLAY_PASSWORD = "display-password";

        /** The Constant PASSWORD_EXPIRY. */
        public static final String PASSWORD_EXPIRY = "password-expiry";
    }

    /**
     * Constants for status fields.
     */
    public static class Status {

        /** The Constant LOGGED_IN_USER. */
        public static final String LOGGED_IN_USER = "loggedInUser";

    }
}
