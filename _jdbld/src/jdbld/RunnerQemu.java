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

public class RunnerQemu extends AbstractProject implements JavaLibraryProject {

    public RunnerQemu() {
        super(name("org.jdrupes.vmoperator.runner.qemu"));
        dependency(Consume, new MvnRepoLookup().resolve(
            "org.jgrapes:org.jgrapes.core:[1.22.1,2)",
            "org.jgrapes:org.jgrapes.util:[1.38.1,2)",
            "org.jgrapes:org.jgrapes.io:[2.12.1,3)",
            "org.jgrapes:org.jgrapes.http:[3.5.0,4)",
            "commons-cli:commons-cli:1.5.0",
            "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:[2.16.1]"));
        dependency(Consume, project(Common.class));
        dependency(Forward, new MvnRepoLookup().resolve(
            "org.slf4j:slf4j-jdk14:[2.0.7,3)"));
    }
}
