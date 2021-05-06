package rhino;

/**
 * Class instances represent serializable tags to mark special Object values.
 * for readResolve method
 */
public final class UniqueTag{
    private static final int ID_NOT_FOUND = 1;
    private static final int ID_NULL_VALUE = 2;
    private static final int ID_DOUBLE_MARK = 3;

    /**
     * Tag to mark non-existing values.
     */
    public static final UniqueTag
    NOT_FOUND = new UniqueTag(ID_NOT_FOUND);

    /**
     * Tag to distinguish between uninitialized and null values.
     */
    public static final UniqueTag
    NULL_VALUE = new UniqueTag(ID_NULL_VALUE);

    /**
     * Tag to indicate that a object represents "double" with the real value
     * stored somewhere else.
     */
    public static final UniqueTag
    DOUBLE_MARK = new UniqueTag(ID_DOUBLE_MARK);

    private final int tagId;

    private UniqueTag(int tagId){
        this.tagId = tagId;
    }

    public Object readResolve(){
        switch(tagId){
            case ID_NOT_FOUND:
                return NOT_FOUND;
            case ID_NULL_VALUE:
                return NULL_VALUE;
            case ID_DOUBLE_MARK:
                return DOUBLE_MARK;
        }
        throw new IllegalStateException(String.valueOf(tagId));
    }

    // Overridden for better debug printouts
    @Override
    public String toString(){
        String name;
        switch(tagId){
            case ID_NOT_FOUND:
                name = "NOT_FOUND";
                break;
            case ID_NULL_VALUE:
                name = "NULL_VALUE";
                break;
            case ID_DOUBLE_MARK:
                name = "DOUBLE_MARK";
                break;
            default:
                throw Kit.codeBug();
        }
        return super.toString() + ": " + name;
    }

}

