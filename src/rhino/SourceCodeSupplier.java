package rhino;

/**
 * A lambda that can provide the source code for a given script.
 *
 * <p>It is expected that this is implemented externally, from Rhino users.
 *
 * @see CompileRequest.Builder#sourceSupplier(ISourceCodeSupplier)
 */
public interface SourceCodeSupplier {
    String get();
}
