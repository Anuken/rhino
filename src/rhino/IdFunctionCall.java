package rhino;

/**
 * Master for id-based functions that knows their properties and how to
 * execute them.
 */
public interface IdFunctionCall{
    /**
     * 'thisObj' will be null if invoked as constructor, in which case
     * instance of Scriptable should be returned
     */
    Object execIdCall(IdFunctionObject f, Context cx, Scriptable scope,
                      Scriptable thisObj, Object[] args);

}

