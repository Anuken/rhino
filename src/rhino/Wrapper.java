// API class

package rhino;

/**
 * Objects that can wrap other values for reflection in the JS environment
 * will implement Wrapper.
 * <p>
 * Wrapper defines a single method that can be called to unwrap the object.
 */

public interface Wrapper{

    /**
     * Unwrap the object by returning the wrapped value.
     * @return a wrapped value
     */
    Object unwrap();
}
