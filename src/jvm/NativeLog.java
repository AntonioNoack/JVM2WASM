package jvm;

import annotations.JavaScriptWASM;
import annotations.NoThrow;

public class NativeLog {

    @NoThrow
    @JavaScriptWASM(code = "console.log(arg0)")
    public static native void log(int code);

    @NoThrow
    @JavaScriptWASM(code = "console.log(arg0, arg1)")
    public static native void log(int code, int v);

    @NoThrow
    @JavaScriptWASM(code = "console.log(arg0, arg1)")
    public static native void log(long code, long v);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), arg1);")
    public static native void log(String msg, int param);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), arg1);")
    public static native void log(String msg, Pointer param);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), arg1);")
    public static native void log(String msg, double param);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), arg1, arg2);")
    public static native void log(String msg, int param, int param2);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), arg1, arg2);")
    public static native void log(String msg, long param, long param2);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), arg1, arg2);")
    public static native void log(String msg, Pointer param, int param2);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), arg1, arg2);")
    public static native void log(String msg, Pointer param, Pointer param2);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), arg1, arg2, arg3);")
    public static native void log(String msg, Pointer param, int param1, Pointer param2);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), String.fromCharCode(arg1), String.fromCharCode(arg2));")
    public static native void log(String msg, char param, char param2);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), str(arg1), str(arg2));")
    public static native void log(String msg, String param, String param2);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), str(arg1), arg2);")
    public static native void log(String msg, String param, int param2);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), str(arg1), arg2);")
    public static native void log(String msg, String param, long param2);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), str(arg1), arg2);")
    public static native void log(String msg, String param, double param2);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), str(arg1), arg2);")
    public static native void log(String msg, String param, boolean param2);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), arg1, arg2, arg3);")
    public static native void log(String msg, int param, int param2, int param3);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), arg1, arg2, arg3);")
    public static native void log(String msg, long param, long param2, int param3);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), arg1, arg2, arg3);")
    public static native void log(String msg, Pointer param, int param2, int param3);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), arg1, arg2, arg3);")
    public static native void log(String msg, Pointer param, Pointer param2, int param3);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), arg1, arg2, arg3);")
    public static native void log(String msg, int param, long param2, int param3);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), str(arg1), arg2, arg3);")
    public static native void log(String msg, String x, int param, int param2);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), str(arg1), arg2, arg3, arg4);")
    public static native void log(String msg, String x, int param, int param2, int param3);

    @NoThrow
    @JavaScriptWASM(code = "console.log(arg0, str(arg1), str(arg2), arg3);")
    public static native void log(int i, String msg, String param, int param2);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), str(arg1), str(arg2), arg3);")
    public static native void log(String i, String msg, String param, int param2);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), str(arg1), str(arg2), arg3);")
    public static native void log(String i, String msg, String param, Pointer param2);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), str(arg1), arg2, str(arg3));")
    public static native void log(String i, String msg, Pointer param, String param2);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), str(arg1), arg2, str(arg3));")
    public static native void log(String i, String msg, int param, String param2);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), arg1, str(arg2));")
    public static native void log(String msg, int param, String param2);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), arg1, str(arg2));")
    public static native void log(String msg, Pointer param, String param2);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0));")
    public static native void log(String msg);

    @NoThrow
    @JavaScriptWASM(code = "console.log(arg0);")
    public static native void log(Throwable v);

    @NoThrow
    @JavaScriptWASM(code = "console.log(str(arg0), str(arg1));")
    public static native void log(String msg, String param);

}
