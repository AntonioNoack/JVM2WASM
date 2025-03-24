package jvm;

import annotations.Alias;
import annotations.NoThrow;

import static jvm.JVM32.*;

public class ArrayAccessUnchecked {

    @NoThrow
    @Alias(names = "i32ArrayStoreU")
    public static void arrayStore(Object instance, int index, int value) {
        writeI32AtOffset(instance, arrayOverhead + (index << 2), value);
    }

    @NoThrow
    @Alias(names = "i64ArrayStoreU")
    public static void arrayStore(Object instance, int index, long value) {
        writeI64AtOffset(instance, arrayOverhead + (index << 3), value);
    }

    @NoThrow
    @Alias(names = "f32ArrayStoreU")
    public static void arrayStore(Object instance, int index, float value) {
        writeF32AtOffset(instance, arrayOverhead + (index << 2), value);
    }

    @NoThrow
    @Alias(names = "f64ArrayStoreU")
    public static void arrayStore(Object instance, int index, double value) {
        writeF64AtOffset(instance, arrayOverhead + (index << 3), value);
    }

    @NoThrow
    @Alias(names = "i8ArrayStoreU")
    public static void arrayStore(Object instance, int index, byte value) {
        writeI8AtOffset(instance, arrayOverhead + index, value);
    }

    @NoThrow
    @Alias(names = "i16ArrayStoreU")
    public static void arrayStore(Object instance, int index, short value) {
        writeI16AtOffset(instance, arrayOverhead + (index << 1), value);
    }

    @NoThrow
    @Alias(names = "i32ArrayLoadU")
    public static int arrayLoad32(Object instance, int index) {
        return readI32AtOffset(instance, arrayOverhead + (index << 2));
    }

    @NoThrow
    @Alias(names = "i64ArrayLoadU")
    public static long arrayLoad64(Object instance, int index) {
        return readI64AtOffset(instance, arrayOverhead + (index << 3));
    }

    @NoThrow
    @Alias(names = "f32ArrayLoadU")
    public static float arrayLoad32f(Object instance, int index) {
        return readF32AtOffset(instance, arrayOverhead + (index << 2));
    }

    @NoThrow
    @Alias(names = "f64ArrayLoadU")
    public static double arrayLoad64f(Object instance, int index) {
        return readF64AtOffset(instance, arrayOverhead + (index << 3));
    }

    @NoThrow
    @Alias(names = "s8ArrayLoadU")
    public static byte arrayLoad8(Object instance, int index) {
        return readI8AtOffset(instance, arrayOverhead + index);
    }

    @NoThrow
    @Alias(names = "u16ArrayLoadU")
    public static char arrayLoad16u(Object instance, int index) {
        return readU16AtOffset(instance, arrayOverhead + (index << 1));
    }

    @NoThrow
    @Alias(names = "s16ArrayLoadU")
    public static short arrayLoad16s(Object instance, int index) {
        return readS16AtOffset(instance, arrayOverhead + (index << 1));
    }

    @NoThrow
    public static int arrayLength(int instance) {
        return arrayLength(ptrTo(instance));
    }

    @NoThrow
    @Alias(names = "arrayLengthU")
    public static int arrayLength(Object instance) {
        return readI32AtOffset(instance, objectOverhead);
    }

}
