package jvm;

import annotations.Alias;
import annotations.NoThrow;

import static jvm.JVM32.arrayOverhead;
import static jvm.JVM32.objectOverhead;
import static jvm.JVMShared.*;

public class ArrayAccessUnchecked {

    @NoThrow
    @Alias(names = "i64ArrayStoreU")
    public static void arrayStore(int instance, int index, long value) {
        write64(instance + arrayOverhead + (index << 3), value);
    }

    @NoThrow
    @Alias(names = "i32ArrayStoreU")
    public static void arrayStore(int instance, int index, int value) {
        write32(instance + arrayOverhead + (index << 2), value);
    }

    @NoThrow
    @Alias(names = "f64ArrayStoreU")
    public static void arrayStore(int instance, int index, double value) {
        write64(instance + arrayOverhead + (index << 3), value);
    }

    @NoThrow
    @Alias(names = "f32ArrayStoreU")
    public static void arrayStore(int instance, int index, float value) {
        write32(instance + arrayOverhead + (index << 2), value);
    }

    @NoThrow
    @Alias(names = "i16ArrayStoreU")
    public static void arrayStore(int instance, int index, short value) {
        write16(instance + arrayOverhead + (index << 1), value);
    }

    @NoThrow
    @Alias(names = "i8ArrayStoreU")
    public static void arrayStore(int instance, int index, byte value) {
        write8(instance + arrayOverhead + index, value);
    }

    @NoThrow
    @Alias(names = "i64ArrayLoadU")
    public static long arrayLoad64(int instance, int index) {
        return read64(instance + arrayOverhead + (index << 3));
    }

    @NoThrow
    @Alias(names = "i32ArrayLoadU")
    public static int arrayLoad32(int instance, int index) {
        return read32(instance + arrayOverhead + (index << 2));
    }

    @NoThrow
    @Alias(names = "f64ArrayLoadU")
    public static double arrayLoad64f(int instance, int index) {
        return read64f(instance + arrayOverhead + (index << 3));
    }

    @NoThrow
    @Alias(names = "f32ArrayLoadU")
    public static float arrayLoad32f(int instance, int index) {
        return read32f(instance + arrayOverhead + (index << 2));
    }

    @NoThrow
    @Alias(names = "u16ArrayLoadU")
    public static char arrayLoad16u(int instance, int index) {
        return read16u(instance + arrayOverhead + (index << 1));
    }

    @NoThrow
    @Alias(names = "s16ArrayLoadU")
    public static short arrayLoad16s(int instance, int index) {
        return read16s(instance + arrayOverhead + (index << 1));
    }

    @NoThrow
    @Alias(names = "s8ArrayLoadU")
    public static byte arrayLoad8(int instance, int index) {
        return read8(instance + arrayOverhead + index);
    }

    @NoThrow
    @Alias(names = "arrayLengthU")
    public static int arrayLength(int instance) {
        return read32(instance + objectOverhead);
    }

}
