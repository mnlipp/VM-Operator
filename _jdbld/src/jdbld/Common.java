/*
 * VM-Operator
 * Copyright (C) 2025 Michael N. Lipp
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

package jdbld;

import static org.jdrupes.builder.api.Intent.*;
import org.jdrupes.builder.core.AbstractProject;
import org.jdrupes.builder.java.JavaLibraryProject;
import org.jdrupes.builder.mvnrepo.MvnRepoLookup;

public class Common extends AbstractProject implements JavaLibraryProject {

    public Common() {
        super(name("org.jdrupes.vmoperator.common"));
        dependency(Expose, project(Util.class));
        dependency(Expose, new MvnRepoLookup().resolve(
            "org.jgrapes:org.jgrapes.core:[1.21.1,2)",
            "io.kubernetes:client-java:[19.0.0,20.0.0)",
            "org.yaml:snakeyaml:[2.4,3]",
            "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:[2.16.1,3]"));
    }
}
