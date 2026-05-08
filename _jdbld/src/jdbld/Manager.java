/*
 * VM-Operator
 * Copyright (C) 2026 Michael N. Lipp
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
import org.jdrupes.builder.java.ApplicationBuilder;
import org.jdrupes.builder.java.JavaLibraryProject;
import static org.jdrupes.builder.java.JavaTypes.*;

import java.util.List;

import org.jdrupes.builder.mvnrepo.MvnRepoLookup;

public class Manager extends AbstractProject implements JavaLibraryProject {

    public Manager() {
        super(name("org.jdrupes.vmoperator.manager"));
        dependency(Expose, project(ManagerEvents.class));
        dependency(Expose, new MvnRepoLookup().resolve(
            "commons-cli:commons-cli:1.5.0",
            "org.jgrapes:org.jgrapes.util:[1.38.1,2)",
            "org.jgrapes:org.jgrapes.io:[2.12.1,3)",
            "org.jgrapes:org.jgrapes.http:[3.5.0,4)",
            "org.jgrapes:org.jgrapes.webconsole.base:[2.3.0,3)",
            "org.jgrapes:org.jgrapes.webconsole.vuejs:[1.8.0,2)",
            "org.jgrapes:org.jgrapes.webconsole.rbac:[1.4.0,2)",
            "org.jgrapes:org.jgrapes.webconlet.oidclogin:[1.7.0,2)",
            "org.jgrapes:org.jgrapes.webconlet.markdowndisplay:[1.2.0,2)"));
        dependency(Reveal, new MvnRepoLookup().resolve(
            "org.jgrapes:org.jgrapes.webconlet.sysinfo:[1.4.0,2)",
            "org.jgrapes:org.jgrapes.webconlet.logviewer:[0.2.0,2)",
            "com.electronwill.night-config:yaml:[3.6.7,3.7)",
            "org.eclipse.angus:angus-activation:[1.0.0,2.0.0)",
            "org.slf4j:slf4j-jdk14:[2.0.7,3)",
            "org.apache.logging.log4j:log4j-to-jul:2.20.0"));
        dependency(Reveal, project(VmMgmt.class));
        dependency(Reveal, project(VmAccess.class));
        dependency(Forward, ApplicationBuilder::new)
            .executableName("vm-manager")
            .applicationJvmOpts(l -> l.addAll(List.of(
                "-Xmx128m",
                "-XX:+UseParallelGC",
                "-Djava.util.logging.manager=org.jdrupes.vmoperator.util.LongLoggingManager")))
            .mainClassName("org.jdrupes.vmoperator.manager.Manager")
            .add(resources(
                of(LibraryJarFileType).using(Supply, Expose, Reveal)));
    }
}

// Update favicon:
// # Convert with inkscape to png because convert cannot handle svg 
// # background transparency, then
// convert VM-Operator.png -background transparent \
//   -define icon:auto-resize=256,64,48,32,16 favicon.ico
