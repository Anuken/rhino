package rhino.tests.harmony;

import rhino.Context;
import rhino.drivers.LanguageVersion;
import rhino.drivers.RhinoTest;
import rhino.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/harmony/promise-prototype-finally.js")
@LanguageVersion(Context.VERSION_ES6)
public class PromiseFinallyTest extends ScriptTestsBase {}
