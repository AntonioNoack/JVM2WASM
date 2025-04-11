package jvm;

import annotations.Alias;
import annotations.JavaScript;
import annotations.NoThrow;
import annotations.WASM;
import jvm.custom.ThreadLocalRandom;
import jvm.gc.GarbageCollector;
import jvm.lang.Bean;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.management.RuntimeMXBean;
import java.security.AccessControlContext;
import java.util.Random;

import static jvm.ArrayAccessSafe.arrayLength;
import static jvm.JVM32.*;
import static jvm.JVMShared.*;
import static jvm.NativeLog.log;
import static jvm.Pointer.unsignedLessThan;
import static jvm.Pointer.*;
import static jvm.ThrowJS.throwJs;

@SuppressWarnings("unused")
public class JavaLang {

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_floor_DD", "java_lang_Math_floor_DD"})
    @WASM(code = "f64.floor")
    public static native double StrictMath_floor_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_ceil_DD", "java_lang_Math_ceil_DD"})
    @WASM(code = "f64.ceil")
    public static native double StrictMath_ceil_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_rint_DD", "java_lang_Math_rint_DD"})
    @WASM(code = "f64.nearest")
    public static native double StrictMath_rint_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_sin_DD", "java_lang_Math_sin_DD"})
    @JavaScript(code = "return Math.sin(arg0);")
    public static native double StrictMath_sin_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_cos_DD", "java_lang_Math_cos_DD"})
    @JavaScript(code = "return Math.cos(arg0);")
    public static native double StrictMath_cos_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_tan_DD", "java_lang_Math_tan_DD"})
    @JavaScript(code = "return Math.tan(arg0);")
    public static native double StrictMath_tan_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_asin_DD", "java_lang_Math_asin_DD"})
    @JavaScript(code = "return Math.asin(arg0);")
    public static native double StrictMath_asin_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_acos_DD", "java_lang_Math_acos_DD"})
    @JavaScript(code = "return Math.acos(arg0);")
    public static native double StrictMath_acos_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_atan_DD", "java_lang_Math_atan_DD"})
    @JavaScript(code = "return Math.atan(arg0);")
    public static native double StrictMath_atan_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_sinh_DD", "java_lang_Math_sinh_DD"})
    @JavaScript(code = "return Math.sinh(arg0);")
    public static native double StrictMath_sinh_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_cosh_DD", "java_lang_Math_cosh_DD"})
    @JavaScript(code = "return Math.cosh(arg0);")
    public static native double StrictMath_cosh_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_tanh_DD", "java_lang_Math_tanh_DD"})
    @JavaScript(code = "return Math.tanh(arg0);")
    public static native double StrictMath_tanh_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_asinh_DD", "java_lang_Math_asinh_DD"})
    @JavaScript(code = "return Math.asinh(arg0);")
    public static native double StrictMath_asinh_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_acosh_DD", "java_lang_Math_acosh_DD"})
    @JavaScript(code = "return Math.acosh(arg0);")
    public static native double StrictMath_acosh_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_atanh_DD", "java_lang_Math_atanh_DD"})
    @JavaScript(code = "return Math.atanh(arg0);")
    public static native double StrictMath_atanh_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_exp_DD", "java_lang_Math_exp_DD"})
    @JavaScript(code = "return Math.exp(arg0);")
    public static native double StrictMath_exp_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_log_DD", "java_lang_Math_log_DD"})
    @JavaScript(code = "return Math.log(arg0);")
    public static native double StrictMath_log_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_log10_DD", "java_lang_Math_log10_DD"})
    @JavaScript(code = "return Math.log10(arg0);")
    public static native double StrictMath_log10_DD(double x);

    @NoThrow
    @Alias(names = {"java_lang_StrictMath_sqrt_DD", "java_lang_Math_sqrt_DD"})
    @WASM(code = "f64.sqrt")
    public static native double StrictMath_sqrt_DD(double x);

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
    public static native float Float_intBitsToFloat(int bits);

    @NoThrow
    @Alias(names = "java_lang_Double_longBitsToDouble_JD")
    @WASM(code = "f64.reinterpret_i64")
    public static native double Double_longBitsToDouble_JD(long bits);

    @NoThrow
    @Alias(names = "java_lang_Math_min_FFF")
    @WASM(code = "f32.min")
    public static native float Math_min(float a, float b);

    @NoThrow
    @Alias(names = "java_lang_Math_max_FFF")
    @WASM(code = "f32.max")
    public static native float Math_max(float a, float b);

    @NoThrow
    @Alias(names = "java_lang_Math_min_DDD")
    @WASM(code = "f64.min")
    public static native float Math_min(double a, double b);

    @NoThrow
    @Alias(names = "java_lang_Math_max_DDD")
    @WASM(code = "f64.max")
    public static native float Math_max(double a, double b);

    @NoThrow
    @Alias(names = "java_lang_Math_abs_FF")
    @WASM(code = "f32.abs")
    public static native float Math_abs_FF(float a);

    @NoThrow
    @Alias(names = "java_lang_Math_abs_DD")
    @WASM(code = "f64.abs")
    public static native double Math_abs_DD(double a);

    // original implementation, just no throw -> isn't that incorrect for Infinity??
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

        int classIdSrc = readClassId(src), classIdDst = readClassId(dst);
        if (classIdSrc != classIdDst) {
            log("Mismatched classes:", classIdSrc, classIdDst);
            throw new RuntimeException("Mismatched types");
        }

        int delta = arrayOverhead;
        int shift = getTypeShift(classIdSrc);// shall be executed here to confirm that this is arrays
        Pointer src1 = castToPtr(src), dst1 = castToPtr(dst);
        Pointer src2 = add(src1, delta + ((long) srcIndex << shift));
        Pointer dst2 = add(dst1, delta + ((long) dstIndex << shift));

        checkIndices(srcIndex, length, arrayLength(src1));
        checkIndices(dstIndex, length, arrayLength(dst1));

        long numBytes = (long) length << shift;
        if (src != dst || srcIndex > dstIndex) copyForwards(src2, dst2, numBytes);
        else copyBackwards(src2, dst2, numBytes);
    }

    @NoThrow
    public static void copyForwards(Pointer src, Pointer dst, long length) {
        Pointer endPtr = add(dst, length);
        Pointer end8 = sub(endPtr, 7);
        while (unsignedLessThan(dst, end8)) {
            write64(dst, read64(src));
            dst = add(dst, 8);
            src = add(src, 8);
        }
        if ((length & 4) != 0) {
            write32(dst, read32(src));
            dst = add(dst, 4);
            src = add(src, 4);
        }
        if ((length & 2) != 0) {
            write16(dst, read16u(src));
            dst = add(dst, 2);
            src = add(src, 2);
        }
        if ((length & 1) != 0) {
            write8(dst, read8(src));
        }
    }

    @NoThrow
    public static void copyBackwards(Pointer src, Pointer dst, long length) {
        Pointer endPtr = sub(dst, 1);
        // we start at the end
        src = add(src, length - 8);
        dst = add(dst, length - 8);
        while (unsignedLessThan(endPtr, dst)) {
            write64(dst, read64(src));
            dst = sub(dst, 8);
            src = sub(src, 8);
        }
        // looks correct :)
        if ((length & 4) != 0) {
            write32(add(dst, 4), read32(add(src, 4)));
        } else {
            dst = sub(dst, 4);
            src = sub(src, 4);
        }
        if ((length & 2) != 0) {
            write16(add(dst, 2), read16u(add(src, 2)));
        } else {
            dst = sub(dst, 2);
            src = sub(src, 2);
        }
        if ((length & 1) != 0) {
            write8(add(dst, 1), read8(add(src, 1)));
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

    @NoThrow
    @WASM(code = "global.get $stackTraceTable")
    public static native Pointer getStackTraceTablePtr();

    @NoThrow
    public static void resetStackPtr() {
        // must be called after every wasm-native error like segfaults
        setStackPtr(getStackStart());
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
    @Alias(names = {"java_lang_System_registerNatives_V", "java_lang_Object_registerNatives_V"})
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
    @Alias(names = "java_lang_Thread_interrupt_V")
    public static void Thread_interrupt_V(Thread self) {
    }

    @NoThrow
    @Alias(names = "java_lang_Thread_checkAccess_V")
    public static void Thread_checkAccess_V(Thread self) {
    }

    @NoThrow
    @Alias(names = "java_lang_Thread_setNativeName_Ljava_lang_StringV")
    public static void Thread_setNativeName_Ljava_lang_StringV(Thread self, String name) {
    }

    @NoThrow
    @Alias(names = "java_lang_Thread_holdsLock_Ljava_lang_ObjectZ")
    public static boolean Thread_holdsLock_Ljava_lang_ObjectZ(Object lock) {
        // static function, why ever
        return true;
    }

    @NoThrow
    @Alias(names = "java_lang_Thread_isAlive_Z")
    public static boolean Thread_isAlive_Z(Thread self) {
        return self == MAIN_THREAD;
    }

    private static final Thread MAIN_THREAD = new Thread("main");

    @Alias(names = "java_lang_Thread_currentThread_Ljava_lang_Thread")
    public static Thread Thread_currentThread() {
        return MAIN_THREAD;
    }

    @NoThrow
    @JavaScript(code = "commandLine[arg1].push(String.fromCharCode(arg0))")
    public static native void printByte(int i, boolean justLog);

    @NoThrow
    @JavaScript(code = "let c=commandLine[arg0];if(c.length>0){if(!arg0)ec++;(arg0?console.log:console.error)(c.join('')); commandLine[arg0] = []}")
    public static native void printFlush(boolean justLog);

    @NoThrow
    @JavaScript(code = "let strI = str(arg0); (arg1?console.log:console.error)(strI);")
    public static native void printString(String line, boolean justLog);

    @Alias(names = "AW_clone_Ljava_lang_Object")
    public static Object Array_clone(Object[] base) {
        int length = base.length;
        Object[] clone = new Object[length];
        System.arraycopy(base, 0, clone, 0, length);
        return clone;
    }

    @Alias(names = "java_lang_System_setOut_Ljava_io_PrintStreamV")
    public static void System_setOut(PrintStream v) throws NoSuchFieldException, IllegalAccessException {
        System.class.getField("out").set(null, v);
    }

    @Alias(names = "java_lang_System_setErr_Ljava_io_PrintStreamV")
    public static void System_setErr(PrintStream v) throws NoSuchFieldException, IllegalAccessException {
        System.class.getField("err").set(null, v);
    }

    @Alias(names = "java_lang_Thread_init_Ljava_lang_ThreadGroupLjava_lang_RunnableLjava_lang_StringJLjava_security_AccessControlContextZV")
    public static void Thread_init(
            Thread self, ThreadGroup threadGroup, Runnable runnable, String name,
            long stackSize, AccessControlContext ctx, boolean inheritThreadLocals) {

    }

    @Alias(names = "java_lang_Thread_interrupt0_V")
    public static void Thread_interrupt0(Thread th) {
    }

    @Alias(names = "java_lang_System_getProperty_Ljava_lang_StringLjava_lang_String")
    public static String System_getProperty(String key) {
        if (key == null) return null;
        switch (key) {
            case "os.name":
                return "Linux Web";
            case "user.home":
                return "/user/virtual";
        }
        return null;
    }

    @NoThrow
    @Alias(names = "java_lang_System_lineSeparator_Ljava_lang_String")
    public static String System_lineSeparator() {
        return "\n";
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
        return getAddrS(getAllocatedSize());
    }

    @Alias(names = "java_lang_Runtime_freeMemory_J")
    public static long java_lang_Runtime_freeMemory_J(Runtime runtime) {
        return GarbageCollector.freeMemory + diff(getAllocatedSize(), getNextPtr());
    }

    @Alias(names = "java_lang_Object_clone_Ljava_lang_Object")
    public static Object java_lang_Object_clone_Ljava_lang_Object(Object self)
            throws CloneNotSupportedException, InstantiationException, IllegalAccessException {
        if (self instanceof Cloneable) {
            // todo copy all properties...
            return self.getClass().newInstance();
        } else throw new CloneNotSupportedException();
    }

    private static RuntimeMXBean bean;

    @Alias(names = "java_lang_management_ManagementFactory_getRuntimeMXBean_Ljava_lang_management_RuntimeMXBean")
    public static Object java_lang_management_ManagementFactory_getRuntimeMXBean_Ljava_lang_management_RuntimeMXBean() {
        if (bean == null) bean = new Bean();
        return bean;
    }

    // todo we're using byte-strings for a while now, add a byte-variant, too

    @NoThrow
    @JavaScript(code = "let s=arg1+'';if(s.indexOf('.')<0)s+='.0';return fill(arg0,s)")
    public static native int fillD2S(char[] chr, double v);

    @Alias(names = "java_lang_Double_toString_DLjava_lang_String")
    public static synchronized String Double_toString(double d) {
        char[] builder = FillBuffer.getBuffer();
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
        int x = Long.hashCode(getAddrS(obj));
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = (x >>> 16) ^ x;
        return x;
    }

    @Alias(names = "java_lang_System_getenv_Ljava_lang_StringLjava_lang_String")
    public static String System_getenv(String key) {
        return null;
    }

    @Alias(names = "java_lang_Character_digit_CII")
    public static int java_lang_Character_digit_CII(char digit, int base) {
        return java_lang_Character_digit_III(digit, base);
    }

    @Alias(names = "java_lang_Character_digit_III")
    public static int java_lang_Character_digit_III(int digit, int base) {
        // much more complicated in original JVM
        if (digit >= '0' & digit <= '9') {
            int number = digit - '0';
            if (number < base) return number;
        } else if (digit >= 'A' & digit <= 'Z') {
            int number = digit - 'A' + 10;
            if (number < base) return number;
        } else if (digit >= 'a' & digit <= 'z') {
            int number = digit - 'a' + 10;
            if (number < base) return number;
        }
        return -1;
    }

    @SuppressWarnings("CallToThreadRun")
    @Alias(names = "java_lang_Thread_start_V")
    public static <V> void Thread_start_V(Thread thread) {
        log("Warning: starting threads is not yet supported!", thread.getName());
        thread.run();
    }

    @NoThrow
    @Alias(names = "java_lang_Runtime_gc_V")
    @JavaScript(code = "gcCtr=1e9")
    public static native void Runtime_gc_V(Runtime rt);

    @NoThrow
    @Alias(names = "java_lang_System_gc_V")
    @JavaScript(code = "gcCtr=1e9")
    public static native void System_gc_V();

    private static byte sleepCtr = 0;
    private static long lastSleepTime;

    @Alias(names = "java_lang_Thread_sleep_JV")
    public static void Thread_sleep_JV(long delay) {
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
    public static Process ProcessBuilder_start_Ljava_lang_Process(ProcessBuilder self) {
        throw new RuntimeException("Starting processes is not possible in WASM");
    }

    @Alias(names = "java_lang_Runtime_exec_AWLjava_lang_Process")
    public static Process Runtime_exec_AWLjava_lang_Process(Runtime rt, String[] args) {
        throw new RuntimeException("Starting processes is not possible in WASM");
    }

    @Alias(names = "java_lang_Runtime_exec_AWAWLjava_io_FileLjava_lang_Process")
    public static Process Runtime_exec_AWAWLjava_io_FileLjava_lang_Process(Runtime rt, String[] args, String[] args2, File file) {
        throw new RuntimeException("Starting processes is not possible in WASM");
    }

    @Alias(names = "java_lang_Shutdown_exit_IV")
    public static void Shutdown_exit_IV(int exitCode) {
        throw new Error("Cannot shutdown website");
    }

    @NoThrow // idc
    @Alias(names = "java_lang_Thread_setContextClassLoader_Ljava_lang_ClassLoaderV")
    public static void Thread_setContextClassLoader_Ljava_lang_ClassLoaderV(Thread th, ClassLoader cl) {
    }

    @NoThrow // idc
    @Alias(names = "java_lang_System_checkIO_V")
    public static void System_checkIO_V() {
    }

    @NoThrow
    @Alias(names = "java_lang_Thread_join_JV")
    public static void Thread_join_JV(Thread self, long timeoutMillis) {
        // not supported, as threads cannot run
    }

    @Alias(names = "java_lang_Double_parseDouble_Ljava_lang_StringD")
    public static double parseDouble(String src) {
        if (src == null || src.isEmpty()) {
            throw new NumberFormatException("Cannot parse empty as double");
        }
        // parse NaN, Infinity
        switch (src) {
            case "NaN":
                return Double.NaN;
            case "Infinity":
            case "+Infinity":
                return Double.POSITIVE_INFINITY;
            case "-Infinity":
                return Double.NEGATIVE_INFINITY;
        }
        char c0 = src.charAt(0);
        int i0 = c0 == '+' || c0 == '-' ? 1 : 0;
        return parseDoubleWhole(src, i0);
    }

    private static double parseDoubleWhole(@NotNull String src, int i0) {
        double whole = 0.0;
        double digits = 0.0;
        int exponent = 0;
        for (int i = i0, l = src.length(); i < l; i++) {
            char c = src.charAt(i);
            if (c >= '0' && c <= '9') {
                whole = 10.0 * whole + (c - '0');
            } else if (c == '.') {
                // read digits next
                return parseDoubleDigits(src, i + 1, whole);
            } else if (c == 'e' || c == 'E') {
                return parseDoubleExponent(src, i + 1, whole);
            } else {
                throw new NumberFormatException("Unexpected character in number");
            }
        }
        if (src.charAt(0) == '-') whole = -whole;
        return whole;
    }

    private static double parseDoubleDigits(@NotNull String src, int i0, double whole) {
        double digits = 0.0;
        double exponent = 0.1;
        for (int i = i0, l = src.length(); i < l; i++) {
            char c = src.charAt(i);
            if (c >= '0' && c <= '9') {
                digits += (c - '0') * exponent;
                exponent *= 0.1;
            } else if (c == 'e' || c == 'E') {
                return parseDoubleExponent(src, i + 1, whole + digits);
            } else {
                throw new NumberFormatException("Unexpected character in digits");
            }
        }
        return whole;
    }

    private static double parseDoubleExponent(@NotNull String src, int i0, double number) {
        int exponent = 0;
        if (i0 == src.length()) {
            throw new NumberFormatException("Empty exponent");
        }
        int i = i0;
        char c0 = src.charAt(i0);
        if (c0 == '+' || c0 == '-') {
            // sign for exponent
            i++;
        }
        if (i == src.length()) {
            throw new NumberFormatException("Empty exponent");
        }
        for (int l = src.length(); i < l; i++) {
            char c = src.charAt(i);
            if (c >= '0' && c <= '9') {
                exponent = exponent * 10 + (c - '0');
            } else {
                throw new NumberFormatException("Unexpected character in exponent");
            }
        }
        if (c0 == '-') exponent = -exponent;
        double whole = number * Math.pow(10.0, exponent);
        if (src.charAt(0) == '-') whole = -whole;
        return whole;
    }

    @Alias(names = "java_lang_Float_parseFloat_Ljava_lang_StringF")
    public static float Float_parseFloat(String src) {
        return (float) parseDouble(src);
    }

    @NoThrow
    @Alias(names = "kotlin_random_jdk8_PlatformThreadLocalRandom_getImpl_Ljava_util_Random")
    public static Random PlatformThreadLocalRandom_getImplRandom(Object self) {
        return ThreadLocalRandom.INSTANCE;
    }

    @NoThrow
    @Alias(names = "kotlin_random_jdk8_PlatformThreadLocalRandom_nextInt_III")
    public static int PlatformThreadLocalRandom_nextInt_III(Object self, int min, int maxExcl) {
        return PlatformThreadLocalRandom_getImplRandom(null)
                .nextInt(maxExcl - min) + min;
    }

    @Alias(names = "static_kotlin_internal_jdk8_JDK8PlatformImplementationsXReflectSdkVersion_V")
    public static void JDK8PlatformImplementationsXReflectSdkVersion_V() {
        // uses reflection with throwables -> nah, we don't want that
    }

    @NotNull
    public static String Object_toString(@NotNull Object self) {
        return self.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(self));
    }

    @Alias(names = "java_lang_Object_notifyAll_V")
    public static void Object_notifyAll_V(Object self) {
        // idk...
    }

    @Alias(names = "java_lang_Object_wait_V")
    public static void Object_wait_V(Object self) {
    }

    @Alias(names = "java_lang_Object_wait_JV")
    public static void Object_wait_JV(Object self, long timeout) {
    }

}
