package rhino;

import rhino.ast.*;

import java.util.*;

/**
 * Abstraction of evaluation, which can be implemented either by an
 * interpreter or compiler.
 */
public interface Evaluator{

    /**
     * Compile the script or function from intermediate representation
     * tree into an executable form.
     * @param compilerEnv Compiler environment
     * @param tree parse tree
     * @param encodedSource encoding of the source code for decompilation
     * @param returnFunction if true, compiling a function
     * @return an opaque object that can be passed to either
     * createFunctionObject or createScriptObject, depending on the
     * value of returnFunction
     */
    Object compile(CompilerEnvirons compilerEnv,
                   ScriptNode tree,
                   String encodedSource,
                   boolean returnFunction);

    /**
     * Create a function object.
     * @param cx Current context
     * @param scope scope of the function
     * @param bytecode opaque object returned by compile
     * @return Function object that can be called
     */
    Function createFunctionObject(Context cx, Scriptable scope,
                                  Object bytecode);

    /**
     * Create a script object.
     * @param bytecode opaque object returned by compile
     * @return Script object that can be evaluated
     */
    Script createScriptObject(Object bytecode);

    /**
     * Capture stack information from the given exception.
     * @param ex an exception thrown during execution
     */
    void captureStackInfo(RhinoException ex);

    /**
     * Get the source position information by examining the stack.
     * @param cx Context
     * @param linep Array object of length &gt;= 1; getSourcePositionFromStack
     * will assign the line number to linep[0].
     * @return the name of the file or other source container
     */
    String getSourcePositionFromStack(Context cx, int[] linep);

    /**
     * Given a native stack trace, patch it with script-specific source
     * and line information
     * @param ex exception
     * @param nativeStackTrace the native stack trace
     * @return patched stack trace
     */
    String getPatchedStack(RhinoException ex,
                           String nativeStackTrace);

    /**
     * Get the script stack for the given exception
     * @param ex exception from execution
     * @return list of strings for the stack trace
     */
    List<String> getScriptStack(RhinoException ex);

    /**
     * Mark the given script to indicate it was created by a call to
     * eval() or to a Function constructor.
     * @param script script to mark as from eval
     */
    void setEvalScriptFlag(Script script);
}
