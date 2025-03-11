package jvm;

import annotations.JavaScript;
import annotations.NoThrow;

public class NativeLog {

    @NoThrow
    @JavaScript(code = "console.log(arg0)")
    public static native void log(int code);

    @NoThrow
    @JavaScript(code = "console.log(arg0, arg1)")
    public static native void log(int code, int v);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), arg1);")
    public static native void log(String msg, int param);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), arg1);")
    public static native void log(String msg, double param);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), arg1, arg2);")
    public static native void log(String msg, int param, int param2);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), String.fromCharCode(arg1), String.fromCharCode(arg2));")
    public static native void log(String msg, char param, char param2);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), str(arg1), str(arg2));")
    public static native void log(String msg, String param, String param2);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), str(arg1), arg2);")
    public static native void log(String msg, String param, int param2);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), str(arg1), arg2);")
    public static native void log(String msg, String param, long param2);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), str(arg1), arg2);")
    public static native void log(String msg, String param, double param2);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), str(arg1), arg2);")
    public static native void log(String msg, String param, boolean param2);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), arg1, arg2, arg3);")
    public static native void log(String msg, int param, int param2, int param3);

    @NoThrow
    @JavaScript(code = "console.log(arg0, str(arg1), str(arg2), arg3);")
    public static native void log(int i, String msg, String param, int param2);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), str(arg1), str(arg2), arg3);")
    public static native void log(String i, String msg, String param, int param2);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), str(arg1), arg2, str(arg3));")
    public static native void log(String i, String msg, int param, String param2);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), arg1, str(arg2));")
    public static native void log(String msg, int param, String param2);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0));")
    public static native void log(String msg);

    @NoThrow
    @JavaScript(code = "console.log(arg0);")
    public static native void log(Throwable v);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), str(arg1));")
    public static native void log(String msg, String param);

}
