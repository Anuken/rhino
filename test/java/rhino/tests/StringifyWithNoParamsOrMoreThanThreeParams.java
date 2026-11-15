/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino.tests;

import rhino.Context;
import rhino.drivers.LanguageVersion;
import rhino.drivers.RhinoTest;
import rhino.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/stringify-with-no-params-or-more-than-3-params.js")
@LanguageVersion(Context.VERSION_DEFAULT)
public class StringifyWithNoParamsOrMoreThanThreeParams extends ScriptTestsBase {}
