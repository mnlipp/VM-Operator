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

import static jdbld.ExtProps.GitApi;
import static org.jdrupes.builder.api.Intent.*;
import static org.jdrupes.builder.distribution.DistributionTypes.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.jdrupes.builder.api.ResourceType;
import org.jdrupes.builder.core.AbstractProject;
import org.jdrupes.builder.core.ScriptExecutor;
import org.jdrupes.builder.distribution.ApplicationBuilder;
import org.jdrupes.builder.java.JavaLibraryProject;
import org.jdrupes.builder.mvnrepo.MvnRepoLookup;

public class RunnerQemu extends AbstractProject implements JavaLibraryProject {

    public RunnerQemu() throws IOException {
        super(name("org.jdrupes.vmoperator.runner.qemu"));
        dependency(Consume, new MvnRepoLookup().resolve(
            "org.jgrapes:org.jgrapes.core:[1.22.1,2)",
            "org.jgrapes:org.jgrapes.util:[1.38.1,2)",
            "org.jgrapes:org.jgrapes.io:[2.12.1,3)",
            "org.jgrapes:org.jgrapes.http:[3.5.0,4)",
            "commons-cli:commons-cli:1.5.0",
            "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:[2.21.4]"));
        dependency(Consume, project(Common.class));
        dependency(Forward, new MvnRepoLookup().resolve(
            "org.slf4j:slf4j-jdk14:[2.0.7,3)"));
        dependency(Supply, ApplicationBuilder::new)
            .executableName("vm-runner.qemu")
            .applicationJvmOpts(l -> l.addAll(List.of(
                "-Xmx32m",
                "-XX:+UseParallelGC",
                "-Djava.util.logging.manager=org.jdrupes.vmoperator.util.LongLoggingManager")))
            .mainClassName("org.jdrupes.vmoperator.runner.qemu.Runner")
            .addFrom(this);

        // Container image build
        containerBuild("arch");
        containerBuild("alpine");
    }

    private void containerBuild(String base) throws IOException {
        var branch = ((Git) get(GitApi)).getRepository().getBranch();
        var imageName = name() + "-" + base;
        dependency(Supply, ScriptExecutor::new)
            .name("VM-Runner.qemu-" + base + "-container-builder")
            .required(resources(of(ApplicationTarFileType).using(Supply)))
            .required(Path.of(
                "src/org/jdrupes/vmoperator/runner/qemu/Containerfile." + base))
            .script("""
                    rm -rf build/install/vm-runner.qemu && \
                    mkdir -p build/install/vm-runner.qemu && \
                    tar -C build/install/vm-runner.qemu -xf $2 && \
                    podman build --pull=always -t $3 \
                      -f src/org/jdrupes/vmoperator/runner/qemu/Containerfile.$1 . && \
                    mkdir -p build/generated && \
                    touch build/generated/ContainerImage-$1.tstamp
                    """)
            .args(base)
            .args(resources(of(ApplicationTarFileType).using(Supply)).limit(1)
                .map(f -> f.path().toString()))
            .args(imageName + ":" + branch.replace('/', '-'))
            .provideResources(of(new ResourceType<ContainerImage>() {}),
                _ -> Stream.of(ContainerImage.of(buildDirectory()
                    .resolve("generated/ContainerImage-" + base + ".tstamp"))));
        var registry = context().property("docker.registry", "");
        dependency(Supply, ScriptExecutor::new)
            .name("VM-Runner.qemu-" + base + "-publisher")
            .required(resources(
                of(new ResourceType<ContainerImage>() {}).using(Supply)))
            .script("""
                    podman push --tls-verify=false $2 $1/$2 && \
                    touch build/generated/ContainerPublication.tstamp
                    """)
            .args(registry, imageName + ":" + branch.replace('/', '-'))
            .provideResources(of(new ResourceType<ContainerPublication>() {}),
                _ -> Stream.of(ContainerImage.of(buildDirectory()
                    .resolve("generated/ContainerPublication-" + base
                        + ".tstamp"))));
        dependency(Supply, ScriptExecutor::new)
            .name("test-publisher")
            .required(resources(
                of(new ResourceType<ContainerImage>() {}).using(Supply)))
            .script("podman push --tls-verify=false $1 $2/$3")
            .args(imageName + ":" + branch.replace('/', '-'),
                registry, imageName + ":test");
    }
}
