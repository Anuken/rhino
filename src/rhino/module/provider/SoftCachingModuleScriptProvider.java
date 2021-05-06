package rhino.module.provider;

import rhino.*;
import rhino.module.*;

import java.lang.ref.*;
import java.net.*;
import java.util.concurrent.*;

/**
 * A module script provider that uses a module source provider to load modules
 * and caches the loaded modules. It softly references the loaded modules'
 * Rhino {@link Script} objects, thus a module once loaded can become eligible
 * for garbage collection if it is otherwise unused under memory pressure.
 * Instances of this class are thread safe.
 * @author Attila Szegedi
 * @version $Id: SoftCachingModuleScriptProvider.java,v 1.3 2011/04/07 20:26:12 hannes%helma.at Exp $
 */
public class SoftCachingModuleScriptProvider extends CachingModuleScriptProviderBase{
    private transient ReferenceQueue<Script> scriptRefQueue = new ReferenceQueue<>();
    private transient ConcurrentMap<String, ScriptReference> scripts = new ConcurrentHashMap<>(16, .75f, getConcurrencyLevel());

    /**
     * Creates a new module provider with the specified module source provider.
     * @param moduleSourceProvider provider for modules' source code
     */
    public SoftCachingModuleScriptProvider(
    ModuleSourceProvider moduleSourceProvider){
        super(moduleSourceProvider);
    }

    @Override
    public ModuleScript getModuleScript(Context cx, String moduleId,
                                        URI uri, URI base, Scriptable paths)
    throws Exception{
        // Overridden to clear the reference queue before retrieving the
        // script.
        while(true){
            ScriptReference ref = (ScriptReference)scriptRefQueue.poll();
            if(ref == null){
                break;
            }
            scripts.remove(ref.getModuleId(), ref);
        }
        return super.getModuleScript(cx, moduleId, uri, base, paths);
    }

    @Override
    protected CachedModuleScript getLoadedModule(String moduleId){
        final ScriptReference scriptRef = scripts.get(moduleId);
        return scriptRef != null ? scriptRef.getCachedModuleScript() : null;
    }

    @Override
    protected void putLoadedModule(String moduleId, ModuleScript moduleScript,
                                   Object validator){
        scripts.put(moduleId, new ScriptReference(moduleScript.getScript(),
        moduleId, moduleScript.getUri(), moduleScript.getBase(),
        validator, scriptRefQueue));
    }

    private static class ScriptReference extends SoftReference<Script>{
        private final String moduleId;
        private final URI uri;
        private final URI base;
        private final Object validator;

        ScriptReference(Script script, String moduleId, URI uri, URI base,
                        Object validator, ReferenceQueue<Script> refQueue){
            super(script, refQueue);
            this.moduleId = moduleId;
            this.uri = uri;
            this.base = base;
            this.validator = validator;
        }

        CachedModuleScript getCachedModuleScript(){
            final Script script = get();
            if(script == null){
                return null;
            }
            return new CachedModuleScript(new ModuleScript(script, uri, base),
            validator);
        }

        String getModuleId(){
            return moduleId;
        }
    }

}