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
import java.net.URI;

import static org.jdrupes.builder.api.Intent.*;
import org.eclipse.aether.repository.RemoteRepository;
import org.jdrupes.builder.api.ExecResult;
import org.jdrupes.builder.api.Project;
import org.jdrupes.builder.api.ResourceType;
import static org.jdrupes.builder.api.ResourceType.*;
import org.jdrupes.builder.core.AbstractRootProject;
import org.jdrupes.builder.eclipse.EclipseConfiguration;
import static org.jdrupes.builder.ext.git.GitTypes.*;
import org.jdrupes.builder.ext.nodejs.NpmExecutor;
import org.jdrupes.builder.mvnrepo.MavenContext;
import org.jdrupes.builder.mvnrepo.MvnVersionType;

import static org.jdrupes.builder.mvnrepo.MvnProperties.*;
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
        set(LookupRepositories, new RemoteRepository[] {
            MavenContext.mavenCentral(),
            MavenContext.jdbldDistribution(),
            MavenContext.createRepository("jgrapes-distribution",
                URI.create("https://codeberg.org/api/packages/JGrapes/maven"),
                MvnVersionType.RELEASE)
        });

        dependency(Expose, project(Util.class));
        dependency(Expose, project(Common.class));
        dependency(Expose, project(RunnerQemu.class));
        dependency(Expose, project(ManagerEvents.class));
        dependency(Expose, project(Manager.class));
        dependency(Expose, project(VmMgmt.class));
        dependency(Expose, project(VmAccess.class));
        dependency(Expose, project(SpiceSquid.class));

        // For npm init
        dependency(Consume,
            ProjectPreparation.prepareNpm(new NpmExecutor(this)));

        // Build javadoc
        generator(VmOpJavadoc::new);

        // Commands
        commandAlias("version").projects("**")
            .resources(of(ProjectVersionType).using(Supply));
        commandAlias("build").projects("**")
            .resources(of(new ResourceType<ContainerImage>() {}).usingAll());
        commandAlias("test").projects("**")
            .resources(of(TestResultType).using(Supply));
        commandAlias("javadoc").resources(of(JavadocDirectoryType));
        commandAlias("eclipse").projects("**")
            .resources(of(new ResourceType<EclipseConfiguration>() {}));
        commandAlias("publication").projects("**")
            .resources(of(new ResourceType<ContainerPublication>() {}));
        commandAlias("test-publication").projects("**").resources(of(
            new ResourceType<ExecResult<?>>() {}).withName("test-publisher"));
        commandAlias("releaseTag").description("Create a release tag")
            .projects("**").resources(of(GitVersionTagType).using(Supply));
    }
}
