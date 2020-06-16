// API class

package rhino.debug;

import rhino.*;

/**
 * Interface to implement if the application is interested in receiving debug
 * information.
 */
public interface Debugger{

    /**
     * Called when compilation of a particular function or script into internal
     * bytecode is done.
     * @param cx current Context for this thread
     * @param fnOrScript object describing the function or script
     * @param source the function or script source
     */
    void handleCompilationDone(Context cx, DebuggableScript fnOrScript,
                               String source);

    /**
     * Called when execution entered a particular function or script.
     * @return implementation of DebugFrame which receives debug information during
     * the function or script execution or null otherwise
     */
    DebugFrame getFrame(Context cx, DebuggableScript fnOrScript);
}
