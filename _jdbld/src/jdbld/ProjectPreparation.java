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
import static org.jdrupes.builder.api.Intend.Supply;
import static org.jdrupes.builder.api.Project.Properties.Version;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.jdrupes.builder.api.BuildException;
import org.jdrupes.builder.api.Project;
import org.jdrupes.builder.api.RootProject;
import org.jdrupes.builder.eclipse.EclipseConfigurator;
import org.jdrupes.builder.java.JavaCompiler;
import org.jdrupes.builder.java.JavaLibraryProject;
import org.jdrupes.builder.java.JavaProject;
import org.jdrupes.builder.java.JavaResourceCollector;
import org.jdrupes.builder.java.LibraryGenerator;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import static org.jdrupes.builder.mvnrepo.MvnProperties.*;

/// The Class ProjectPreparation.
///
public class ProjectPreparation {

    private static GenericVersionScheme gvs = new GenericVersionScheme();

    public static String getLatestVersion(String prefix, Git git)
            throws IOException, GitAPIException {
        var tags = new HashMap<ObjectId, Ref>();
        git.tagList().call().forEach(ref -> {
            tags.put(ref.getPeeledObjectId(), ref);
        });

        var logIter = git.log().add(git.getRepository().resolve(Constants.HEAD))
            .call().iterator();
        int additional;

        git.log().add(git.getRepository().resolve(Constants.HEAD)).call()
            .forEach(commit -> {
                var ref = tags.get(commit.getId());
                if (ref != null) {
                    System.out.println(commit + " " + ref.getName());
                }
            });

        return git.tagList().call().stream()
            .map(c -> c.getName().substring("refs/tags/".length()))
            .filter(tn -> tn.startsWith(prefix))
            .map(tn -> tn.substring(prefix.length()))
            .sorted(new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    try {
                        Version v1 = gvs.parseVersion(o1);
                        Version v2 = gvs.parseVersion(o2);
                        return v2.compareTo(v1);
                    } catch (InvalidVersionSpecificationException e) {
                        return 0;
                    }
                }
            }).findFirst().orElse("0.0.0");
    }

    private static AbstractTreeIterator prepareTreeParser(Repository repository,
            String ref) throws IOException {
        // from the commit we can build the tree which allows us to construct
        // the TreeParser
        Ref head = repository.exactRef(ref);
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(head.getObjectId());
            RevTree tree = walk.parseTree(commit.getTree().getId());

            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }

            walk.dispose();

            return treeParser;
        }
    }

    public static boolean hasFileChangedSinceLastCommit(Git git)
            throws IOException, GitAPIException {
        Repository repositoryObject = git.getRepository();
        Iterable<DiffEntry> diff
            = git.diff().setOldTree(prepareTreeParser(repositoryObject, "HEAD"))
                .setNewTree(new FileTreeIterator(repositoryObject)).call();
//            for (DiffEntry entry : diff) {
//                if (entry.getChangeType() == DiffEntry.ChangeType.MODIFIED
//                    || entry.getChangeType() == DiffEntry.ChangeType.ADDED) {
//                    return true;
//                }
//            }
        diff.forEach(d -> {
            System.out.println(d);
        });
        return false;

    }

    public static void setupVersion(Project project)
            throws IOException, GitAPIException, InvalidPatternException {
        if (project instanceof RootProject) {
            project.set(GitApi, Git.open(project.directory().toFile()));
        }
        project.set(GroupId, "org.jdrupes.vmoperator");

        // Use shortened project name for tags
        var shortened = project.name().startsWith(project.get(GroupId) + ".")
            ? project.name()
                .substring(project.<String> get(GroupId).length() + 1)
            : project.name();
        if ("manager".equals(shortened)) {
            shortened = "manager-app";
        }
        var tagPrefix = shortened.replace('.', '-') + "-";
//        if (grgit.branch.current.name != "main"
//            && grgit.branch.current.name != "HEAD"
//            && !grgit.branch.current.name.startsWith("testing")
//            && !grgit.branch.current.name.startsWith("release")
//            && !grgit.branch.current.name.startsWith("develop")) {
//            tagName = tagName + grgit.branch.current.name.replace('/', '-') + "-"
//        }
//        project.ext.tagName = tagName

        System.out.println("vvv");
        System.out.println(tagPrefix);
        System.out
            .println(getLatestVersion(tagPrefix, project.<Git> get(GitApi)));

        var obj = project.<Git> get(GitApi).getRepository().exactRef("HEAD")
            .getTarget().getName();
        Repository.shortenRefName(obj);
        System.out.println(obj);
        hasFileChangedSinceLastCommit(project.<Git> get(GitApi));
        System.out.println("^^^");

        project.set(Version, "0.0.2");
    }

    public static void setupCommonGenerators(Project project) {
        if (project instanceof JavaProject) {
            project.generator(JavaCompiler::new)
                .addSources(Path.of("src"), "**/*.java")
                .options("--release", "21");
            project.generator(JavaResourceCollector::new)
                .add(Path.of("resources"), "**/*");
        }
        if (project instanceof JavaLibraryProject) {
            project.generator(LibraryGenerator::new)
                .from(project.providers(Supply))
                .attributes(Map.of(
                    IMPLEMENTATION_TITLE, project.name(),
                    IMPLEMENTATION_VERSION, project.get(Version),
                    IMPLEMENTATION_VENDOR, "Michael N. Lipp (mnl@mnl.de)")
                    .entrySet().stream());
//          Git-Descriptor: c6c635842
//          Git-SHA: c6c6358426287c0258edd9869adf3db3d9bbec17

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
                    throw new BuildException(e);
                }
            }));
    }
}
