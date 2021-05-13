package rhino;

/**
 * A wrapper for runtime exceptions.
 * <p>
 * Used by the JavaScript runtime to wrap and propagate exceptions that occur
 * during runtime.
 * @author Norris Boyd
 */
public class WrappedException extends EvaluatorException{
    /**
     * @see Context#throwAsScriptRuntimeEx(Throwable e)
     */
    public WrappedException(Throwable exception){
        super("Wrapped " + exception.toString());
        this.exception = exception;
        this.initCause(exception);

        int[] linep = {0};
        String sourceName = Context.getSourcePositionFromStack(linep);
        int lineNumber = linep[0];
        if(sourceName != null){
            initSourceName(sourceName);
        }
        if(lineNumber != 0){
            initLineNumber(lineNumber);
        }
    }

    /**
     * Get the wrapped exception.
     * @return the exception that was presented as a argument to the
     * constructor when this object was created
     */
    public Throwable getWrappedException(){
        return exception;
    }

    private final Throwable exception;
}
