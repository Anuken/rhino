/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino.sourcemap;

/**
 * Thrown by {@link SourceMapV3#parse} (and friends) when the input is not a valid ECMA-426 source
 * map. Unchecked, matching the style of other Rhino exceptions.
 */
public class SourceMapException extends RuntimeException {

    public SourceMapException(String message) {
        super(message);
    }

    public SourceMapException(String message, Throwable cause) {
        super(message, cause);
    }
}
