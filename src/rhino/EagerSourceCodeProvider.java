package rhino;

import java.util.Objects;

/** Stores a reference to the original source code and returns it as necessary. */
public class EagerSourceCodeProvider implements SourceCodeProvider {
    private final String rawSource;

    public EagerSourceCodeProvider(String rawSource) {
        Objects.requireNonNull(rawSource);
        this.rawSource = rawSource;
    }

    @Override
    public String getSource(JSDescriptor<?> desc, int start, int end) {
        return rawSource.substring(start, end);
    }

    @Override
    public String getRawSource() {
        return rawSource;
    }
}
