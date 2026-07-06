package jdbld;

import org.eclipse.jgit.api.Git;
import org.jdrupes.builder.api.PropertyKey;

/// Extra properties.
///
@SuppressWarnings("PMD.FieldNamingConventions")
final class ExtProps {

    public static final PropertyKey<Git> GitApi
        = new PropertyKey<Git>(Git.class);
}