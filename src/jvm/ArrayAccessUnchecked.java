package jvm;

import annotations.Alias;
import annotations.NoThrow;
import annotations.WASM;

import static jvm.JVM32.*;

public class ArrayAccessUnchecked {

    @NoThrow
    @Alias(names = "i64ArrayStore")
    public static void arrayStore(int instance, int index, long value) {
        write64(instance + arrayOverhead + (index << 3), value);
    }

    @NoThrow
    @Alias(names = "i32ArrayStore")
    public static void arrayStore(int instance, int index, int value) {
        write32(instance + arrayOverhead + (index << 2), value);
    }

    @NoThrow
    @Alias(names = "f64ArrayStore")
    public static void arrayStore(int instance, int index, double value) {
        write64(instance + arrayOverhead + (index << 3), value);
    }

    @NoThrow
    @Alias(names = "f32ArrayStore")
    public static void arrayStore(int instance, int index, float value) {
        write32(instance + arrayOverhead + (index << 2), value);
    }

    @NoThrow
    @Alias(names = "i16ArrayStore")
    public static void arrayStore(int instance, int index, short value) {
        write16(instance + arrayOverhead + (index << 1), value);
    }

    @NoThrow
    @Alias(names = "i8ArrayStore")
    public static void arrayStore(int instance, int index, byte value) {
        write8(instance + arrayOverhead + index, value);
    }

    @NoThrow
    @Alias(names = "i64ArrayLoad")
    public static long arrayLoad64(int instance, int index) {
        return read64(instance + arrayOverhead + (index << 3));
    }

    @NoThrow
    @Alias(names = "i32ArrayLoad")
    public static int arrayLoad32(int instance, int index) {
        return read32(instance + arrayOverhead + (index << 2));
    }

    @NoThrow
    @Alias(names = "f64ArrayLoad")
    public static double arrayLoad64f(int instance, int index) {
        return read64f(instance + arrayOverhead + (index << 3));
    }

    @NoThrow
    @Alias(names = "f32ArrayLoad")
    public static float arrayLoad32f(int instance, int index) {
        return read32f(instance + arrayOverhead + (index << 2));
    }

    @NoThrow
    @Alias(names = "u16ArrayLoad")
    public static char arrayLoad16u(int instance, int index) {
        return read16u(instance + arrayOverhead + (index << 1));
    }

    @NoThrow
    @Alias(names = "s16ArrayLoad")
    public static short arrayLoad16s(int instance, int index) {
        return read16s(instance + arrayOverhead + (index << 1));
    }

    @NoThrow
    @Alias(names = "i8ArrayLoad")
    public static byte arrayLoad8(int instance, int index) {
        return read8(instance + arrayOverhead + index);
    }

    @NoThrow
    @Alias(names = "al")
    public static int arrayLength(int instance) {
        return read32(instance + objectOverhead);
    }

}
