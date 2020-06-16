// API class

package rhino;

/**
 * Interface to represent arbitrary action that requires to have Context
 * object associated with the current thread for its execution.
 * @param T the type of the return value of action execution
 */
public interface ContextAction<T>{
    /**
     * Execute action using the supplied Context instance.
     * When Rhino runtime calls the method, <tt>cx</tt> will be associated
     * with the current thread as active context.
     * @see ContextFactory#call(ContextAction)
     */
    T run(Context cx);
}

