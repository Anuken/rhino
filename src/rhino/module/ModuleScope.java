package rhino.module;

import rhino.*;

import java.net.*;

/**
 * A top-level module scope. This class provides methods to retrieve the
 * module's source and base URIs in order to resolve relative module IDs
 * and check sandbox constraints.
 */
public class ModuleScope extends TopLevel{
    private final URI uri;
    private final URI base;

    public ModuleScope(Scriptable prototype, URI uri, URI base){
        this.uri = uri;
        this.base = base;
        setPrototype(prototype);
        cacheBuiltins(prototype, false);
    }

    public URI getUri(){
        return uri;
    }

    public URI getBase(){
        return base;
    }
}
