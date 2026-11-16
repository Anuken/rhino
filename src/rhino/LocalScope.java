package rhino;

public class LocalScope extends DeclarationScope {
    public LocalScope(VarScope parentScope) {
        super(parentScope);
    }

    @Override
    public boolean isNestedScope() {
        return true;
    }

    @Override
    public LocalScope copyScope() {
        var res = new LocalScope(getParentScope());
        res.setMap(getMap().copyMap());
        return res;
    }
}
