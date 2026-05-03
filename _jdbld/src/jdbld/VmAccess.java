/*
 * JGrapes Event Driven Framework
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

import java.nio.file.Path;
import java.util.stream.Stream;
import static org.jdrupes.builder.api.Intent.*;
import static org.jdrupes.builder.api.ResourceType.BaseFileTreeType;

import org.jdrupes.builder.api.InputTree;
import org.jdrupes.builder.core.AbstractProject;
import org.jdrupes.builder.core.FileTreeBuilder;
import org.jdrupes.builder.core.FileTreeBuilder.Source;
import org.jdrupes.builder.ext.nodejs.NpmExecutor;
import org.jdrupes.builder.java.JavaLibraryProject;
import org.jdrupes.builder.java.JavaProject;
import org.jdrupes.builder.java.JavaResourceTree;
import static org.jdrupes.builder.java.JavaTypes.*;
import static org.jdrupes.builder.mvnrepo.MvnRepoTypes.*;
import org.jdrupes.builder.mvnrepo.MvnRepoLookup;

public class VmAccess extends AbstractProject
        implements JavaProject, JavaLibraryProject {

    public VmAccess() {
        super(name("org.jdrupes.vmoperator.vmaccess"));
        dependency(Reveal, project(ManagerEvents.class));
        dependency(Reveal, new MvnRepoLookup().resolve(
            "org.jgrapes:org.jgrapes.webconsole.base:[2.1.0,3)",
            "org.jgrapes:org.jgrapes.webconsole.provider.vue:[1,2)",
            "org.jgrapes:org.jgrapes.webconsole.provider.jgwcvuecomponents:[1.2,2)"));
        dependency(Consume, FileTreeBuilder::new)
            .into(buildDirectory().resolve("unpacked"))
            .add(resources(of(MvnRepoLibraryJarFileType).using(Reveal))
                .filter(j -> j.path().getFileName().toString().startsWith(
                    "org.jgrapes.webconsole.base")
                    || j.path().getFileName().toString().startsWith(
                        "org.jgrapes.webconsole.provider"))
                .map(j -> Source.of(InputTree.of(j))))
            .provideResources(of(JavaResourceTreeType));
        ProjectPreparation.prepareNpm(dependency(Supply, NpmExecutor::new))
            .args("run", "build").required(Path.of("src"), "**/*.ts")
            .required(Path.of("tsconfig.json"))
            .required(Path.of("rollup.config.mjs"))
            .required(resources(of(BaseFileTreeType).using(Consume)))
            .output(p -> Stream.of(JavaResourceTree.of(p,
                p.buildDirectory().resolve("generated/resources"), "**/*")))
            .provideResources(of(JavaResourceTreeType));
    }
}
