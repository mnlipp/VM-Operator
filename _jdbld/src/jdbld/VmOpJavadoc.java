/*
 * JDrupes Builder
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

import com.google.common.flogger.FluentLogger;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jdrupes.builder.api.BuildException;
import org.jdrupes.builder.api.MergedTestProject;

import static org.jdrupes.builder.api.Intent.*;
import org.jdrupes.builder.api.Project;
import static org.jdrupes.builder.api.CoreProperties.*;
import org.jdrupes.builder.api.Renamable;
import org.jdrupes.builder.api.Resource;
import org.jdrupes.builder.api.ResourceRequest;
import static org.jdrupes.builder.api.ResourceType.*;
import org.jdrupes.builder.core.AbstractGenerator;
import org.jdrupes.builder.java.ClassTree;
import org.jdrupes.builder.java.ClasspathElement;
import org.jdrupes.builder.java.JarFile;
import org.jdrupes.builder.java.JavadocDirectory;
import static org.jdrupes.builder.java.JavaTypes.*;
import org.jdrupes.builder.mvnrepo.MvnRepoLookup;

/// Very special javadoc generation.
///
public class VmOpJavadoc extends AbstractGenerator implements Renamable {

    @SuppressWarnings("unused")
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    /**
     * Instantiates a new vm op javadoc.
     *
     * @param project the project
     */
    public VmOpJavadoc(Project project) {
        super(project);
        rename(VmOpJavadoc.class.getSimpleName() + " in " + project);
    }

    @Override
    protected <T extends Resource> Collection<T>
            doProvide(ResourceRequest<T> requested) {
        if (!requested.accepts(JavadocDirectoryType)
            && !requested.accepts(CleanlinessType)) {
            return Collections.emptyList();
        }

        // Get destination and check if we only have to cleanup.
        var destDir = project().buildDirectory().resolve("javadoc");
        var generated = ClassTree.of(project(), destDir);
        if (requested.accepts(CleanlinessType)) {
            generated.cleanup();
            destDir.toFile().delete();
            return Collections.emptyList();
        }

        // Sources
        var sourcepath = project().resources(of(JavaSourceTreeType)
            .using(Expose)).map(t -> t.root().toString())
            .collect(Collectors.joining(File.pathSeparator));

        // Classpath
        var classpath = elementsToPath(project().rootProject().projects("**")
            .filter(p -> !(p instanceof MergedTestProject))
            .flatMap(p -> p.resources(of(ClasspathElementType)
                .using(Consume, Reveal, Expose))));

        // Build command
        List<String> command = List.of(
            context().property("javadocJava", "java"),
            "--add-exports=jdk.compiler/com.sun.tools.doclint=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
            "--add-exports=jdk.javadoc/jdk.javadoc.internal.tool=ALL-UNNAMED",
            "--add-exports=jdk.javadoc/jdk.javadoc.internal.doclets.toolkit=ALL-UNNAMED",
            "--add-opens=jdk.javadoc/jdk.javadoc.internal.doclets.toolkit"
                + ".resources.releases=ALL-UNNAMED",
            "-Duser.language=en_US", "-Duser.region=US",
            "jdk.javadoc.internal.tool.Main",
            "-doctitle", String.format(
                "VM-Operator (runner.qemu-%s, manager-%s)",
                project().project(RunnerQemu.class).get(Version),
                project().project(Manager.class).get(Version)),
            "-use", "-linksource",
            "-link", "https://docs.oracle.com/en/java/javase/21/docs/api/",
            "-link", "https://mnlipp.github.io/jdrupes-httpcodec/javadoc/",
            "-link", "https://jakarta.ee/specifications/mail/2.1/apidocs/",
            "--add-exports",
            "jdk.javadoc/jdk.javadoc.internal.tool=ALL-UNNAMED",
            "--add-exports",
            "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports=jdk.javadoc/jdk.javadoc.internal.doclets.formats.html=ALL-UNNAMED",
            "-doclet", "org.jdrupes.mdoclet.MDoclet",
            "-docletpath", elementsToPath(new MvnRepoLookup()
                .resolve("org.jdrupes.mdoclet:doclet:4.2.0")
                .resources(of(ClasspathElementType).using(Supply, Expose))),
            "--disable-auto-highlight",
            "-taglet", "org.jdrupes.taglets.plantUml.PlantUml",
            "-taglet", "org.jdrupes.taglets.plantUml.StartUml",
            "-taglet", "org.jdrupes.taglets.plantUml.EndUml",
            "-tagletpath", elementsToPath(new MvnRepoLookup()
                .resolve("org.jdrupes.taglets:plantuml-taglet:3.1.0")
                .resources(of(ClasspathElementType).using(Supply, Expose))),
            "-overview", project().rootProject()
                .directory().resolve("overview.md").toString(),
            "-bottom", project().rootProject()
                .directory().resolve("misc/javadoc.bottom.txt").toString(),
            "--allow-script-in-comments",
            "-Xdoclint:-html",
            "--add-stylesheet", project().rootProject()
                .directory().resolve("misc/javadoc-overwrites.css").toString(),
            "-quiet",
            "-d", project().buildDirectory().resolve("javadoc").toString(),
            "-cp", classpath,
            "-sourcepath", sourcepath,
            "-subpackages", "org.jdrupes");

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        try {
            Process process = processBuilder.start();
            copyData(process.getInputStream(), context().out());
            copyData(process.getErrorStream(), context().error());
            int ret = process.waitFor();
            if (ret != 0) {
                throw new BuildException().from(this)
                    .message("Process javadoc returned: %d", ret);
            }
            @SuppressWarnings("unchecked")
            var result = (Collection<T>) List
                .of(JavadocDirectory.of(project(), destDir));
            return result;
        } catch (IOException | InterruptedException e) {
            throw new BuildException().from(this).cause(e);
        }
    }

    private String elementsToPath(Stream<ClasspathElement> elements) {
        return elements.<Path> mapMulti((e, consumer) -> {
            if (e instanceof ClassTree classTree) {
                consumer.accept(classTree.root());
            } else if (e instanceof JarFile jarFile) {
                consumer.accept(jarFile.path());
            }
        }).collect(Collectors.toSet()).stream().map(Path::toString)
            .collect(Collectors.joining(File.pathSeparator));
    }

    private void copyData(InputStream source, OutputStream sink) {
        Thread.startVirtualThread(() -> {
            try (source) {
                source.transferTo(sink);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}