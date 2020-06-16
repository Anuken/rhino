package rhino.classfile;

final class ClassFileMethod{
    ClassFileMethod(String name, short nameIndex, String type, short typeIndex,
                    short flags){
        itsName = name;
        itsNameIndex = nameIndex;
        itsType = type;
        itsTypeIndex = typeIndex;
        itsFlags = flags;
    }

    void setCodeAttribute(byte[] codeAttribute){
        itsCodeAttribute = codeAttribute;
    }

    int write(byte[] data, int offset){
        offset = ClassFileWriter.putInt16(itsFlags, data, offset);
        offset = ClassFileWriter.putInt16(itsNameIndex, data, offset);
        offset = ClassFileWriter.putInt16(itsTypeIndex, data, offset);
        // Code attribute only
        offset = ClassFileWriter.putInt16(1, data, offset);
        System.arraycopy(itsCodeAttribute, 0, data, offset,
        itsCodeAttribute.length);
        offset += itsCodeAttribute.length;
        return offset;
    }

    int getWriteSize(){
        return 2 * 4 + itsCodeAttribute.length;
    }

    String getName(){
        return itsName;
    }

    String getType(){
        return itsType;
    }

    short getFlags(){
        return itsFlags;
    }

    private final String itsName;
    private final String itsType;
    private final short itsNameIndex;
    private final short itsTypeIndex;
    private final short itsFlags;
    private byte[] itsCodeAttribute;

}
