package rhino;

import java.util.Collections;
import java.util.Iterator;

public class NativeCollectionIterator extends ES6Iterator {
    private String className;
    private Type type;
    private Iterator<Hashtable.Entry> iterator = Collections.emptyIterator();

    enum Type {
        KEYS,
        VALUES,
        BOTH
    }

    public NativeCollectionIterator(String tag) {
        this.className = tag;
        this.iterator = Collections.emptyIterator();
        this.type = Type.BOTH;
    }

    public NativeCollectionIterator(
            VarScope scope, String className, Type type, Iterator<Hashtable.Entry> iterator) {
        super(scope, className);
        this.className = className;
        this.iterator = iterator;
        this.type = type;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    protected boolean isDone(Context cx, VarScope scope) {
        return !iterator.hasNext();
    }

    @Override
    protected Object nextValue(Context cx, VarScope scope) {
        final Hashtable.Entry e = iterator.next();
        switch (type) {
            case KEYS:
                return e.key;
            case VALUES:
                return e.value;
            case BOTH:
                return cx.newArray(scope, new Object[] {e.key, e.value});
            default:
                throw new AssertionError();
        }
    }
}
