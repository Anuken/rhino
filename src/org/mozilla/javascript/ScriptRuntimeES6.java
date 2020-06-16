package org.mozilla.javascript;

public class ScriptRuntimeES6 {

    public static Scriptable requireObjectCoercible(Context cx, Scriptable val, IdFunctionObject idFuncObj) {
        if (val == null || Undefined.isUndefined(val)) {
            throw ScriptRuntime.typeError2("msg.called.null.or.undefined", idFuncObj.getTag(), idFuncObj.getFunctionName());
        }
        return val;
    }
}
