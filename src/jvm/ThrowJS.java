package jvm;

import annotations.Alias;
import annotations.NoThrow;
import annotations.WASM;

import static jvm.JavaThrowable.Throwable_printStackTrace_V;
import static jvm.NativeLog.log;

public class ThrowJS {

    @NoThrow
    @WASM(code = "unreachable")
    public static native void crash();

    @NoThrow
    public static void throwJs() {
        log("Internal VM error!");
        crash();
    }

    @NoThrow
    public static void throwJs(String s) {
        log(s);
        crash();
    }

    @NoThrow
    public static void throwJs(String s, int a) {
        log(s, a);
        crash();
    }

    @NoThrow
    public static void throwJs(String s, String a) {
        log(s, a);
        crash();
    }

    @NoThrow
    public static void throwJs(String s, int a, int b) {
        log(s, a, b);
        crash();
    }

    @NoThrow
    public static void throwJs(String s, int a, String b) {
        log(s, a, b);
        crash();
    }

    @NoThrow
    public static void throwJs(String s, int a, int b, int c) {
        log(s, a, b, c);
        crash();
    }

    @NoThrow
    @Alias(names = "panic")
    private static void panic(Throwable throwable) {
        if (throwable != null) {
            Throwable_printStackTrace_V(throwable);
            throwJs();
        }
    }
}
