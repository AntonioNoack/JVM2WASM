package jvm;

import annotations.*;
import jvm.lang.JavaLangAccessImpl;
import sun.misc.SharedSecrets;

import java.io.PrintStream;

import static jvm.JVM32.unsignedGreaterThanEqual;
import static jvm.JVM32.validateClassIdx;
import static jvm.JavaLang.getAddr;
import static jvm.NativeLog.log;

/**
 * Things that are shared between JVM32 and JVM64: stuff, that is independent of pointerSize/is32Bits/ptrType
 */
public class JVMShared {

    @NoThrow
    @Alias(names = "stackPop")
    public static void stackPop() {
        stackPopImpl();
    }

    @NoThrow
    @Alias(names = "getStackDepth")
    public static int getStackDepth() {
        return getStackDepth(getStackPtr());
    }

    @NoThrow
    public static int getStackDepth(int stackPointer) {
        return (getStackStart() - stackPointer) >> 2;
    }

    @NoThrow
    @WASM(code = "global.get $stackPointer i32.const 4 i32.add global.set $stackPointer")
    public static native void stackPopImpl();

    @NoThrow
    @WASM(code = "global.get $stackPointer")
    public static native int getStackPtr();

    @NoThrow
    @WASM(code = "global.get $stackPointerStart")
    public static native int getStackStart();

    @NoThrow
    @WASM(code = "global.get $stackEndPointer")
    public static native int getStackLimit();

    @NoThrow
    @WASM(code = "global.set $stackPointer")
    public static native void setStackPtr(int addr);

    private static boolean canWarnStackOverflow = true;

    @NoThrow
    @Alias(names = "stackPush")
    public static void stackPush(int idx) {
        JVM32.printStackTraceLine(idx);
        int stackPointer = getStackPtr() - 4;
        int limit = getStackLimit();
        if (unsignedGreaterThanEqual(stackPointer, limit)) {
            write32(stackPointer, idx);
        } else if (stackPointer == limit - 4) {
            // stack overflow
            // we can do different things here -> let's just keep running;
            // just the stack is no longer tracked :)
            if (canWarnStackOverflow) {
                canWarnStackOverflow = false;
                log("Warning: Exited stack space, meaning",
                        (getStackStart() - getStackLimit()) >> 2, " recursive calls");
            }
        }
        setStackPtr(stackPointer);
    }

    @Alias(names = "checkNotNull")
    public static void checkNotNull(Object obj, String clazzName, String fieldName) {
        if (obj == null) {
            log("NullPointer@class.field:", clazzName, fieldName);
            throw new NullPointerException("Instance must not be null");
        }
    }

    @Export
    @NoThrow
    @UsedIfIndexed
    @Alias(names = "init")
    public static void init() {
        staticInit();
        // access static, so it is initialized
        getAddr(System.out);
        // could be used to initialize classes or io
        System.setOut(new PrintStream(new JavaLang.JSOutputStream(true)));
        System.setErr(new PrintStream(new JavaLang.JSOutputStream(false)));
        SharedSecrets.setJavaLangAccess(new JavaLangAccessImpl());
    }

    /**
     * method contents will be replaced programmatically,
     * if the callStaticInitOnce flag is set
     * */
    private static void staticInit() {
        /* !!!DO NOT PLACE CODE HERE!!! */
    }

    @Alias(names = "throwAME")
    public static void throwAbstractMethodError() {
        throw new AbstractMethodError();
    }

    @NoThrow
    @WASM(code = "i64.store")
    public static native void write64(int addr, long value);

    @NoThrow
    @WASM(code = "f64.store")
    public static native void write64(int addr, double value);

    @NoThrow
    @WASM(code = "i32.store")
    public static native void write32(int addr, int value);

    @NoThrow
    @WASM(code = "f32.store")
    public static native void write32(int addr, float value);

    @NoThrow
    @WASM(code = "i32.store16")
    public static native void write16(int addr, short value);

    @NoThrow
    @WASM(code = "i32.store16")
    public static native void write16(int addr, char value);

    @NoThrow
    @WASM(code = "i32.store8")
    public static native void write8(int addr, byte value);

    @NoThrow
    @WASM(code = "i64.load")
    public static native long read64(int addr);

    @NoThrow
    @WASM(code = "i32.load")
    public static native int read32(int addr);

    @NoThrow
    @WASM(code = "f64.load")
    public static native double read64f(int addr);

    @NoThrow
    @WASM(code = "f32.load")
    public static native float read32f(int addr);

    @NoThrow
    @WASM(code = "i32.load16_s")
    public static native short read16s(int addr);

    @NoThrow
    @WASM(code = "i32.load16_u")
    public static native char read16u(int addr);

    @NoThrow
    @WASM(code = "i32.load8_s")
    public static native byte read8(int addr);

    @NoThrow
    @WASM(code = "i32.div_s")
    public static native int div(int a, int b);

    @NoThrow
    @WASM(code = "i64.div_s")
    public static native long div(long a, long b);

    @Alias(names = "safeDiv32")
    public static int safeDiv32(int a, int b) {
        if (b == 0) throw new ArithmeticException("Division by zero");
        if ((a == Integer.MIN_VALUE) & (b == -1)) return Integer.MIN_VALUE;
        return div(a, b);
    }

    @Alias(names = "safeDiv64")
    public static long safeDiv64(long a, long b) {
        if (b == 0) throw new ArithmeticException("Division by zero");
        if ((a == Long.MIN_VALUE) & (b == -1)) return Long.MIN_VALUE;
        return div(a, b);
    }

    @Alias(names = "checkNonZero32")
    public static int checkNonZero32(int b) {
        if (b == 0) throw new ArithmeticException("Division by zero");
        return b;
    }

    @Alias(names = "checkNonZero64")
    public static long checkNonZero64(long b) {
        if (b == 0) throw new ArithmeticException("Division by zero");
        return b;
    }

    // to do implement this, when we have multi-threading
    @Alias(names = "monitorEnter")
    public static void monitorEnter(Object lock) {
    }

    @Alias(names = "monitorExit")
    public static void monitorExit(Object lock) {
    }

    @NoThrow
    @WASM(code = "global.get $staticInitTable")
    public static native int getStaticInitTable();

    @Alias(names = "wasStaticInited")
    public static boolean wasStaticInited(int index) {
        validateClassIdx(index);
        int address = getStaticInitTable() + index;
        boolean answer = read8(address) != 0;
        write8(address, (byte) 1);
        return answer;
    }

}
