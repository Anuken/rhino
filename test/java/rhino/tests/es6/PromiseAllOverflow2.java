package rhino.tests.es6;

import rhino.Context;
import rhino.drivers.LanguageVersion;
import rhino.drivers.RhinoTest;
import rhino.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/es6/promise-all-overflow-2.js")
@LanguageVersion(Context.VERSION_ES6)
public class PromiseAllOverflow2 extends ScriptTestsBase {}
