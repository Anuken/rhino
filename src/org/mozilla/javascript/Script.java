// API class

package org.mozilla.javascript;

/**
 * All compiled scripts implement this interface.
 * <p>
 * This class encapsulates script execution relative to an
 * object scope.
 * @since 1.3
 * @author Norris Boyd
 */

public interface Script {

    /**
     * Execute the script.
     * <p>
     * The script is executed in a particular runtime Context, which
     * must be associated with the current thread.
     * The script is executed relative to a scope--definitions and
     * uses of global top-level variables and functions will access
     * properties of the scope object. For compliant ECMA
     * programs, the scope must be an object that has been initialized
     * as a global object using <code>Context.initStandardObjects</code>.
     * <p>
     *
     * @param cx the Context associated with the current thread
     * @param scope the scope to execute relative to
     * @return the result of executing the script
     * @see org.mozilla.javascript.Context#initStandardObjects()
     */
    public Object exec(Context cx, Scriptable scope);

}
