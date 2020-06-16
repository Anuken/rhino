// API class

package rhino;

/**
 * This class provides support for implementing Java-style synchronized
 * methods in Javascript.
 * <p>
 * Synchronized functions are created from ordinary Javascript
 * functions by the <code>Synchronizer</code> constructor, e.g.
 * <code>new Packages.rhino.Synchronizer(fun)</code>.
 * The resulting object is a function that establishes an exclusive
 * lock on the <code>this</code> object of its invocation.
 * <p>
 * The Rhino shell provides a short-cut for the creation of
 * synchronized methods: <code>sync(fun)</code> has the same effect as
 * calling the above constructor.
 * @author Matthias Radestock
 * @see Delegator
 */

public class Synchronizer extends Delegator{

    private Object syncObject;

    /**
     * Create a new synchronized function from an existing one.
     * @param obj the existing function
     */
    public Synchronizer(Scriptable obj){
        super(obj);
    }

    /**
     * Create a new synchronized function from an existing one using
     * an explicit object as synchronization object.
     * @param obj the existing function
     * @param syncObject the object to synchronized on
     */
    public Synchronizer(Scriptable obj, Object syncObject){
        super(obj);
        this.syncObject = syncObject;
    }

    /**
     * @see Function#call
     */
    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj,
                       Object[] args){
        Object sync = syncObject != null ? syncObject : thisObj;
        synchronized(sync instanceof Wrapper ? ((Wrapper)sync).unwrap() : sync){
            return ((Function)obj).call(cx, scope, thisObj, args);
        }
    }
}
