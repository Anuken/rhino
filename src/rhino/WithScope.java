package rhino;

import static rhino.Scriptable.NOT_FOUND;

public class WithScope implements VarScope {
    private final Scriptable obj;
    private final VarScope parent;

    public WithScope(VarScope parentScope, Scriptable obj) {
        this.parent = parentScope;
        this.obj = obj;
    }

    @Override
    public VarScope getParentScope() {
        return parent;
    }

    @Override
    public void delete(int index) {}

    @Override
    public void delete(Symbol arg0) {}

    @Override
    public Object get(int index, VarScope start) {
        return NOT_FOUND;
    }

    @Override
    public Object get(Symbol arg0, VarScope arg1) {
        return NOT_FOUND;
    }

    @Override
    public boolean has(int index, VarScope start) {
        return false;
    }

    @Override
    public boolean has(Symbol arg0, VarScope arg1) {
        return false;
    }

    @Override
    public void put(int index, VarScope start, Object value) {}

    @Override
    public void put(Symbol arg0, VarScope arg1, Object arg2) {}

    @Override
    public void defineConst(Context cx, String name, VarScope start) {
        Kit.codeBug("Attempt to define a const on a `with` scope.");
    }

    @Override
    public boolean isConst(Context cx, String name) {
        return false;
    }

    @Override
    public void putConst(Context cx, String name, VarScope start, Object value) {
        Kit.codeBug("Attempt to define a const on a `with` scope.");
    }

    @Override
    public Object get(String name, VarScope start) {
        return ScriptableObject.getProperty(obj, name);
    }

    @Override
    public void put(String name, VarScope start, Object value) {
        ScriptableObject.putProperty(obj, name, value);
    }

    @Override
    public boolean has(String name, VarScope start) {
        return ScriptableObject.hasProperty(obj, name);
    }

    @Override
    public void delete(String name) {
        ScriptableObject.deleteProperty(obj, name);
    }

    @Override
    public Object[] getIds() {
        return obj.getIds();
    }

    public Scriptable getObject() {
        return obj;
    }

    @Override
    public boolean isNestedScope() {
        return true;
    }
}
