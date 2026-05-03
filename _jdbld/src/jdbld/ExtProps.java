package jdbld;

import org.jdrupes.builder.api.PropertyKey;

/// Extra properties.
///
@SuppressWarnings("PMD.FieldNamingConventions")
enum ExtProps implements PropertyKey {

    /// The Build directory. Created artifacts should be put there.
    /// Defaults to [Path] "build".
    GitApi(null);
    
    private final Object defaultValue;

    <T> ExtProps(T defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T defaultValue() {
        return (T)defaultValue;
    }
}