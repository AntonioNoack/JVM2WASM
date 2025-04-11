package jvm;

import annotations.NoThrow;
import annotations.WASM;

/**
 * implementations for 64-bits
 */
public class Pointer64 {
    @NoThrow
    @WASM(code = "i64.extend_i32_u i64.add")
    public static native Pointer add(Pointer p, int offset);

    @NoThrow
    @WASM(code = "i64.add")
    public static native Pointer add(Pointer p, long offset);

    @NoThrow
    @WASM(code = "i64.add")
    public static native Pointer add(Pointer p, Pointer offset);

    @NoThrow
    @WASM(code = "i64.sub")
    public static native Pointer sub(Pointer p, Pointer offset);

    @NoThrow
    @WASM(code = "i64.extend_i32_u i64.sub")
    public static native Pointer sub(Pointer p, int offset);

    @NoThrow
    @WASM(code = "i64.sub")
    public static native long diff(Pointer p, Pointer offset);

    @NoThrow
    public static <V> V ptrTo(int addr) {
        return ptrTo((long) addr);
    }

    @NoThrow
    @WASM(code = "")
    public static native <V> V ptrTo(long addr);

    @NoThrow
    @WASM(code = "")
    public static native long getAddrS(Object obj);

    @NoThrow
    @WASM(code = "i64.lt_u")
    public static native boolean unsignedLessThan(Pointer a, Pointer b);

    @NoThrow
    @WASM(code = "i64.ge_u")
    public static native boolean unsignedGreaterThanEqual(Pointer a, Pointer b);

    @NoThrow
    @WASM(code = "i64.extend_i32_s i64.and i64.eqz i32.eqz")
    public static native boolean hasFlag(Pointer addr, int mask);

    @NoThrow
    @WASM(code = "i64.extend_i32_s i64.add")
    public static native Pointer addr(Object p, int offset);
}
