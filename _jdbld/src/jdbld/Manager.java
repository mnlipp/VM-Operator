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

import static jdbld.ExtProps.GitApi;
import org.eclipse.jgit.api.Git;
import static org.jdrupes.builder.api.Intent.*;

import org.jdrupes.builder.api.MergedTestProject;
import org.jdrupes.builder.api.ResourceType;
import org.jdrupes.builder.core.AbstractProject;
import org.jdrupes.builder.core.ScriptExecutor;
import org.jdrupes.builder.distribution.ApplicationBuilder;
import static org.jdrupes.builder.distribution.DistributionTypes.*;
import org.jdrupes.builder.java.JavaLibraryProject;
import org.jdrupes.builder.java.JavaProject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.jdrupes.builder.mvnrepo.MvnRepoLookup;

public class Manager extends AbstractProject implements JavaLibraryProject {

    public Manager() throws IOException {
        super(name("org.jdrupes.vmoperator.manager"));
        dependency(Consume, project(ManagerEvents.class));
        dependency(Consume, new MvnRepoLookup().resolve(
            "commons-cli:commons-cli:1.5.0",
            "org.jgrapes:org.jgrapes.util:[1.38.1,2)",
            "org.jgrapes:org.jgrapes.io:[2.12.1,3)",
            "org.jgrapes:org.jgrapes.http:[3.5.0,4)",
            "org.jgrapes:org.jgrapes.webconsole.base:[2.3.0,3)",
            "org.jgrapes:org.jgrapes.webconsole.vuejs:[1.8.0,2)",
            "org.jgrapes:org.jgrapes.webconsole.rbac:[1.4.0,2)",
            "org.jgrapes:org.jgrapes.webconlet.oidclogin:[1.7.0,2)",
            "org.jgrapes:org.jgrapes.webconlet.markdowndisplay:[1.2.0,2)"));
        dependency(Forward, new MvnRepoLookup().resolve(
            "org.jgrapes:org.jgrapes.webconlet.sysinfo:[1.4.0,2)",
            "org.jgrapes:org.jgrapes.webconlet.logviewer:[0.2.0,2)",
            "com.electronwill.night-config:yaml:[3.6.7,3.7)",
            "org.eclipse.angus:angus-activation:[1.0.0,2.0.0)",
            "org.slf4j:slf4j-jdk14:[2.0.7,3)",
            "org.apache.logging.log4j:log4j-to-jul:2.20.0"));
        dependency(Forward, project(VmMgmt.class));
        dependency(Forward, project(VmAccess.class));
        dependency(Supply, ApplicationBuilder::new)
            .executableName("vm-manager")
            .applicationJvmOpts(l -> l.addAll(List.of(
                "-Xmx128m",
                "-XX:+UseParallelGC",
                "-Djava.util.logging.manager=org.jdrupes.vmoperator.util.LongLoggingManager")))
            .mainClassName("org.jdrupes.vmoperator.manager.Manager")
            .addFrom(this);

        // Container image build
        var branch = ((Git) get(GitApi)).getRepository().getBranch();
        dependency(Supply, ScriptExecutor::new)
            .name("VM-Operator-container-builder")
            .required(resources(of(ApplicationTarFileType).using(Supply)))
            .required(Path.of(
                "src/org/jdrupes/vmoperator/manager/Containerfile"))
            .script(
                """
                        mkdir -p build/install/vm-manager && \
                        tar -C build/install/vm-manager -xf $1 && \
                        podman build --pull=always -t $2 \
                            -f src/org/jdrupes/vmoperator/manager/Containerfile . && \
                        mkdir -p build/generated && \
                        touch build/generated/ContainerImage.tstamp
                        """)
            .args(resources(of(ApplicationTarFileType).using(Supply)).limit(1)
                .map(f -> f.path().toString()))
            .args(name() + ":" + branch.replace('/', '-'))
            .provideResources(of(new ResourceType<ContainerImage>() {}),
                _ -> Stream.of(ContainerImage.of(buildDirectory()
                    .resolve("generated/ContainerImage.tstamp"))));
        var registry = context().property("docker.registry", "");
        dependency(Supply, ScriptExecutor::new)
            .name("VM-Operator-publisher")
            .required(resources(
                of(new ResourceType<ContainerImage>() {}).using(Supply)))
            .script("""
                    podman push --tls-verify=false $2 $1/$2 && \
                        touch build/generated/ContainerPublication.tstamp
                    """)
            .args(registry, name() + ":" + branch.replace('/', '-'))
            .provideResources(of(new ResourceType<ContainerPublication>() {}),
                _ -> Stream.of(ContainerImage.of(buildDirectory()
                    .resolve("generated/ContainerPublication.tstamp"))));
        dependency(Supply, ScriptExecutor::new)
            .name("test-publisher")
            .required(resources(
                of(new ResourceType<ContainerImage>() {}).using(Supply)))
            .script("podman push --tls-verify=false $1 $2/$3")
            .args(name() + ":" + branch.replace('/', '-'), registry,
                name() + ":test");
    }

    public static class ManagerTest extends AbstractProject
            implements JavaProject, MergedTestProject {

        public ManagerTest() {
            super(parent(Manager.class));
            dependency(Consume, project(Manager.class));
        }
    }
}

// Update favicon:
// # Convert with inkscape to png because convert cannot handle svg 
// # background transparency, then
// convert VM-Operator.png -background transparent \
//   -define icon:auto-resize=256,64,48,32,16 favicon.ico
