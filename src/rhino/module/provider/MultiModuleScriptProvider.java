package rhino.module.provider;

import rhino.*;
import rhino.module.*;

import java.net.*;
import java.util.*;

/**
 * A multiplexer for module script providers.
 * @author Attila Szegedi
 * @version $Id: MultiModuleScriptProvider.java,v 1.4 2011/04/07 20:26:12 hannes%helma.at Exp $
 */
public class MultiModuleScriptProvider implements ModuleScriptProvider{
    private final ModuleScriptProvider[] providers;

    /**
     * Creates a new multiplexing module script provider tht gathers the
     * specified providers
     * @param providers the providers to multiplex.
     */
    public MultiModuleScriptProvider(Iterable<? extends ModuleScriptProvider> providers){
        final List<ModuleScriptProvider> l = new LinkedList<>();
        for(ModuleScriptProvider provider : providers){
            l.add(provider);
        }
        this.providers = l.toArray(new ModuleScriptProvider[0]);
    }

    @Override
    public ModuleScript getModuleScript(Context cx, String moduleId, URI uri,
                                        URI base, Scriptable paths) throws Exception{
        for(ModuleScriptProvider provider : providers){
            final ModuleScript script = provider.getModuleScript(cx, moduleId,
            uri, base, paths);
            if(script != null){
                return script;
            }
        }
        return null;
    }
}
