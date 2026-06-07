package jdbld;

import java.nio.file.Path;
import org.jdrupes.builder.api.FileResource;
import org.jdrupes.builder.api.ResourceFactory;
import org.jdrupes.builder.api.ResourceType;

public interface ContainerPublication extends FileResource {

    /// Represents the publication of a container image. We use a file
    /// to mirror the timestamp because retrieving the
    /// timestamp is not trivial.
    ///
    /// @return the resource
    ///
    static ContainerPublication of(Path path) {
        return ResourceFactory.create(new ResourceType<>() {}, path);
    }
}
