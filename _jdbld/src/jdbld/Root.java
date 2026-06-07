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

import java.io.IOException;
import java.util.stream.Stream;
import static org.jdrupes.builder.api.Intent.*;

import org.jdrupes.builder.api.ExecResult;
import org.jdrupes.builder.api.Project;
import org.jdrupes.builder.api.ResourceType;
import static org.jdrupes.builder.api.ResourceType.*;
import org.jdrupes.builder.core.AbstractRootProject;
import org.jdrupes.builder.eclipse.EclipseConfiguration;
import org.jdrupes.builder.ext.nodejs.NpmExecutor;
import org.jdrupes.builder.mvnrepo.MvnRepoLookup;
import org.jdrupes.builder.java.Javadoc;
import static org.jdrupes.builder.java.JavaTypes.*;
import static org.jdrupes.builder.mvnrepo.MvnProperties.GroupId;

public class Root extends AbstractRootProject {

    @Override
    public void prepareProject(Project project) throws Exception {
        project.set(GroupId, "org.jdrupes.vmoperator");
        ProjectPreparation.setupVersion(project);
        ProjectPreparation.setupCommonGenerators(project);
        ProjectPreparation.setupEclipseConfigurator(project);
    }

    public Root() throws IOException {
        super(name("VM-Operator"));

        dependency(Expose, project(Util.class));
        dependency(Expose, project(Common.class));
        dependency(Expose, project(RunnerQemu.class));
        dependency(Expose, project(ManagerEvents.class));
        dependency(Expose, project(Manager.class));
        dependency(Expose, project(VmMgmt.class));
        dependency(Expose, project(VmAccess.class));

        // For npm init
        dependency(Consume,
            ProjectPreparation.prepareNpm(new NpmExecutor(this)));

        // Build javadoc
        generator(Javadoc::new)
            .destination(rootProject().directory().resolve("webpages/javadoc"))
            .tagletpath(new MvnRepoLookup()
                .resolve("org.jdrupes.taglets:plantuml-taglet:3.1.0",
                    "net.sourceforge.plantuml:plantuml:1.2023.11")
                .resources(of(ClasspathElementType).using(Supply, Expose)))
            .taglets(Stream.of("org.jdrupes.taglets.plantUml.PlantUml",
                "org.jdrupes.taglets.plantUml.StartUml",
                "org.jdrupes.taglets.plantUml.EndUml"))
            .addSources(resources(of(JavaSourceTreeType)))
            .options("-overview", directory().resolve("overview.md").toString())
            .options("--add-stylesheet",
                directory().resolve("misc/javadoc-overwrites.css").toString())
            .options("--add-script",
                directory().resolve("misc/highlight.min.js").toString())
            .options("--add-script",
                directory().resolve("misc/highlight-all.js").toString())
            .options("--add-stylesheet",
                directory().resolve("misc/highlight-default.css").toString())
            .options("-bottom",
                readString(directory().resolve("misc/javadoc.bottom.txt")))
            .options("--allow-script-in-comments")
            .options("-linksource")
            .options("-link",
                "https://docs.oracle.com/en/java/javase/21/docs/api/")
            .options("-quiet");

        // Commands
        commandAlias("build").projects("**")
            .resources(of(new ResourceType<ContainerImage>() {}).usingAll());
        commandAlias("test").projects("**")
            .resources(of(TestResultType).using(Supply));
        commandAlias("javadoc").resources(of(JavadocDirectoryType));
        commandAlias("eclipse").projects("**")
            .resources(of(new ResourceType<EclipseConfiguration>() {}));
        commandAlias("publish").projects("**")
            .resources(of(new ResourceType<ContainerPublication>() {}));
        commandAlias("test-publication").projects("**").resources(of(
            new ResourceType<ExecResult<?>>() {}).withName("test-publisher"));
    }
}
