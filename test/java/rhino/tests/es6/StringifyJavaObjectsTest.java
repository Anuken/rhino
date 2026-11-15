package rhino.tests.es6;

import rhino.Context;
import rhino.drivers.LanguageVersion;
import rhino.drivers.RhinoTest;
import rhino.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/es6/stringify-java-objects.js")
@LanguageVersion(Context.VERSION_ES6)
public class StringifyJavaObjectsTest extends ScriptTestsBase {}
