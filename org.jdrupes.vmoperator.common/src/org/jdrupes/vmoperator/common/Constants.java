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

    /** The Constant VM_OP_NAME. */
    public static final String VM_OP_NAME = "vm-operator";

    /** The Constant VM_OP_GROUP. */
    public static final String VM_OP_GROUP = "vmoperator.jdrupes.org";

    /** The Constant VM_OP_KIND_VM. */
    public static final String VM_OP_KIND_VM = "VirtualMachine";

    /** The Constant VM_OP_KIND_VM_POOL. */
    public static final String VM_OP_KIND_VM_POOL = "VmPool";

    /** The Constant COMP_DISPLAY_SECRETS. */
    public static final String COMP_DISPLAY_SECRET = "display-secret";

    /** The Constant DATA_DISPLAY_PASSWORD. */
    public static final String DATA_DISPLAY_PASSWORD = "display-password";

    /** The Constant DATA_PASSWORD_EXPIRY. */
    public static final String DATA_PASSWORD_EXPIRY = "password-expiry";
}
