package jvm;

import annotations.Alias;
import annotations.JavaScript;
import annotations.NoThrow;
import annotations.WASM;
import jvm.lang.Bean;
import kotlin.jvm.internal.ClassBasedDeclarationContainer;
import kotlin.jvm.internal.ClassReference;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.*;
import java.util.Collection;
import java.util.Collections;

import static jvm.JVM32.*;
import static jvm.Utils.cl;

@SuppressWarnings("unused")
public class JavaLang {

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_floor_DD", "java_lang_Math_floor_DD"})
    @WASM(code = "f64.floor")
    public static native double java_lang_StrictMath_floor_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_ceil_DD", "java_lang_Math_ceil_DD"})
    @WASM(code = "f64.ceil")
    public static native double java_lang_StrictMath_ceil_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_rint_DD", "java_lang_Math_rint_DD"})
    @WASM(code = "f64.nearest")
    public static native double java_lang_StrictMath_rint_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_sin_DD", "java_lang_Math_sin_DD"})
    @JavaScript(code = "return Math.sin(arg0);")
    public static native double java_lang_StrictMath_sin_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_cos_DD", "java_lang_Math_cos_DD"})
    @JavaScript(code = "return Math.cos(arg0);")
    public static native double java_lang_StrictMath_cos_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_tan_DD", "java_lang_Math_tan_DD"})
    @JavaScript(code = "return Math.tan(arg0);")
    public static native double java_lang_StrictMath_tan_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_asin_DD", "java_lang_Math_asin_DD"})
    @JavaScript(code = "return Math.asin(arg0);")
    public static native double java_lang_StrictMath_asin_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_acos_DD", "java_lang_Math_acos_DD"})
    @JavaScript(code = "return Math.acos(arg0);")
    public static native double java_lang_StrictMath_acos_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_atan_DD", "java_lang_Math_atan_DD"})
    @JavaScript(code = "return Math.atan(arg0);")
    public static native double java_lang_StrictMath_atan_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_sinh_DD", "java_lang_Math_sinh_DD"})
    @JavaScript(code = "return Math.sinh(arg0);")
    public static native double java_lang_StrictMath_sinh_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_cosh_DD", "java_lang_Math_cosh_DD"})
    @JavaScript(code = "return Math.cosh(arg0);")
    public static native double java_lang_StrictMath_cosh_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_tanh_DD", "java_lang_Math_tanh_DD"})
    @JavaScript(code = "return Math.tanh(arg0);")
    public static native double java_lang_StrictMath_tanh_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_asinh_DD", "java_lang_Math_asinh_DD"})
    @JavaScript(code = "return Math.asinh(arg0);")
    public static native double java_lang_StrictMath_asinh_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_acosh_DD", "java_lang_Math_acosh_DD"})
    @JavaScript(code = "return Math.acosh(arg0);")
    public static native double java_lang_StrictMath_acosh_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_atanh_DD", "java_lang_Math_atanh_DD"})
    @JavaScript(code = "return Math.atanh(arg0);")
    public static native double java_lang_StrictMath_atanh_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_exp_DD", "java_lang_Math_exp_DD"})
    @JavaScript(code = "return Math.exp(arg0);")
    public static native double java_lang_StrictMath_exp_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_log_DD", "java_lang_Math_log_DD"})
    @JavaScript(code = "return Math.log(arg0);")
    public static native double java_lang_StrictMath_log_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_log10_DD", "java_lang_Math_log10_DD"})
    @JavaScript(code = "return Math.log10(arg0);")
    public static native double java_lang_StrictMath_log10_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_sqrt_DD", "java_lang_Math_sqrt_DD"})
    @WASM(code = "f64.sqrt")
    public static native double java_lang_StrictMath_sqrt_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_atan2_DDD", "java_lang_Math_atan2_DDD"})
    @JavaScript(code = "return Math.atan2(arg0,arg1);")
    public static native double StrictMath_atan2_DDD(double y, double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_hypot_DDD", "java_lang_Math_hypot_DDD"})
    @JavaScript(code = "return Math.hypot(arg0,arg1);")
    public static native double StrictMath_hypot_DDD(double x, double y);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_pow_DDD", "java_lang_Math_pow_DDD"})
    @JavaScript(code = "return Math.pow(arg0,arg1);")
    public static native double StrictMath_pow_DDD(double x, double y);

    @NoThrow
    @Alias(names = "java_lang_Math_round_FI")
    @JavaScript(code = "return Math.round(arg0)")
    public static native int java_lang_Math_round_FI(float f);

    @NoThrow
    @Alias(names = "java_lang_Float_floatToRawIntBits_FI")
    @WASM(code = "i32.reinterpret_f32")
    public static native int Float_floatToRawIntBits_FI(float f);

    @NoThrow
    @Alias(names = "java_lang_Double_doubleToRawLongBits_DJ")
    @WASM(code = "i64.reinterpret_f64")
    public static native long Double_doubleToRawLongBits_DJ(double f);

    @NoThrow
    @Alias(names = "java_lang_Float_intBitsToFloat_IF")
    @WASM(code = "f32.reinterpret_i32")
    public static native float java_lang_Float_intBitsToFloat(int bits);

    @NoThrow
    @Alias(names = "java_lang_Double_longBitsToDouble_JD")
    @WASM(code = "f64.reinterpret_i64")
    public static native double java_lang_Double_longBitsToDouble_JD(long bits);

    @NoThrow
    @Alias(names = "java_lang_Math_min_FFF")
    @WASM(code = "f32.min")
    public static native float java_lang_Math_min(float a, float b);

    @NoThrow
    @Alias(names = "java_lang_Math_max_FFF")
    @WASM(code = "f32.max")
    public static native float java_lang_Math_max(float a, float b);

    @NoThrow
    @Alias(names = "java_lang_Math_min_DDD")
    @WASM(code = "f64.min")
    public static native float java_lang_Math_min(double a, double b);

    @NoThrow
    @Alias(names = "java_lang_Math_max_DDD")
    @WASM(code = "f64.max")
    public static native float java_lang_Math_max(double a, double b);

    @NoThrow
    @Alias(names = "java_lang_Math_abs_FF")
    @WASM(code = "f32.abs")
    public static native float java_lang_Math_abs_FF(float a);

    @NoThrow
    @Alias(names = "java_lang_Math_abs_DD")
    @WASM(code = "f64.abs")
    public static native double java_lang_Math_abs_DD(double a);

    // original implementation, just no throw
    @NoThrow
    @Alias(names = "java_lang_Double_isNaN_DZ")
    public static boolean isNaN(double d) {
        return d != d;
    }

    @NoThrow
    @Alias(names = "java_lang_Float_isNaN_FZ")
    public static boolean isNaN(float d) {
        return d != d;
    }

    @NoThrow
    @Alias(names = "java_lang_ClassLoader_desiredAssertionStatus_Ljava_lang_StringZ")
    public static boolean desiredAssertionStatus(String s) {
        return false;
    }

    @NoThrow
    @Alias(names = "java_lang_Class_desiredAssertionStatus_Z")
    public static boolean desiredAssertionStatus(Class<Object> clazz) {
        return false;
    }

    private static boolean copyQuickly(int a, int b, int length) {
        return (a >= b + length) || (b >= a + length);
    }

    private static boolean copyBackwards(int a, int b) {
        return a < b;
    }

    public static void checkIndices(int a, int l, int al) {
        if (a < 0 || a + l > al) throw new IndexOutOfBoundsException();
    }

    @Alias(names = "java_lang_System_arraycopy_Ljava_lang_ObjectILjava_lang_ObjectIIV")
    public static void arraycopy(Object src, int srcIndex, Object dst, int dstIndex, int length) {

        if (src == null || dst == null) throw new NullPointerException("arraycopy");
        if (length < 0) throw new IllegalArgumentException("length<0");
        if ((src == dst && srcIndex == dstIndex) || length == 0) return; // done :)
        int src1 = getAddr(src), dst1 = getAddr(dst);

        int clazz1 = readClass(src1), clazz2 = readClass(dst1);
        if (clazz1 != clazz2) throw new RuntimeException("Mismatched types");

        int delta = arrayOverhead;
        int shift = getTypeShift(clazz1);// shall be executed here to confirm that this is arrays
        int src2 = src1 + delta + (srcIndex << shift);
        int dst2 = dst1 + delta + (dstIndex << shift);

        checkIndices(srcIndex, length, arrayLength(src1));
        checkIndices(dstIndex, length, arrayLength(dst1));

        int bytes = length << shift;
        if (src != dst || srcIndex > dstIndex) copyForwards(src2, dst2, bytes);
        else copyBackwards(src2, dst2, bytes);
    }

    @NoThrow
    @WASM(code = "") // auto
    public static native int getAddr(Object obj);

    @NoThrow
    @Alias(names = "copy")
    public static void copyForwards(int src, int dst, int length) {
        int endPtr = dst + length;
        int end8 = endPtr - 7;
        for (; unsignedLessThan(dst, end8); dst += 8, src += 8) {
            write64(dst, read64(src));
        }
        if ((length & 4) != 0) {
            write32(dst, read32(src));
            dst += 4;
            src += 4;
        }
        if ((length & 2) != 0) {
            write16(dst, read16u(src));
            dst += 2;
            src += 2;
        }
        if ((length & 1) != 0) {
            write8(dst, read8(src));
        }
    }

    @NoThrow
    @Alias(names = "copyBW")
    public static void copyBackwards(int src, int dst, int length) {
        int endPtr = dst - 1;
        // we start at the end
        src += length - 8;
        dst += length - 8;
        for (; unsignedLessThan(endPtr, dst); dst -= 8, src -= 8) {
            write64(dst, read64(src));
        }
        // looks correct :)
        if ((length & 4) != 0) {
            write32(dst + 4, read32(src + 4));
        } else {
            src += 4;
            dst += 4;
        }
        if ((length & 2) != 0) {
            write16(dst + 2, read16u(src + 2));
        } else {
            src += 2;
            dst += 2;
        }
        if ((length & 1) != 0) {
            write8(dst + 1, read8(src + 1));
        }
    }

    @NoThrow
    @Alias(names = "java_lang_System_currentTimeMillis_J")
    @JavaScript(code = "return BigInt(Date.now());")
    public static native long java_lang_System_currentTimeMillis_J();

    @NoThrow
    @Alias(names = "java_lang_System_nanoTime_J")
    @JavaScript(code = "return BigInt(Math.round(performance.now()*1e6));")
    public static native long java_lang_System_nanoTime_J();

    private static final int stackReportLimit = 256;

    @Alias(names = "java_lang_Throwable_getStackTraceDepth_I")
    public static int Throwable_getStackTraceDepth_I(Throwable th) {
        // is this correct, where is it used?
        return (getStackPtr0() - getStackPtr()) >> 2;
    }

    @JavaScript(code = "trace(arg0)")
    @Alias(names = "java_lang_Throwable_printStackTrace_V")
    public static native void java_lang_Throwable_printStackTrace_V(Throwable th);

    @NoThrow
    @Alias(names = "java_lang_Throwable_fillInStackTrace_Ljava_lang_Throwable")
    public static Throwable Throwable_fillInStackTraceI0(Throwable th) throws NoSuchFieldException, IllegalAccessException {
        return Throwable_fillInStackTraceI(th);
    }

    private static boolean insideFIST = false;

    @NoThrow
    @Alias(names = "fIST")
    public static Throwable Throwable_fillInStackTraceI(Throwable th) throws NoSuchFieldException, IllegalAccessException {

        // todo this is supposed to store the state, but not yet create the stack elements
        // todo -> create an int[] instead to save a little memory :)
        // (if we don't use the trace)

        if (insideFIST) {
            log("Error within fIST!");
            return th;
        }

        insideFIST = true;

        int sp = getStackPtr();
        int stackLength = (getStackPtr0() - sp) >> 2;// each element is 4 bytes in size currently
        final int stackLength0 = stackLength;
        // log("stack ptr", sp);
        // log("stack ptr0", getStackPtr0());
        // log("stack length", stackLength);
        if (stackLength < 1) {
            insideFIST = false;
            return th;
        }

        // todo there is a leak somewhere: Engine.update() and GFXBase2Kt.renderFrame2 are put onto the stack every frame, but not taken off.
        boolean reachedLimit = false;
        if (stackLength >= stackReportLimit) {
            stackLength = stackReportLimit;
            reachedLimit = true;
        }

        int lookupBasePtr = getLookupBasePtr();
        if (lookupBasePtr <= 0) {
            insideFIST = false;
            return th;
        }

        criticalAlloc = true;

        StackTraceElement[] array0 = th != null ? ptrTo(read32(getAddr(th) + objectOverhead + 4)) : null;
        StackTraceElement[] array1 = array0 != null && array0.length == stackLength ? array0 : new StackTraceElement[stackLength];
        /*if (array1 == null) {
            // how? branch generation was broken
            throwJs("Array1 is null somehow...", getAddr(array0), getAddr(array1), stackLength);
            insideFIST = false;
            return th;
        }*/
        // assign stackTrace; detailMessage, then stackTrace -> 4 offset
        if (th != null) write32(getAddr(th) + objectOverhead + 4, getAddr(array1));

        for (int i = 0; i < stackLength; i++) {
            int stackData = read32(sp);
            int throwableLookup = lookupBasePtr + stackData * 12;
            // log("stack data", i, stackData);
            String className = ptrTo(read32(throwableLookup));
            String methodName = ptrTo(read32(throwableLookup + 4));
            int line = read32(throwableLookup + 8);
            fillInElement(className, methodName, line, array1, i);
            sp += 4;
        }

        if (reachedLimit) {
            fillInElement("Warning", "Reached Stack Limit!",
                    stackLength0, array1, stackLength - 1);
        }

        criticalAlloc = false;

        // if (th != null) printStackTrace(th);
        // else printStackTrace(array1);

        insideFIST = false;
        return th;
    }

    @NoThrow
    @SuppressWarnings("JavaReflectionMemberAccess")
    private static void fillInElement(String className, String methodName, int line, StackTraceElement[] array1, int i) throws NoSuchFieldException, IllegalAccessException {
        // log("Fill In Element", className, methodName);
        // log("Fill In Element", line, getAddr(array1), i);
        // if (i >= array1.length) throwJs("Index out of bounds", getAddr(array1), i, array1.length);
        StackTraceElement element = array1[i];
        if (element == null) {
            array1[i] = new StackTraceElement(className, methodName, className, line);
        } else {
            // if field was already defined, reuse it :)
            Class<StackTraceElement> clazz = StackTraceElement.class;
            if (!className.equals(element.getClassName())) clazz.getField("declaringClass").set(element, className);
            if (!methodName.equals(element.getMethodName())) clazz.getField("methodName").set(element, methodName);
            if (!className.equals(element.getFileName())) clazz.getField("fileName").set(element, className);
            if (element.getLineNumber() != line) clazz.getField("lineNumber").setInt(element, line);
        }
    }

    @NoThrow
    public static void printStackTrace() {

        int sp = getStackPtr();
        int stackLength = (getStackPtr0() - sp) >> 2;// each element is 4 bytes in size currently
        if (stackLength >= stackReportLimit) stackLength = stackReportLimit;
        int lookupBasePtr = getLookupBasePtr();
        if (lookupBasePtr <= 0) return;
        if (stackLength < 1) return;

        int endPtr = sp + (stackLength << 2);
        int i = 0;
        while (unsignedLessThan(sp, endPtr)) {
            int stackData = read32(sp);
            int throwableLookup = lookupBasePtr + stackData * 12;
            String className = ptrTo(read32(throwableLookup));
            String methodName = ptrTo(read32(throwableLookup + 4));
            int line = read32(throwableLookup + 8);
            log(i, className, methodName, line);
            sp += 4;
            i++;
        }

    }

    @Alias(names = "java_lang_Thread_getStackTrace_ALjava_lang_StackTraceElement")
    public static StackTraceElement[] Thread_getStackTrace(Thread thread) {

        int sp = getStackPtr();
        int stackLength = (getStackPtr0() - sp) >> 2;// each element is 4 bytes in size currently
        if (stackLength < 1) return new StackTraceElement[0];
        if (stackLength >= stackReportLimit) stackLength = stackReportLimit;
        int lookupBasePtr = getLookupBasePtr();
        if (lookupBasePtr <= 0) return new StackTraceElement[0];

        StackTraceElement[] array = new StackTraceElement[stackLength];
        for (int i = 0; i < stackLength; i++) {
            int stackData = read32(sp);
            int throwableLookup = lookupBasePtr + stackData * 12;
            // log("stack data", i, stackData);
            String clazz = ptrTo(read32(throwableLookup));
            String name = ptrTo(read32(throwableLookup + 4));
            int line = read32(throwableLookup + 8);
            array[i] = new StackTraceElement(clazz, name, clazz, line);
            sp += 4;
        }
        return array;
    }

    @NoThrow
    @WASM(code = "")
    public static native <V> V ptrTo(int addr);

    @NoThrow
    @JavaScript(code = "console.log(arg0, trace(arg0))")
    public static native void printStackTrace(Throwable th);

    @NoThrow
    @JavaScript(code = "console.log(trace1(arg0))")
    public static native void printStackTrace(StackTraceElement[] th);

    @NoThrow
    @WASM(code = "global.get $L")
    public static native int getLookupBasePtr();

    @NoThrow
    @Alias(names = "resetSP")
    public static void resetStackPtr() {
        // must be called after every wasm-native error like segfaults
        setStackPtr(getStackPtr0());
    }

    static class JSOutputStream extends OutputStream {

        public JSOutputStream(boolean justLog) {
            this.justLog = justLog;
        }

        private final boolean justLog;

        @Override
        public void write(int i) {
            if (i != '\n') {
                printByte(i, justLog);
            } else flush();
        }

        @Override
        public void flush() {
            printFlush(justLog);
        }
    }

    @NoThrow
    @Alias(names = "java_lang_System_registerNatives_V")
    public static void registerNatives() {
    }

    @NoThrow
    @Alias(names = "java_lang_Thread_registerNatives_V")
    public static void registerNatives2() {
    }

    @NoThrow
    @Alias(names = "java_lang_Thread_setPriority_IV")
    public static void java_lang_Thread_setPriority_IV(Thread self, int v) {
    }

    @NoThrow
    @JavaScript(code = "commandLine[arg1].push(String.fromCharCode(arg0))")
    public static native void printByte(int i, boolean justLog);

    @NoThrow
    @JavaScript(code = "let c=commandLine[arg0];if(c.length>0){if(!arg0)ec++;(arg0?console.log:console.error)(c.join('')); commandLine[arg0] = []}")
    public static native void printFlush(boolean justLog);

    @NoThrow
    @Alias(names = "java_lang_Object_getClass_Ljava_lang_Class")
    public static int Object_getClass(int instance) {
        return findClass2(readClass(instance));
    }

    @Alias(names = "java_lang_Object_hashCode_I")
    public static int Object_hashCode(Object instance) {
        return getAddr(instance);
    }

    @Alias(names = "java_lang_Class_getPrimitiveClass_Ljava_lang_StringLjava_lang_Class")
    public static Class Class_getPrimitiveClass(String name) {
        switch (name) {
            case "void":
                return Void.class;
            case "boolean":
                return Boolean.class;
            case "byte":
                return Byte.class;
            case "char":
                return Character.class;
            case "short":
                return Short.class;
            case "int":
                return Integer.class;
            case "long":
                return Long.class;
            case "float":
                return Float.class;
            case "double":
                return Double.class;
            default:
                throw new IllegalArgumentException(name);
        }
    }

    private static Thread thread;

    @Alias(names = "java_lang_Thread_currentThread_Ljava_lang_Thread")
    public static Thread Thread_currentThread() {
        if (thread == null) thread = new Thread("main");
        return thread;
    }

    @Alias(names = "AW_clone_Ljava_lang_Object")
    public static Object Array_clone(Object[] base) {
        int length = base.length;
        Object[] clone = new Object[length];
        System.arraycopy(base, 0, clone, 0, length);
        return clone;
    }

    @Alias(names = "java_lang_System_setOut0_Ljava_io_PrintStreamV")
    public static void System_setOut0(PrintStream v) throws NoSuchFieldException, IllegalAccessException {
        System.class.getField("out").set(null, v);
    }

    @Alias(names = "java_lang_System_setErr0_Ljava_io_PrintStreamV")
    public static void System_setErr0(PrintStream v) throws NoSuchFieldException, IllegalAccessException {
        System.class.getField("err").set(null, v);
    }

    @NoThrow
    @Alias(names = "java_lang_Class_getClassLoader_Ljava_lang_ClassLoader")
    public static ClassLoader getClassLoader(Class<Object> clazz) {
        return cl;
    }

    @NoThrow
    @Alias(names = "java_lang_ClassLoader_getSystemClassLoader_Ljava_lang_ClassLoader")
    public static ClassLoader ClassLoader_getSystemClassLoader() {
        return cl;
    }

    @Alias(names = "java_lang_Thread_init_Ljava_lang_ThreadGroupLjava_lang_RunnableLjava_lang_StringJLjava_security_AccessControlContextZV")
    public static void privateThreadInit(int threadGroup, int runnable, int name, long stackSize, int ctx, boolean inheritThreadLocals) {

    }

    @Alias(names = "java_lang_Thread_interrupt0_V")
    public static void Thread_interrupt0(Thread th) {
    }

    @NoThrow
    @Alias(names = "java_lang_Class_getFields_ALjava_lang_reflect_Field")
    public static <V> int getFields(Class<V> clazz) {
        return read32(getAddr(clazz) + objectOverhead + 4);
    }

    @Alias(names = "java_lang_Class_getField_Ljava_lang_StringLjava_lang_reflect_Field")
    public static <V> Field Class_getField(Class<V> clazz, String name) throws NoSuchFieldException {
        // using class instance, find fields
        if (clazz == null || name == null) throw new NullPointerException("Class.getField()");
        int fields = getFields(clazz);
        int length = fields == 0 ? 0 : arrayLength(fields);
        // log("class for fields", getAddr(clazz));
        // log("fields*, length", fields, length);
        // log("looking for field", searchedField);
        for (int i = 0; i < length; i++) {
            Field field = ptrTo(read32(fields + arrayOverhead + (i << 2)));
            String fieldName = field.getName();
            // log("field[i]:", fieldName);
            // log("field[i].offset:", getFieldOffset(field));
            if (name.equals(fieldName)) return field;
        }
        // todo use parent class as well
        // todo for that, we need to store the parent class...
        // if (getAddr(clazz) == findClass(0))
        log("looked for field, but failed", clazz.getName(), name);
        throw new NoSuchFieldException(name);
        // else return Class_getField(ptrTo(findClass(getParentClassIdx())), name);
    }

    @Alias(names = "java_lang_Class_getDeclaredField_Ljava_lang_StringLjava_lang_reflect_Field")
    public static <V> Field Class_getDeclaredField(Class<V> clazz, String name) throws NoSuchFieldException {
        return Class_getField(clazz, name);
    }

    @Alias(names = "java_lang_Class_getDeclaredMethods_ALjava_lang_reflect_Method")
    public static <V> int Class_getDeclaredMethods(Class<V> clazz) {
        return read32(getAddr(clazz) + objectOverhead + 8);
    }

    private static final NoSuchMethodException noSuchMethodEx = new NoSuchMethodException();

    @Alias(names = "java_lang_Class_getMethod_Ljava_lang_StringALjava_lang_ClassLjava_lang_reflect_Method")
    public static <V> Method Class_getMethod(Class<V> self, String name, Class[] parameters) throws NoSuchMethodException {
        if (name == null) return null;
        Method[] methods = ptrTo(Class_getDeclaredMethods(self));
        for (Method method : methods) {
            if (matches(method, name, parameters)) return method;
        }
        throw noSuchMethodEx;
    }

    @Alias(names = "java_lang_Class_getDeclaredMethod_Ljava_lang_StringALjava_lang_ClassLjava_lang_reflect_Method")
    public static <V> Method Class_getDeclaredMethod(Class<V> self, String name, Class[] parameters) throws NoSuchMethodException {
        return Class_getMethod(self, name, parameters);
    }

    @Alias(names = "java_lang_Class_getCanonicalName_Ljava_lang_String")
    public static <V> String java_lang_Class_getCanonicalName_Ljava_lang_String(Class<V> self) {
        return self.getName();
    }

    private static boolean matches(Method method, String name, Class[] parameters) {
        if (method.getName().equals(name) && parameters.length == method.getParameterCount()) {
            Class[] params2 = method.getParameterTypes();
            for (int j = 0, l2 = parameters.length; j < l2; j++) {
                if (parameters[j] != params2[j]) {
                    return false;
                }
            }
            return true;
        } else return false;
    }

    public static int getFieldOffset(Field field) {
        int offset = read32(getAddr(field) + objectOverhead + 9);// hardcoded, could be a global :)
        if (offset < objectOverhead && !Modifier.isStatic(field.getModifiers()))
            throw new IllegalStateException("Field offset must not be zero");
        return offset;
    }

    @Alias(names = "java_lang_reflect_Field_get_Ljava_lang_ObjectLjava_lang_Object")
    public static int Field_get(Field field, Object instance) {
        int addr = getFieldAddr(field, instance);
        // if the field is native, we have to wrap it
        Class<?> clazz = field.getType();
        String clazzName = clazz.getName();
        switch (clazzName) {
            case "boolean":
                return getAddr(read8(addr) > 0);
            case "byte":
                return getAddr(read8(addr));
            case "short":
                return getAddr(read16s(addr));
            case "char":
                return getAddr(read16u(addr));
            case "int":
                return getAddr(read32(addr));
            case "long":
                return getAddr(read64(addr));
            case "float":
                return getAddr(read32f(addr));
            case "double":
                return getAddr(read64f(addr));
            default:
                return read32(addr);
        }
    }

    private static int getFieldAddr(Field field, Object instance) {
        int offset = getFieldOffset(field);
        if (Modifier.isStatic(field.getModifiers())) {
            return findStatic(getClassIndex(field.getDeclaringClass()), offset);
        } else {
            if (instance == null) throw new NullPointerException("getFieldAddr");
            return getAddr(instance) + offset;
        }
    }

    @Alias(names = "java_lang_reflect_Field_set_Ljava_lang_ObjectLjava_lang_ObjectV")
    public static void Field_set(Field field, Object instance, Object value) {
        int addr = getFieldAddr(field, instance);
        // log("writing field at", offset);
        // log("wrote obj", getAddr(value));
        Class<?> clazz = field.getType();
        String clazzName = clazz.getName();
        switch (clazzName) {
            case "boolean":
                write8(addr, (byte) (((Boolean) value) ? 1 : 0));
                break;
            case "byte":
                write8(addr, (Byte) value);
                break;
            case "short":
                write16(addr, (Short) value);
                break;
            case "char":
                write16(addr, (Character) value);
                break;
            case "int":
                write32(addr, (Integer) value);
                break;
            case "long":
                write64(addr, (Long) value);
                break;
            case "float":
                write32(addr, (Float) value);
                break;
            case "double":
                write64(addr, (Double) value);
                break;
            default:
                write32(addr, getAddr(value));
                break;
        }
    }

    @Alias(names = "java_lang_reflect_Field_setInt_Ljava_lang_ObjectIV")
    public static void Field_setInt(Field field, Object instance, int value) {
        Class<?> clazz = field.getType();
        if (!"int".equals(clazz.getName())) throw new IllegalArgumentException("Type is not an integer");
        write32(getFieldAddr(field, instance), value);
    }

    @Alias(names = "java_lang_reflect_Field_getInt_Ljava_lang_ObjectI")
    public static int Field_getInt(Field field, Object instance) {
        Class<?> clazz = field.getType();
        if (!"int".equals(clazz.getName())) throw new IllegalArgumentException("Type is not an integer");
        return read32(getFieldAddr(field, instance));
    }

    @Alias(names = "java_lang_reflect_Field_setFloat_Ljava_lang_ObjectFV")
    public static void Field_setFloat(Field field, Object instance, float value) {
        Class<?> clazz = field.getType();
        if (!"float".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a float");
        write32(getFieldAddr(field, instance), value);
    }

    @Alias(names = "java_lang_reflect_Field_getFloat_Ljava_lang_ObjectF")
    public static float Field_getFloat(Field field, Object instance) {
        Class<?> clazz = field.getType();
        if (!"float".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a float");
        return read32f(getFieldAddr(field, instance));
    }

    @Alias(names = "java_lang_reflect_Field_setLong_Ljava_lang_ObjectLV")
    public static void Field_setLong(Field field, Object instance, long value) {
        Class<?> clazz = field.getType();
        if (!"int".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a long");
        write32(getFieldAddr(field, instance), value);
    }

    @Alias(names = "java_lang_reflect_Field_getLong_Ljava_lang_ObjectL")
    public static long Field_getLong(Field field, Object instance) {
        Class<?> clazz = field.getType();
        if (!"long".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a long");
        return read64(getFieldAddr(field, instance));
    }

    @Alias(names = "java_lang_reflect_Field_setDouble_Ljava_lang_ObjectDV")
    public static void Field_setDouble(Field field, Object instance, double value) {
        Class<?> clazz = field.getType();
        if (!"double".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a double");
        write64(getFieldAddr(field, instance), value);
    }

    @Alias(names = "java_lang_reflect_Field_getDouble_Ljava_lang_ObjectD")
    public static double Field_getDouble(Field field, Object instance) {
        Class<?> clazz = field.getType();
        if (!"double".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a double");
        return read64f(getFieldAddr(field, instance));
    }

    @Alias(names = "java_lang_reflect_Field_setByte_Ljava_lang_ObjectBV")
    public static void Field_setByte(Field field, Object instance, byte value) {
        Class<?> clazz = field.getType();
        if (!"byte".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a byte");
        write8(getFieldAddr(field, instance), value);
    }

    @Alias(names = "java_lang_reflect_Field_getByte_Ljava_lang_ObjectB")
    public static byte Field_getByte(Field field, Object instance) {
        Class<?> clazz = field.getType();
        if (!"byte".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a byte");
        return read8(getFieldAddr(field, instance));
    }

    @Alias(names = "java_lang_reflect_Field_setShort_Ljava_lang_ObjectSV")
    public static void Field_setShort(Field field, Object instance, short value) {
        Class<?> clazz = field.getType();
        if (!"short".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a short");
        write16(getFieldAddr(field, instance), value);
    }

    @Alias(names = "java_lang_reflect_Field_getShort_Ljava_lang_ObjectS")
    public static short Field_getShort(Field field, Object instance) {
        Class<?> clazz = field.getType();
        if (!"short".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a short");
        return read16s(getFieldAddr(field, instance));
    }

    @Alias(names = "java_lang_reflect_Field_setChar_Ljava_lang_ObjectCV")
    public static void Field_setChar(Field field, Object instance, char value) {
        Class<?> clazz = field.getType();
        if (!"char".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a char");
        write16(getFieldAddr(field, instance), value);
    }

    @Alias(names = "java_lang_reflect_Field_getChar_Ljava_lang_ObjectC")
    public static char Field_getChar(Field field, Object instance) {
        Class<?> clazz = field.getType();
        if (!"char".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a char");
        return read16u(getFieldAddr(field, instance));
    }

    @Alias(names = "java_lang_reflect_Field_setBoolean_Ljava_lang_ObjectZV")
    public static void Field_setBoolean(Field field, Object instance, boolean value) {
        Class<?> clazz = field.getType();
        if (!"boolean".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a boolean");
        write8(getFieldAddr(field, instance), (byte) (value ? 1 : 0));
    }

    @Alias(names = "java_lang_reflect_Field_getBoolean_Ljava_lang_ObjectZ")
    public static boolean Field_getBoolean(Field field, Object instance) {
        Class<?> clazz = field.getType();
        if (!"boolean".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a boolean");
        return read8(getFieldAddr(field, instance)) != 0;
    }

    @Alias(names = "java_lang_Class_forName_Ljava_lang_StringLjava_lang_Class")
    public static <V> Class<V> Class_forName(String name) throws ClassNotFoundException {
        // iterate over all class instances, and get their names
        // if the name matches, return that class
        log("looking for class", name);
        for (int i = 0, l = numClasses(); i < l; i++) {
            Class<V> clazz = ptrTo(findClass(i));
            // log("instance of class?", clazz instanceof Class ? 1 : 0);
            // log("class", i, clazz.getName());
            if (name.equals(clazz.getName())) return clazz;
        }
        throw new ClassNotFoundException();
    }

    @NoThrow
    private static boolean isSeparator(char c) {
        return c == '.' || c == '/' || c == '\\';
    }

    @Alias(names = "java_lang_Class_forName_Ljava_lang_StringZLjava_lang_ClassLoaderLjava_lang_Class")
    public static <V> Class<V> Class_forName(String name, ClassLoader loader) throws ClassNotFoundException {
        return Class_forName(name);
    }

    @Alias(names = "java_lang_Class_isArray_Z")
    public static <V> boolean Class_isArray(Class<V> clazz) {
        return clazz.getName().charAt(0) == '[';
    }

    @Alias(names = "java_lang_Class_getEnclosingClass_Ljava_lang_Class")
    public static <V> Class<V> Class_getEnclosingClass(Class<V> clazz) {
        // todo could be found at compile time (massive speedup from O(|classes|) to O(1))
        String name = clazz.getName();
        int lio1 = name.lastIndexOf('$');
        int lio2 = name.lastIndexOf('.');
        lio1 = Math.max(lio1, lio2);// find the better index
        if (lio1 < 0) return null;
        String subName = name.substring(0, lio1);
        try {
            return Class_forName(subName);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Alias(names = "java_lang_Class_isLocalClass_Z")
    public static <V> boolean java_lang_Class_isLocalClass_Z(Class<V> clazz) {
        return Class_getEnclosingClass(clazz) != null;
    }

    @Alias(names = "java_lang_System_getProperty_Ljava_lang_StringLjava_lang_String")
    public static String System_getProperty(String key) {
        // todo should be a definable map
        switch (key) {
            case "os.name":
                return "Linux Web";
            case "user.home":
                return "/user/virtual";
            case "java.vm.name":
                return "JVM2WASM";
            case "java.vm.vendor":
                return "AntonioNoack";
            case "java.vm.version":
                return "1.0.0";
        }
        return null;
    }

    @NoThrow
    @Alias(names = "java_lang_Class_checkMemberAccess_ILjava_lang_ClassZV")
    public static <V> void java_lang_Class_checkMemberAccess_ILjava_lang_ClassZV(Class<V> self, int a, Class<V> b, boolean c) {
        // security stuff... whatever...
    }

    @NoThrow
    @Alias(names = "java_lang_Class_registerNatives_V")
    public static void java_lang_Class_registerNatives_V() {

    }

    @Alias(names = "java_lang_Class_isPrimitive_Z")
    public static <V> boolean java_lang_Class_isPrimitive_Z(Class<V> clazz) {
        switch (clazz.getName()) {
            case "boolean":
            case "byte":
            case "char":
            case "short":
            case "float":
            case "integer":
            case "double":
            case "long":
                return true;
            default:
                return false;
        }
    }

    @Alias(names = "java_lang_Class_getComponentType_Ljava_lang_Class")
    public static <V> Class java_lang_Class_getComponentType_Ljava_lang_Class(Class<V> clazz) {
        switch (clazz.getName()) {
            case "[Z":
                return Boolean.class;
            case "[B":
                return Byte.class;
            case "[C":
                return Character.class;
            case "[S":
                return Short.class;
            case "[F":
                return Float.class;
            case "[I":
                return Integer.class;
            case "[D":
                return Double.class;
            case "[J":
                return Long.class;
            case "[V":
                return Void.class;
            case "[]":
                return Object.class;
            default:
                return null;
        }
    }

    @NoThrow
    @Alias(names = "java_lang_String_intern_Ljava_lang_String")
    public static String java_lang_String_intern_Ljava_lang_String(String self) {
        // would we really need to check the whole heap for this function???
        return self;
    }

    @NoThrow
    @Alias(names = "java_lang_Runtime_availableProcessors_I")
    public static int java_lang_Runtime_availableProcessors_I(Runtime rt) {
        return 1;
    }

    @Alias(names = "java_lang_Runtime_totalMemory_J")
    public static long java_lang_Runtime_totalMemory_J(Runtime runtime) {
        return Integer.toUnsignedLong(getAllocatedSize());
    }

    @Alias(names = "java_lang_Runtime_freeMemory_J")
    public static long java_lang_Runtime_freeMemory_J(Runtime runtime) {
        return Integer.toUnsignedLong(GC.freeMemory + getAllocatedSize() - getNextPtr());
    }

    @Alias(names = "java_lang_Object_clone_Ljava_lang_Object")
    public static Object java_lang_Object_clone_Ljava_lang_Object(Object obj)
            throws CloneNotSupportedException, InstantiationException, IllegalAccessException {
        if (obj instanceof Cloneable) {
            return obj.getClass().newInstance();
        } else throw new CloneNotSupportedException();
    }

    private static RuntimeMXBean bean;

    @Alias(names = "java_lang_management_ManagementFactory_getRuntimeMXBean_Ljava_lang_management_RuntimeMXBean")
    public static Object java_lang_management_ManagementFactory_getRuntimeMXBean_Ljava_lang_management_RuntimeMXBean() {
        if (bean == null) bean = new Bean();
        return bean;
    }


    @NoThrow
    @JavaScript(code = "let s=arg1+'';if(s.indexOf('.')<0)s+='.0';return fill(arg0,s)")
    public static native int fillD2S(char[] chr, double v);

    @Alias(names = "java_lang_Double_toString_DLjava_lang_String")
    public static synchronized String Double_toString(double d) {
        char[] builder = FillBuffer.getBuffer();
        ;
        int length = fillD2S(builder, d);
        return new String(builder, 0, length);
    }

    @NoThrow
    @JavaScript(code = "return fill(arg0, arg1.toFixed(arg2))")
    public static native int fillD2S(char[] chr, double v, int digits);

    public static synchronized String toFixed(double d, int digits) {
        char[] builder = FillBuffer.getBuffer();
        int length = fillD2S(builder, d, digits);
        return new String(builder, 0, length);
    }

    @Alias(names = "java_lang_StringBuilder_append_FLjava_lang_StringBuilder")
    public static StringBuilder java_lang_StringBuilder_append_FLjava_lang_StringBuilder(StringBuilder builder, float v) {
        return java_lang_StringBuilder_append_DLjava_lang_StringBuilder(builder, v);
    }

    @Alias(names = "java_lang_AbstractStringBuilder_append_DLjava_lang_AbstractStringBuilder")
    public static StringBuilder java_lang_AbstractStringBuilder_append_DLjava_lang_AbstractStringBuilder(StringBuilder builder, double v) {
        return java_lang_StringBuilder_append_DLjava_lang_StringBuilder(builder, v);
    }

    @Alias(names = "java_lang_StringBuilder_append_DLjava_lang_StringBuilder")
    public static StringBuilder java_lang_StringBuilder_append_DLjava_lang_StringBuilder(StringBuilder builder, double v) {
        char[] content = FillBuffer.getBuffer();
        int length = fillD2S(content, v);
        builder.append(content, 0, length);
        return builder;
    }

    @Alias(names = "java_lang_Float_toString_FLjava_lang_String")
    public static String Float_toString(float f) {
        return Double_toString(f);
    }

    @Alias(names = "java_lang_Package_getSystemPackage0_Ljava_lang_StringLjava_lang_String")
    public static String getSystemPackage0(String var0) {
        return "";
    }

    @Alias(names = "java_lang_System_identityHashCode_Ljava_lang_ObjectI")
    public static int System_identityHashCode(Object obj) {
        // https://stackoverflow.com/questions/664014/what-integer-hash-function-are-good-that-accepts-an-integer-hash-key
        // return getAddr(obj); // good for debugging
        int x = getAddr(obj);
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = (x >>> 16) ^ x;
        return x;
    }

    @Alias(names = "java_lang_System_getenv_Ljava_lang_StringLjava_lang_String")
    public static String System_getenv(String key) {
        return null;
    }

    @NoThrow
    public static <V> int getClassIndex(Class<V> clazz) {
        if (clazz == null) return -1;
        return read32(getAddr(clazz) + objectOverhead + 12);
    }

    @Alias(names = "java_lang_Class_getSuperclass_Ljava_lang_Class")
    public static <V> Class<V> java_lang_Class_getSuperclass_Ljava_lang_Class(Class<V> clazz) {
        int idx = getClassIndex(clazz);
        if (idx <= 0) return null;
        int superClassIdx = getSuperClass(idx);
        return ptrTo(findClass(superClassIdx));
    }

    @NoThrow
    @WASM(code = "global.get $R")
    private static native int getResourcePtr();

    @Alias(names = "java_lang_ClassLoader_getResourceAsStream_Ljava_lang_StringLjava_io_InputStream")
    public static InputStream java_lang_ClassLoader_getResourceAsStream_Ljava_lang_StringLjava_io_InputStream(ClassLoader cl, String name) {
        if (name == null) return null;
        int ptr = getResourcePtr();
        int length = read32(ptr);
        ptr += 4;
        int endPtr = ptr + (length << 3);
        while (ptr < endPtr) {
            String key = ptrTo(read32(ptr));
            if (name.equals(key)) {
                byte[] bytes = ptrTo(read32(ptr + 4));
                return new ByteArrayInputStream(bytes);
            }
            ptr += 8;
        }
        return null;
    }

    private static final Object[] empty = new Object[0];

    @Alias(names = "java_lang_Class_getDeclaredFields_ALjava_lang_reflect_Field")
    public static <V> Object[] java_lang_Class_getDeclaredFields_ALjava_lang_reflect_Field(Class<V> clazz) {
        // should be ok, I think...
        // just nobody must modify our fields
        // todo should not return fields of super class
        if (clazz == null) throw new NullPointerException("getDeclaredFields");
        int fields = read32(getAddr(clazz) + objectOverhead + 4);
        if (fields == 0) return empty;
        return ptrTo(fields);
    }

    @Alias(names = "java_lang_reflect_Field_getDeclaredAnnotations_ALjava_lang_annotation_Annotation")
    public static <V> Object[] Field_getDeclaredAnnotations(Field field) {
        // todo implement annotation instances
        if (field == null) throw new NullPointerException("getDeclaredAnnotations");
        return empty;
    }

    @Alias(names = "java_lang_reflect_AccessibleObject_getDeclaredAnnotations_ALjava_lang_annotation_Annotation")
    public static <V> Object[] AccessibleObject_getDeclaredAnnotations(AccessibleObject object) {
        if (object == null) throw new NullPointerException("getDeclaredAnnotations2");
        return empty;
    }

    @Alias(names = "java_lang_Class_isAnonymousClass_Z")
    public static <V> boolean isAnonymousClass(Class<V> clazz) {
        return false;
    }

    @Alias(names = "kotlin_jvm_internal_ClassReference_getSimpleName_Ljava_lang_String")
    public static String ClassReference_getSimpleName(ClassReference c) {
        return c.getJClass().getSimpleName();
    }

    @Alias(names = "kotlin_reflect_jvm_internal_KClassImpl_getSimpleName_Ljava_lang_String")
    public static <V> String KClassImpl_getSimpleName(ClassBasedDeclarationContainer c) {
        return c.getJClass().getSimpleName();
    }

    @Alias(names = "kotlin_reflect_full_KClasses_getMemberFunctions_Lkotlin_reflect_KClassLjava_util_Collection")
    public static Collection<Object> KClasses_getMemberFunctions_Lkotlin_reflect_KClassLjava_util_Collection(ClassBasedDeclarationContainer clazz) {
        return Collections.emptyList();
    }

    @Alias(names = "java_lang_Class_getSimpleName_Ljava_lang_String")
    public static <V> String Class_getSimpleName(Class<V> c) {
        String name = c.getName();
        int i = name.lastIndexOf('.');
        if (i < 0) return name;
        else return name.substring(i + 1);
    }

    @Alias(names = "java_lang_Character_digit_III")
    public static int java_lang_Character_digit_III(int digit, int base) {
        // much more complicated in original JVM
        if (digit >= '0' & digit <= '9') {
            int number = digit - '0';
            if (number < base) return number;
        } else if (digit >= 'A' & digit <= 'z') {
            int number = (digit & 0x63) - 'A' + 10; // mask is for making the char lowercase
            if (number < base & number < 36) return number;
        }
        return -1;
    }

    @Alias(names = "java_lang_reflect_Field_equals_Ljava_lang_ObjectZ")
    public static boolean Field_equals(Field f, Object o) {
        return Field_equals2(f, o);
    }

    @NoThrow
    @WASM(code = "i32.eq")
    public static native boolean Field_equals2(Field f, Object o);

    @Alias(names = "java_lang_Class_newInstance_Ljava_lang_Object")
    public static <V> Object java_lang_Class_newInstance_Ljava_lang_Object(Class<V> clazz) {
        if (clazz == null) throwJs();
        int classIndex = getClassIndex(clazz);
        if (classIndex < 0) throwJs();
        int instance = create(classIndex);
        int constructorPtr = resolveIndirect(instance, 4); // (id+1)<<2, id = 0
        invoke(instance, constructorPtr);
        return ptrTo(instance);
    }

    @WASM(code = "call_indirect (type $fRV0)")
    public static native void invoke(int obj, int methodPtr);

    @Alias(names = "java_lang_Class_toString_Ljava_lang_String")
    public static <V> String java_lang_Class_toString_Ljava_lang_String(Class<V> clazz) {
        // we could have to implement isInterface() otherwise
        return clazz.getName();
    }

    @Alias(names = "java_lang_Class_getAnnotations_ALjava_lang_annotation_Annotation")
    public static <V> Annotation[] java_lang_Class_getAnnotations_ALjava_lang_annotation_Annotation(Class<V> clazz) {
        // todo implement this properly
        return new Annotation[0];
    }

    @Alias(names = "java_lang_Class_isInstance_Ljava_lang_ObjectZ")
    public static <V> boolean java_lang_Class_isInstance_Ljava_lang_ObjectZ(Class<V> clazz, Object instance) {
        if (instance == null) return false;
        if (instance.getClass() == clazz) return true;
        int classIndex = getClassIndex(clazz);
        if (classIndex >= 0) {
            return instanceOf(getAddr(instance), classIndex);
        }
        return false;
    }

    @Alias(names = "java_lang_Thread_start_V")
    public static <V> void java_lang_Thread_start_V(Thread thread) {
        log("Warning: starting threads is not yet supported!", thread.getName());
    }

    @NoThrow
    @Alias(names = "java_lang_Runtime_gc_V")
    @JavaScript(code = "gcCtr=1e9")
    public static native void java_lang_Runtime_gc_V(Runtime rt);

    @NoThrow
    @Alias(names = "java_lang_System_gc_V")
    @JavaScript(code = "gcCtr=1e9")
    public static native void java_lang_System_gc_V();

    @Alias(names = "java_lang_reflect_Array_newArray_Ljava_lang_ClassILjava_lang_Object")
    public static <V> Object java_lang_reflect_Array_newArray_Ljava_lang_ClassILjava_lang_Object(Class<V> clazz, int length) {
        // I don't think this might happen
        /*String name = clazz.getName();
        if (name == "int") return new int[length];
        else if (name == "float") return new float[length];
        else if (name == "double") return new double[length];*/
        return new Object[length];
    }

    private static byte sleepCtr = 0;
    private static long lastSleepTime;

    @Alias(names = "java_lang_Thread_sleep_JV")
    public static void java_lang_Thread_sleep_JV(long delay) {
        if (delay > 0) {
            throwJs("Cannot sleep in web!");
        } else {
            if (sleepCtr++ > 100) {
                long time = System.currentTimeMillis();
                if (time == lastSleepTime) throwJs("Active waiting is not possible in web!");
                lastSleepTime = time;
                sleepCtr = 0;
            }
        }
    }

    @NoThrow
    @Alias(names = "new_java_lang_ClassLoader_Ljava_lang_VoidLjava_lang_ClassLoaderV")
    public static void new_java_lang_ClassLoader_Ljava_lang_VoidLjava_lang_ClassLoaderV(Object self, Object voidI, Object cl) {
        // idk what we should do here, probably nothing :)
        // might prevent HashTable from being used
    }

    @Alias(names = "java_lang_ProcessBuilder_start_Ljava_lang_Process")
    public static Process java_lang_ProcessBuilder_start_Ljava_lang_Process(ProcessBuilder self) {
        throw new RuntimeException("Starting processes is not possible in WASM");
    }

    @Alias(names = "java_lang_Runtime_exec_ALjava_lang_StringLjava_lang_Process")
    public static Process java_lang_Runtime_exec_ALjava_lang_StringLjava_lang_Process(Runtime rt, String[] args) {
        throw new RuntimeException("Starting processes is not possible in WASM");
    }

    @Alias(names = "java_lang_Runtime_exec_ALjava_lang_StringALjava_lang_StringLjava_io_FileLjava_lang_Process")
    public static Process java_lang_Runtime_exec_ALjava_lang_StringALjava_lang_StringLjava_io_FileLjava_lang_Process(Runtime rt, String[] args, String[] args2, File file) {
        throw new RuntimeException("Starting processes is not possible in WASM");
    }

    @Alias(names = "java_lang_Shutdown_exit_IV")
    public static void java_lang_Shutdown_exit_IV(int exitCode) {
        throw new Error("Cannot shutdown website");
    }

    @NoThrow // idc
    @Alias(names = "java_lang_Thread_setContextClassLoader_Ljava_lang_ClassLoaderV")
    public static void java_lang_Thread_setContextClassLoader_Ljava_lang_ClassLoaderV(Thread th, ClassLoader cl) {
    }

    @NoThrow // idc
    @Alias(names = "java_lang_System_checkIO_V")
    public static void java_lang_System_checkIO_V() {
    }

    @NoThrow
    @Alias(names = "java_lang_Thread_join_JV")
    public static void java_lang_Thread_join_JV(Thread self, long timeoutMillis) {
        // not supported, as threads cannot run
    }

    @Alias(names = "java_lang_reflect_AccessibleObject_checkAccess_Ljava_lang_ClassLjava_lang_ClassLjava_lang_ObjectIV")
    public static <A, B> void java_lang_reflect_AccessibleObject_checkAccess_Ljava_lang_ClassLjava_lang_ClassLjava_lang_ObjectIV(Object self, Class<A> clazz, Class<B> clazz2, Object obj, int x) {
    }

    @Alias(names = "java_lang_Class_reflectionData_Ljava_lang_ClassXReflectionData")
    public static Object java_lang_Class_reflectionData_Ljava_lang_ClassXReflectionData(Object self) {
        throw new RuntimeException("Cannot ask for reflection data, won't work");
    }

    @Alias(names = "java_lang_Class_privateGetPublicMethods_ALjava_lang_reflect_Method")
    public static Object[] java_lang_Class_privateGetPublicMethods_ALjava_lang_reflect_Method(Object self) {
        return empty;// todo
    }

    @Alias(names = "java_lang_Class_privateGetDeclaredMethods_ZALjava_lang_reflect_Method")
    public static Object[] java_lang_Class_privateGetDeclaredMethods_ZALjava_lang_reflect_Method(Object self, boolean sth) {
        return empty;// todo
    }

    private static Constructor<Object>[] constructors;

    @Alias(names = "java_lang_Class_getConstructor_ALjava_lang_ClassLjava_lang_reflect_Constructor")
    public static <V> Constructor<V> getConstructor(Class<V> self, Object[] args) throws NoSuchFieldException, IllegalAccessException {
        if (args == null) {
            throwJs("Arguments was null?");
            return null;
        }
        if (args.length > 0) {
            throwJs("Cannot access constructors with arguments");
            return null;
        }
        if (constructors == null) {
            constructors = new Constructor[numClasses()];
        }
        int idx = getClassIndex(self);
        Constructor<Object> cs = constructors[idx];
        if (cs == null) {
            cs = ptrTo(create(getClassIndex(Constructor.class)));
            Constructor.class.getDeclaredField("clazz").set(cs, self);
            constructors[idx] = cs;
        }
        return (Constructor<V>) cs;
    }

    @Alias(names = "java_lang_reflect_Constructor_equals_Ljava_lang_ObjectZ")
    public static boolean java_lang_reflect_Constructor_equals_Ljava_lang_ObjectZ(Object self, Object other) {
        return self == other;
    }

    @Alias(names = "java_lang_reflect_Constructor_toString_Ljava_lang_String")
    public static String java_lang_reflect_Constructor_toString_Ljava_lang_String(Object self) {
        return self.getClass().getName();
    }

    @Alias(names = "java_lang_reflect_Constructor_getDeclaredAnnotations_ALjava_lang_annotation_Annotation")
    public static Object java_lang_reflect_Constructor_getDeclaredAnnotations_ALjava_lang_annotation_Annotation(Object self) {
        return null;
    }

    @Alias(names = "java_lang_reflect_Constructor_newInstance_ALjava_lang_ObjectLjava_lang_Object")
    public static <V> V java_lang_reflect_Constructor_newInstance_ALjava_lang_ObjectLjava_lang_Object(Constructor<V> self, Object[] args) throws InstantiationException, IllegalAccessException {
        if (args != null && args.length != 0)
            throw new IllegalArgumentException("Constructors with arguments aren't yet supported in WASM");
        return self.getDeclaringClass().newInstance();
    }

    @Alias(names = "java_lang_Class_getInterfaces_ALjava_lang_Class")
    public static Object[] java_lang_Class_getInterfaces_ALjava_lang_Class(Object self) {
        return empty;// todo
    }

    @Alias(names = "static_java_lang_ClassLoaderXParallelLoaders_V")
    public static void static_java_lang_ClassLoaderXParallelLoaders_V() {
    }

    @Alias(names = "java_lang_ClassLoaderXParallelLoaders_register_Ljava_lang_ClassZ")
    public static boolean java_lang_ClassLoaderXParallelLoaders_register_Ljava_lang_ClassZ(Object clazz) {
        // idc
        return false;
    }

    @Alias(names = "java_lang_Class_getAnnotation_Ljava_lang_ClassLjava_lang_annotation_Annotation")
    public static Annotation java_lang_Class_getAnnotation_Ljava_lang_ClassLjava_lang_annotation_Annotation(Object self, Object annotClass) {
        // todo implement properly
        return null;
    }

    @Alias(names = "java_lang_ClassLoader_loadLibrary0_Ljava_lang_ClassLjava_io_FileZ")
    public static boolean java_lang_ClassLoader_loadLibrary0_Ljava_lang_ClassLjava_io_FileZ(Object clazz, File file) {
        return false;
    }

    @Alias(names = "java_lang_ClassLoaderXNativeLibrary_finalize_V")
    public static void java_lang_ClassLoaderXNativeLibrary_finalize_V(Object self) {
    }

    @Alias(names = "java_lang_ClassLoader_loadLibrary_Ljava_lang_ClassLjava_lang_StringZV")
    public static void java_lang_ClassLoader_loadLibrary_Ljava_lang_ClassLjava_lang_StringZV(Object clazz, String file, boolean sth) {
    }

    @Alias(names = "java_lang_reflect_Executable_getParameters_ALjava_lang_reflect_Parameter")
    public static Object[] Executable_getParameters(Object self) {
        return empty;// todo implement for panels...
    }

}
