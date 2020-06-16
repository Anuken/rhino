package rhino;

/**
 * Generic notion of callable object that can execute some script-related code
 * upon request with specified values for script scope and this objects.
 */
public interface Callable{
    /**
     * Perform the call.
     * @param cx the current Context for this thread
     * @param scope the scope to use to resolve properties.
     * @param thisObj the JavaScript <code>this</code> object
     * @param args the array of arguments
     * @return the result of the call
     */
    Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args);
}

