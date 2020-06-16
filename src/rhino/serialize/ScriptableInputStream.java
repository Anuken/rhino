// API class

package rhino.serialize;

import rhino.*;

import java.io.*;

/**
 * Class ScriptableInputStream is used to read in a JavaScript
 * object or function previously serialized with a ScriptableOutputStream.
 * References to names in the exclusion list
 * replaced with references to the top-level scope specified during
 * creation of the ScriptableInputStream.
 * @author Norris Boyd
 */

public class ScriptableInputStream extends ObjectInputStream{

    /**
     * Create a ScriptableInputStream.
     * @param in the InputStream to read from.
     * @param scope the top-level scope to create the object in.
     */
    public ScriptableInputStream(InputStream in, Scriptable scope)
    throws IOException{
        super(in);
        this.scope = scope;
        enableResolveObject(true);
        Context cx = Context.getCurrentContext();
        if(cx != null){
            this.classLoader = cx.getApplicationClassLoader();
        }
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc)
    throws IOException, ClassNotFoundException{
        String name = desc.getName();
        if(classLoader != null){
            try{
                return classLoader.loadClass(name);
            }catch(ClassNotFoundException ex){
                // fall through to default loading
            }
        }
        return super.resolveClass(desc);
    }

    @Override
    protected Object resolveObject(Object obj)
    throws IOException{
        if(obj instanceof ScriptableOutputStream.PendingLookup){
            String name = ((ScriptableOutputStream.PendingLookup)obj).getName();
            obj = ScriptableOutputStream.lookupQualifiedName(scope, name);
            if(obj == Scriptable.NOT_FOUND){
                throw new IOException("Object " + name + " not found upon " +
                "deserialization.");
            }
        }else if(obj instanceof UniqueTag){
            obj = ((UniqueTag)obj).readResolve();
        }else if(obj instanceof Undefined){
            obj = ((Undefined)obj).readResolve();
        }
        return obj;
    }

    private final Scriptable scope;
    private ClassLoader classLoader;
}
