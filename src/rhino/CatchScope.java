package rhino;

public class CatchScope extends DeclarationScope {
    public CatchScope(VarScope parentScope) {
        super(parentScope);
    }

    @Override
    public boolean isNestedScope() {
        return true;
    }
}
