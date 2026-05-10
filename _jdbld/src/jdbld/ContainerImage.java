package jdbld;

import java.nio.file.Path;
import org.jdrupes.builder.api.FileResource;
import org.jdrupes.builder.api.ResourceFactory;
import org.jdrupes.builder.api.ResourceType;

public interface ContainerImage extends FileResource {

    /// Creates a new virtual resource.
    ///
    /// @return the virtual resource
    ///
    static ContainerImage of(Path path) {
        return ResourceFactory.create(new ResourceType<>() {}, path);
    }
}
