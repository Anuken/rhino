/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino;

/**
 * Class instances represent tags to mark special Object values.
 */
public final class UniqueTag {
    private static final int ID_NOT_FOUND = 1;
    private static final int ID_NULL_VALUE = 2;
    private static final int ID_DOUBLE_MARK = 3;
    private static final int ID_UNINITIALIZED = 4;

    /** Tag to mark non-existing values. */
    public static final UniqueTag NOT_FOUND = new UniqueTag(ID_NOT_FOUND);

    /** Tag to distinguish between uninitialized and null values. */
    public static final UniqueTag NULL_VALUE = new UniqueTag(ID_NULL_VALUE);

    /**
     * Tag to indicate that a object represents "double" with the real value stored somewhere else.
     */
    public static final UniqueTag DOUBLE_MARK = new UniqueTag(ID_DOUBLE_MARK);

    /**
     * Value of a let, const or class binding whose declaration has not run yet, where a reference
     * may read it before then.
     */
    public static final UniqueTag UNINITIALIZED = new UniqueTag(ID_UNINITIALIZED);

    private final int tagId;

    private UniqueTag(int tagId) {
        this.tagId = tagId;
    }

    // Overridden for better debug printouts
    @Override
    public String toString() {
        String name;
        switch (tagId) {
            case ID_NOT_FOUND:
                name = "NOT_FOUND";
                break;
            case ID_NULL_VALUE:
                name = "NULL_VALUE";
                break;
            case ID_DOUBLE_MARK:
                name = "DOUBLE_MARK";
                break;
            case ID_UNINITIALIZED:
                name = "UNINITIALIZED";
                break;
            default:
                throw Kit.codeBug();
        }
        return super.toString() + ": " + name;
    }
}
