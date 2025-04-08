package jvm;

import annotations.NoThrow;
import annotations.WASM;

/**
 * pseudo-class to represent pointers
 */
public class Pointer {
    @NoThrow
    @WASM(code = "i32.add")
    public static native Pointer add(Pointer p, int offset);
    @NoThrow
    @WASM(code = "i32.wrap_i64 i32.add")
    public static native Pointer add(Pointer p, long offset);
    @NoThrow
    @WASM(code = "i32.add")
    public static native Pointer add(Pointer p, Pointer offset);

    @NoThrow
    @WASM(code = "i32.sub")
    public static native Pointer sub(Pointer p, Pointer offset);

    @NoThrow
    @WASM(code = "i32.sub")
    public static native Pointer sub(Pointer p, int offset);

    @NoThrow
    @WASM(code = "i32.sub i64.extend_i32_u")
    public static native long diff(Pointer p, Pointer offset);

    @NoThrow
    @WASM(code = "")
    public static native <V> V ptrTo(int addr);

    @NoThrow
    public static <V> V ptrTo(long addr) {
        return ptrTo((int) addr);
    }

    @NoThrow
    @WASM(code = "i64.extend_i32_u")
    public static native long getAddrS(Object obj);

    @NoThrow
    @WASM(code = "i32.lt_u")
    public static native boolean unsignedLessThan(Pointer a, Pointer b);

    @NoThrow
    @WASM(code = "i32.ge_u")
    public static native boolean unsignedGreaterThanEqual(Pointer a, Pointer b);

}
