package rhino.classfile;

final class FieldOrMethodRef{
    FieldOrMethodRef(String className, String name, String type){
        this.className = className;
        this.name = name;
        this.type = type;
    }

    public String getClassName(){
        return className;
    }

    public String getName(){
        return name;
    }

    public String getType(){
        return type;
    }

    @Override
    public boolean equals(Object obj){
        if(!(obj instanceof FieldOrMethodRef)){
            return false;
        }
        FieldOrMethodRef x = (FieldOrMethodRef)obj;
        return className.equals(x.className)
        && name.equals(x.name)
        && type.equals(x.type);
    }

    @Override
    public int hashCode(){
        if(hashCode == -1){
            int h1 = className.hashCode();
            int h2 = name.hashCode();
            int h3 = type.hashCode();
            hashCode = h1 ^ h2 ^ h3;
        }
        return hashCode;
    }

    private final String className;
    private final String name;
    private final String type;
    private int hashCode = -1;
}
