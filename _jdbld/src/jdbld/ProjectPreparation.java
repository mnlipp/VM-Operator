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

import static java.util.jar.Attributes.Name.IMPLEMENTATION_TITLE;
import static java.util.jar.Attributes.Name.IMPLEMENTATION_VENDOR;
import static java.util.jar.Attributes.Name.IMPLEMENTATION_VERSION;
import static jdbld.ExtProps.GitApi;
import static org.jdrupes.builder.api.Intent.Consume;
import static org.jdrupes.builder.api.Intent.Supply;
import static org.jdrupes.builder.api.CoreProperties.*;
import static org.jdrupes.builder.api.ResourceType.ExecResultType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.jar.Attributes;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.jdrupes.builder.api.BuildException;
import org.jdrupes.builder.api.MergedTestProject;
import org.jdrupes.builder.api.Project;
import org.jdrupes.builder.api.RootProject;
import org.jdrupes.builder.eclipse.EclipseConfigurator;
import org.jdrupes.builder.ext.nodejs.NpmExecutor;
import org.jdrupes.builder.java.JavaCompiler;
import org.jdrupes.builder.java.JavaLibraryProject;
import org.jdrupes.builder.java.JavaProject;
import org.jdrupes.builder.java.JavaResourceCollector;
import org.jdrupes.builder.java.LibraryBuilder;
import org.jdrupes.builder.junit.JUnitTestRunner;
import org.jdrupes.builder.mvnrepo.MvnRepoLookup;
import org.jdrupes.gitversioning.api.VersionEvaluator;
import org.jdrupes.gitversioning.core.DefaultTagFilter;
import org.jdrupes.gitversioning.core.MavenStyleTagProcessor;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import static org.jdrupes.builder.mvnrepo.MvnProperties.*;

/// The Class ProjectPreparation.
///
public class ProjectPreparation {

    public static void setupVersion(Project project)
            throws IOException, GitAPIException, InvalidPatternException {
        if (project instanceof RootProject) {
            project.set(GitApi, Git.open(project.directory().toFile()));
        }

        // Use shortened project name for tags
        var shortened = project.name().startsWith(project.get(GroupId) + ".")
            ? project.name()
                .substring(project.<String> get(GroupId).length() + 1)
            : project.name();
        if ("manager".equals(shortened)) {
            shortened = "manager-app";
        }
        var tagPrefix = shortened.replace('.', '-') + "-";

        var evaluator = VersionEvaluator
            .forRepository(project.<Git> get(GitApi).getRepository())
            .tagFilter(new DefaultTagFilter().prepend(tagPrefix))
            .tagProcessor(new MavenStyleTagProcessor()
                .ignoreBranches("testing/.*", "release/.*", "develop/.*"));
        project.set(Version, evaluator.version());
    }

    public static void setupCommonGenerators(Project project) {
        if (project instanceof JavaProject) {
            if (!(project instanceof MergedTestProject)) {
                project.generator(JavaCompiler::new)
                    .addSources(Path.of("src"), "**/*.java")
                    .options("--release", "21");
                project.generator(JavaResourceCollector::new)
                    .add(Path.of("resources"), "**/*");
            } else {
                project.generator(JavaCompiler::new).addSources(Path.of("test"),
                    "**/*.java").options("--release", "21");
                project.generator(JavaResourceCollector::new).add(Path.of(
                    "test-resources"), "**/*");
                project.dependency(Consume, new MvnRepoLookup()
                    .resolve("junit:junit:4.13.2")
                    .bom("org.junit:junit-bom:5.14.2")
                    .resolve("org.junit.jupiter:junit-jupiter-api")
                    .resolve("org.junit.jupiter:junit-jupiter-params")
                    .resolve("org.junit.jupiter:junit-jupiter-engine",
                        "org.junit.vintage:junit-vintage-engine",
                        "net.jodah:concurrentunit:0.4.2"));
                project.dependency(Supply, JUnitTestRunner::new);
            }
        }
        if (project instanceof JavaLibraryProject) {
            var gen = project.generator(LibraryBuilder::new)
                .addFrom(project.providers().select(Supply))
                .attributes(Map.entry(
                    IMPLEMENTATION_TITLE, project.name()),
                    Map.entry(IMPLEMENTATION_VERSION, project.get(Version)),
                    Map.entry(IMPLEMENTATION_VENDOR,
                        "Michael N. Lipp (mnl@mnl.de)"));
            var git = project.<Git> get(GitApi);
            try {
                gen.attributes(
                    Map.entry(new Attributes.Name("Git-Descriptor"),
                        git.describe().setAll(true).call()
                            + (git.status().call().getUncommittedChanges()
                                .isEmpty() ? "" : "-dirty")),
                    Map.entry(new Attributes.Name("Git-SHA"),
                        git.getRepository().resolve("HEAD").getName()));
            } catch (GitAPIException | RevisionSyntaxException
                    | IOException e) {
                throw new BuildException().cause(e);
            }
        }
    }

    public static void setupEclipseConfigurator(Project project) {
        project.generator(new EclipseConfigurator(project)
            .adaptProjectConfiguration((Document doc,
                    Node buildSpec, Node natures) -> {
                if (project instanceof JavaProject) {
                    var cmd = buildSpec
                        .appendChild(doc.createElement("buildCommand"));
                    cmd.appendChild(doc.createElement("name"))
                        .appendChild(doc.createTextNode(
                            "net.sf.eclipsecs.core.CheckstyleBuilder"));
                    cmd.appendChild(doc.createElement("arguments"));
                    natures.appendChild(doc.createElement("nature"))
                        .appendChild(doc.createTextNode(
                            "net.sf.eclipsecs.core.CheckstyleNature"));
                    cmd = buildSpec
                        .appendChild(doc.createElement("buildCommand"));
                    cmd.appendChild(doc.createElement("name"))
                        .appendChild(doc.createTextNode(
                            "ch.acanda.eclipse.pmd.builder.PMDBuilder"));
                    cmd.appendChild(doc.createElement("arguments"));
                    natures.appendChild(doc.createElement("nature"))
                        .appendChild(doc.createTextNode(
                            "ch.acanda.eclipse.pmd.builder.PMDNature"));
                }
            }).adaptConfiguration(() -> {
                if (!(project instanceof JavaProject)) {
                    return;
                }
                try {
                    Files.copy(
                        Root.class.getResourceAsStream("net.sf.jautodoc.prefs"),
                        project.directory()
                            .resolve(".settings/net.sf.jautodoc.prefs"),
                        StandardCopyOption.REPLACE_EXISTING);
                    Files.copy(Root.class.getResourceAsStream("checkstyle"),
                        project.directory().resolve(".checkstyle"),
                        StandardCopyOption.REPLACE_EXISTING);
                    Files.copy(Root.class.getResourceAsStream("eclipse-pmd"),
                        project.directory().resolve(".eclipse-pmd"),
                        StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new BuildException().cause(e);
                }
            }));
    }

    public static NpmExecutor prepareNpm(NpmExecutor executor) {
        executor.nodeJsVersion("25.7.0").name("npmInstall");
        var project = executor.project();
        if (!(project instanceof RootProject)) {
            executor.required(project.rootProject()
                .resources(project.of(ExecResultType).using(Consume)
                    .withName("npmInstall")));
        }
        return executor;
    }
}
