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
import org.jdrupes.builder.api.ResourceType;
import org.jdrupes.builder.core.AbstractProject;
import org.jdrupes.builder.core.ScriptExecutor;
import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

public class SpiceSquid extends AbstractProject {

    public SpiceSquid() throws IOException {
        super(name("spice-squid"));

        // Container image build
        var branch = ((Git) get(GitApi)).getRepository().getBranch();
        dependency(Supply, ScriptExecutor::new)
            .name("spice-squid-container-builder")
            .required(Path.of("Containerfile"))
            .script(
                """
                        podman build --pull=always -t $1 \
                          -f Containerfile . && \
                        mkdir -p build/generated && \
                        touch build/generated/ContainerImage.tstamp
                        """)
            .args(name() + ":" + branch.replace('/', '-'))
            .provideResources(of(new ResourceType<ContainerImage>() {}),
                _ -> Stream.of(ContainerImage.of(buildDirectory()
                    .resolve("generated/ContainerImage.tstamp"))));
        var registry = context().property("docker.registry", "");
        dependency(Supply, ScriptExecutor::new)
            .name("spice-squid-publisher")
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
}

// Update favicon:
// # Convert with inkscape to png because convert cannot handle svg 
// # background transparency, then
// convert VM-Operator.png -background transparent \
//   -define icon:auto-resize=256,64,48,32,16 favicon.ico
