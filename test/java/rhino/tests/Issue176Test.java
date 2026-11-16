/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino.tests;

import java.io.InputStreamReader;
import org.junit.jupiter.api.Test;
import rhino.Context;
import rhino.Script;
import rhino.ScriptRuntime;
import rhino.TopLevel;

public class Issue176Test {

    Context cx;
    TopLevel scope;

    @Test
    public void throwing() throws Exception {
        cx = Context.enter();
        try {
            InputStreamReader in =
                    new InputStreamReader(Issue176Test.class.getResourceAsStream("Issue176.js"));
            Script script = cx.compileReader(in, "Issue176.js", 1);
            scope = cx.initStandardObjects();
            scope.put("host", scope, this);
            script.exec(cx, scope, scope.getGlobalThis()); // calls our methods
        } finally {
            Context.exit();
        }
    }

    public void throwError(String msg) {
        throw ScriptRuntime.throwError(cx, scope, msg);
    }

    public void throwCustomError(String constr, String msg) {
        throw ScriptRuntime.throwCustomError(cx, scope, constr, msg);
    }
}
