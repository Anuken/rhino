package rhino;

public final class NativeStringIterator extends ES6Iterator{
    private static final String ITERATOR_TAG = "StringIterator";

    static void init(ScriptableObject scope, boolean sealed){
        ES6Iterator.init(scope, sealed, new NativeStringIterator(), ITERATOR_TAG);
    }

    /**
     * Only for constructing the prototype object.
     */
    private NativeStringIterator(){
        super();
    }

    NativeStringIterator(Scriptable scope, Scriptable stringLike){
        super(scope, ITERATOR_TAG);
        this.index = 0;
        this.string = ScriptRuntime.toString(stringLike);
    }

    @Override
    public String getClassName(){
        return "String Iterator";
    }

    @Override
    protected boolean isDone(Context cx, Scriptable scope){
        return index >= string.length();
    }

    @Override
    protected Object nextValue(Context cx, Scriptable scope){
        int newIndex = string.offsetByCodePoints(index, 1);
        Object value = string.substring(index, newIndex);
        index = newIndex;
        return value;
    }

    @Override
    protected String getTag(){
        return ITERATOR_TAG;
    }

    private String string;
    private int index;
}
