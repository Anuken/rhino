package org.mozilla.javascript;

/**
 * Object that can allows assignments to the result of function calls.
 */
public interface RefCallable extends Callable
{
    /**
     * Perform function call in reference context.
     * The args array reference should not be stored in any object that is
     * can be GC-reachable after this method returns. If this is necessary,
     * for example, to implement {@link Ref} methods, then store args.clone(),
     * not args array itself.
     *
     * @param cx the current Context for this thread
     * @param thisObj the JavaScript <code>this</code> object
     * @param args the array of arguments
     */
    public Ref refCall(Context cx, Scriptable thisObj, Object[] args);
}

