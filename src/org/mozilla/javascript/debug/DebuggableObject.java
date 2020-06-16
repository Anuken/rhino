// API class

package org.mozilla.javascript.debug;

/**
 * This interface exposes debugging information from objects.
 */
public interface DebuggableObject {

    /**
     * Returns an array of ids for the properties of the object.
     *
     * <p>All properties, even those with attribute {DontEnum}, are listed.
     * This allows the debugger to display all properties of the object.<p>
     *
     * @return an array of java.lang.Objects with an entry for every
     * listed property. Properties accessed via an integer index will
     * have a corresponding
     * Integer entry in the returned array. Properties accessed by
     * a String will have a String entry in the returned array.
     */
    public Object[] getAllIds();
}
