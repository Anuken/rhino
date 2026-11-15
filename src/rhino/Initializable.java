package rhino;

/**
 * A single-function interface so that we can use lambda functions to lazily initialize native
 * classes.
 */
public interface Initializable {
    /** Initialize the class in question, returning the new constructor. */
    Object initialize(Context cx, VarScope scope, boolean sealed);
}
