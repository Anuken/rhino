package rhino;

/**
 * Load generated classes.
 * @author Norris Boyd
 */
public class DefiningClassLoader extends ClassLoader implements GeneratedClassLoader{

    public DefiningClassLoader(){
        this.parentLoader = getClass().getClassLoader();
    }

    public DefiningClassLoader(ClassLoader parentLoader){
        this.parentLoader = parentLoader;
    }

    @Override
    public Class<?> defineClass(String name, byte[] data){
        return super.defineClass(name, data, 0, data.length);
    }

    @Override
    public void linkClass(Class<?> cl){
        resolveClass(cl);
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve)
    throws ClassNotFoundException{
        Class<?> cl = findLoadedClass(name);
        if(cl == null){
            if(parentLoader != null){
                cl = parentLoader.loadClass(name);
            }else{
                cl = findSystemClass(name);
            }
        }
        if(resolve){
            resolveClass(cl);
        }
        return cl;
    }

    private final ClassLoader parentLoader;
}
