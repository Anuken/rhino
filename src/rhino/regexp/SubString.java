package rhino.regexp;

/**
 * A utility class for lazily instantiated substrings.
 */
public class SubString{

    public SubString(){
    }

    public SubString(String str){
        this.str = str;
        index = 0;
        length = str.length();
    }

    public SubString(String source, int start, int len){
        str = source;
        index = start;
        length = len;
    }

    @Override
    public String toString(){
        return str == null
        ? ""
        : str.substring(index, index + length);
    }

    String str;
    int index;
    int length;
}

