/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino.tests;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import rhino.ContextFactory;
import rhino.Function;

public class Bug496585Test {

    public String method(String one, Function function) {
        return "string+function";
    }

    public String method(String... strings) {
        return "string[]";
    }

    @Test
    public void callOverloadedFunction() {
        new ContextFactory()
                .call(
                        cx -> {
                            cx.getWrapFactory().setJavaPrimitiveWrap(false);
                            Assertions.assertEquals(
                                    "string[]",
                                    cx.evaluateString(
                                            cx.initStandardObjects(),
                                            "new Packages.rhino.tests.Bug496585Test().method('one', 'two', 'three')",
                                            "<test>",
                                            1,
                                            null));
                            Assertions.assertEquals(
                                    "string+function",
                                    cx.evaluateString(
                                            cx.initStandardObjects(),
                                            "new Packages.rhino.tests.Bug496585Test().method('one', function() {})",
                                            "<test>",
                                            1,
                                            null));
                            return null;
                        });
    }
}
