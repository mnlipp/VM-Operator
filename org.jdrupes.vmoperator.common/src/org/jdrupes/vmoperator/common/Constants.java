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

// TODO: Auto-generated Javadoc
/**
 * Some constants.
 */
@SuppressWarnings("PMD.DataClass")
public class Constants {

    /** The Constant APP_NAME. */
    public static final String APP_NAME = "vm-runner";

    /** The Constant VM_OP_NAME. */
    public static final String VM_OP_NAME = "vm-operator";

    /**
     * Constants related to the CRD.
     */
    @SuppressWarnings("PMD.ShortClassName")
    public static class Crd {
        /** The Constant GROUP. */
        public static final String GROUP = "vmoperator.jdrupes.org";

        /** The Constant KIND_VM. */
        public static final String KIND_VM = "VirtualMachine";

        /** The Constant KIND_VM_POOL. */
        public static final String KIND_VM_POOL = "VmPool";
    }

    /**
     * Status related constants.
     */
    public static class Status {
        /** The Constant CPUS. */
        public static final String CPUS = "cpus";

        /** The Constant RAM. */
        public static final String RAM = "ram";

        /** The Constant OSINFO. */
        public static final String OSINFO = "osinfo";

        /** The Constant DISPLAY_PASSWORD_SERIAL. */
        public static final String DISPLAY_PASSWORD_SERIAL
            = "displayPasswordSerial";

        /** The Constant LOGGED_IN_USER. */
        public static final String LOGGED_IN_USER = "loggedInUser";

        /** The Constant CONSOLE_CLIENT. */
        public static final String CONSOLE_CLIENT = "consoleClient";

        /** The Constant CONSOLE_USER. */
        public static final String CONSOLE_USER = "consoleUser";

        /** The Constant ASSIGNMENT. */
        public static final String ASSIGNMENT = "assignment";

        /** The Constant COND_RUNNING. */
        public static final String COND_RUNNING = "Running";

        /** The Constant COND_BOOTED. */
        public static final String COND_BOOTED = "Booted";

        /** The Constant COND_VMOP_AGENT. */
        public static final String COND_VMOP_AGENT = "VmopAgentConnected";

        /** The Constant COND_USER_LOGGED_IN. */
        public static final String COND_USER_LOGGED_IN = "UserLoggedIn";

        /** The Constant COND_CONSOLE. */
        public static final String COND_CONSOLE = "ConsoleConnected";
    }

    /**
     * DisplaySecret related constants.
     */
    public static class DisplaySecret {

        /** The Constant NAME. */
        public static final String NAME = "display-secret";

        /** The Constant PASSWORD. */
        public static final String PASSWORD = "display-password";

        /** The Constant EXPIRY. */
        public static final String EXPIRY = "password-expiry";

    }
}
