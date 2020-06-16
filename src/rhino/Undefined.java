package rhino;

import java.lang.reflect.*;

/**
 * This class implements the Undefined value in JavaScript.
 */
public class Undefined{
    public static final Object instance = new Undefined();

    private Undefined(){
    }

    public Object readResolve(){
        return instance;
    }

    @Override
    public boolean equals(Object obj){
        return isUndefined(obj) || super.equals(obj);
    }

    @Override
    public int hashCode(){
        // All instances of Undefined are equivalent!
        return 0;
    }

    public static final Scriptable SCRIPTABLE_UNDEFINED;

    static{
        SCRIPTABLE_UNDEFINED = (Scriptable)Proxy.newProxyInstance(Undefined.class.getClassLoader(), new Class[]{Scriptable.class}, (proxy, method, args) -> {
            if(method.getName().equals("toString")) return "undefined";
            if(method.getName().equals("equals")){
                return args.length > 0 && isUndefined(args[0]);
            }
            throw new UnsupportedOperationException("undefined doesn't support " + method.getName());
        });
    }

    public static boolean isUndefined(Object obj){
        return Undefined.instance == obj || Undefined.SCRIPTABLE_UNDEFINED == obj;
    }
}
