package jdbld;

import java.nio.file.Path;
import org.jdrupes.builder.api.FileResource;
import org.jdrupes.builder.api.ResourceFactory;
import org.jdrupes.builder.api.ResourceType;

public interface ContainerImage extends FileResource {

    /// Represents a container image resulting from a podman build step.
    /// We use a file to mirror the timestamp because retrieving the
    /// timestamp is not trivial.
    ///
    /// @return the resource
    ///
    static ContainerImage of(Path path) {
        return ResourceFactory.create(new ResourceType<>() {}, path);
    }
}
