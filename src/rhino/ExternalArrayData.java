package rhino;

/**
 * Implement this interface in order to allow external data to be attached to a ScriptableObject.
 */

public interface ExternalArrayData{
    /**
     * Return the element at the specified index. The result must be a type that is valid in JavaScript:
     * Number, String, or Scriptable. This method will not be called unless "index" is in
     * range.
     */
    Object getArrayElement(int index);

    /**
     * Set the element at the specified index. This method will not be called unless "index" is in
     * range. The method must check that "value" is a valid type, and convert it if necessary.
     */
    void setArrayElement(int index, Object value);

    /**
     * Return the length of the array.
     */
    int getArrayLength();
}
