package rhino.tests.es2024;

import rhino.Context;
import rhino.drivers.LanguageVersion;
import rhino.drivers.RhinoTest;
import rhino.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/es2024/dataview-resizablearraybuffer.js")
@LanguageVersion(Context.VERSION_ES6)
public class DataViewResizableArrayBufferTest extends ScriptTestsBase {}
