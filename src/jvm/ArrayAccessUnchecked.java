package jvm;

import annotations.Alias;
import annotations.NoThrow;
import annotations.PureJavaScript;

import static jvm.JVMFlags.ptrSize;
import static jvm.JVMShared.*;

public class ArrayAccessUnchecked {

    @NoThrow
    @Alias(names = "i32ArrayStoreU")
    @PureJavaScript(code = "arg0.values[arg1] = arg2;")
    public static void arrayStoreU32(Object instance, int index, int value) {
        writeI32AtOffset(instance, arrayOverhead + (index << 2), value);
    }

    @NoThrow // used by replacement logic for (un)safe array access
    @SuppressWarnings("unused")
    @PureJavaScript(code = "arg0.values[arg1] = arg2;")
    public static void arrayStoreUPtr(Object[] instance, int index, Object value) {
        writePtrAtOffset(instance, arrayOverhead + index * ptrSize, value);
    }

    @NoThrow
    @Alias(names = "i64ArrayStoreU")
    @PureJavaScript(code = "arg0.values[arg1] = arg2;")
    public static void arrayStoreU64(Object instance, int index, long value) {
        writeI64AtOffset(instance, arrayOverhead + (index << 3), value);
    }

    @NoThrow
    @Alias(names = "f32ArrayStoreU")
    @PureJavaScript(code = "arg0.values[arg1] = arg2;")
    public static void arrayStoreU32(float[] instance, int index, float value) {
        writeF32AtOffset(instance, arrayOverhead + (index << 2), value);
    }

    @NoThrow
    @Alias(names = "f64ArrayStoreU")
    @PureJavaScript(code = "arg0.values[arg1] = arg2;")
    public static void arrayStoreU64(double[] instance, int index, double value) {
        writeF64AtOffset(instance, arrayOverhead + (index << 3), value);
    }

    @NoThrow
    @Alias(names = "i8ArrayStoreU")
    @PureJavaScript(code = "arg0.values[arg1] = arg2;")
    public static void arrayStore8U(Object instance, int index, byte value) {
        writeI8AtOffset(instance, arrayOverhead + index, value);
    }

    @NoThrow
    @Alias(names = "i16ArrayStoreU")
    @PureJavaScript(code = "arg0.values[arg1] = arg2;")
    public static void arrayStoreU(char[] instance, int index, short value) {
        writeI16AtOffset(instance, arrayOverhead + (index << 1), value);
    }

    @NoThrow
    @Alias(names = "i32ArrayLoadU")
    @PureJavaScript(code = "return arg0.values[arg1];")
    public static int arrayLoad32U(Object instance, int index) {
        return readI32AtOffset(instance, arrayOverhead + (index << 2));
    }

    @NoThrow
    @Alias(names = "i64ArrayLoadU")
    @PureJavaScript(code = "return arg0.values[arg1];")
    public static long arrayLoad64U(Object instance, int index) {
        return readI64AtOffset(instance, arrayOverhead + (index << 3));
    }

    @NoThrow
    @Alias(names = "f32ArrayLoadU")
    @PureJavaScript(code = "return arg0.values[arg1];")
    public static float arrayLoad32fU(float[] instance, int index) {
        return readF32AtOffset(instance, arrayOverhead + (index << 2));
    }

    @NoThrow
    @Alias(names = "f64ArrayLoadU")
    @PureJavaScript(code = "return arg0.values[arg1];")
    public static double arrayLoad64fU(double[] instance, int index) {
        return readF64AtOffset(instance, arrayOverhead + (index << 3));
    }

    @NoThrow
    @Alias(names = "s8ArrayLoadU")
    @PureJavaScript(code = "return arg0.values[arg1];")
    public static byte arrayLoad8U(Object instance, int index) {
        return readI8AtOffset(instance, arrayOverhead + index);
    }

    @NoThrow
    @Alias(names = "u16ArrayLoadU")
    @PureJavaScript(code = "return arg0.values[arg1];")
    public static char arrayLoad16uU(char[] instance, int index) {
        return readU16AtOffset(instance, arrayOverhead + (index << 1));
    }

    @NoThrow
    @Alias(names = "s16ArrayLoadU")
    @PureJavaScript(code = "return arg0.values[arg1];")
    public static short arrayLoad16sU(short[] instance, int index) {
        return readS16AtOffset(instance, arrayOverhead + (index << 1));
    }

    @NoThrow
    @Alias(names = "arrayLengthU")
    @PureJavaScript(code = "return arg0.values.length;")
    public static int arrayLengthU(Object instance) {
        return readI32AtOffset(instance, objectOverhead);
    }
}
