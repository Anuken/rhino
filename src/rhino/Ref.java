package rhino;

/**
 * Generic notion of reference object that know how to query/modify the
 * target objects based on some property/index.
 */
public abstract class Ref{

    public boolean has(Context cx){
        return true;
    }

    public abstract Object get(Context cx);

    /**
     * @deprecated Use {@link #set(Context, Scriptable, Object)} instead
     */
    @Deprecated
    public abstract Object set(Context cx, Object value);

    public Object set(Context cx, Scriptable scope, Object value){
        return set(cx, value);
    }

    public boolean delete(Context cx){
        return false;
    }

}

