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

    public abstract Object set(Context cx, Scriptable scope, Object value);

    public boolean delete(Context cx){
        return false;
    }

}

