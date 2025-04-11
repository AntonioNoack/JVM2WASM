package jvm;

import annotations.*;
import jvm.lang.JavaLangAccessImpl;
import org.jetbrains.annotations.Nullable;
import sun.misc.SharedSecrets;

import java.io.PrintStream;

import static jvm.ArrayAccessSafe.arrayStore;
import static jvm.JVM32.*;
import static jvm.JVMFlags.ptrSizeBits;
import static jvm.JVMValues.emptyArray;
import static jvm.JavaLang.getStackTraceTablePtr;
import static jvm.NativeLog.log;
import static jvm.Pointer.*;
import static jvm.ThrowJS.throwJs;
import static utils.StaticClassIndices.*;

/**
 * Things that are shared between JVM32 and JVM64: stuff, that is independent of pointerSize/is32Bits/ptrType
 */
public class JVMShared {

    public static final int objectOverhead = 4;// 3x class, 1x GC
    public static final int arrayOverhead = objectOverhead + 4;// length

    public static final int intSize = 4;
    public static final int longSize = 8;
    public static boolean trackAllocations = true;

    @NoThrow
    @WASM(code = "global.get $inheritanceTable")
    public static native Pointer inheritanceTable();

    @NoThrow
    @WASM(code = "global.get $staticTable")
    public static native Pointer staticInstancesOffset();

    @NoThrow
    @Alias(names = "findStatic")
    public static int findStatic(int classId, int offset) {
        return read32(add(staticInstancesOffset(), classId << 2)) + offset;
    }

    @NoThrow
    @JavaScript(code = "calloc[arg0] = (calloc[arg0]||0)+1")
    public static native void trackCalloc(int classId);

    @NoThrow
    @JavaScript(code = "calloc[arg0] = (calloc[arg0]||0)+1")
    public static native void trackCalloc(int classId, int arrayLength);

    @NoThrow
    @WASM(code = "i32.lt_u")
    public static native boolean unsignedLessThan(int a, int b);

    @NoThrow
    @WASM(code = "i32.le_u")
    public static native boolean unsignedLessThanEqual(int a, int b);

    @NoThrow
    @WASM(code = "i32.ge_u")
    public static native boolean unsignedGreaterThanEqual(int a, int b);

    @NoThrow
    @WASM(code = "i64.ne")
    public static native boolean neq(long a, long b);

    @NoThrow
    @WASM(code = "i32.and")
    public static native boolean and(boolean a, int b);

    @NoThrow
    @WASM(code = "i32.and")
    public static native boolean and(boolean a, boolean b);

    @NoThrow
    @Alias(names = "f2i")
    public static int f2i(float v) {
        if (v < -2147483648f) return Integer.MIN_VALUE;
        if (v > 2147483647f) return Integer.MAX_VALUE;
        if (Float.isNaN(v)) return 0;
        return f2iNative(v);
    }

    @NoThrow
    @WASM(code = "i32.trunc_f32_s")
    public static native int f2iNative(float v);

    @NoThrow
    @Alias(names = "f2l")
    public static long f2l(float v) {
        if (v < -9223372036854775808f) return Long.MIN_VALUE;
        if (v > 9223372036854775807f) return Long.MAX_VALUE;
        if (Float.isNaN(v)) return 0L;
        return f2lNative(v);
    }

    @NoThrow
    @WASM(code = "i64.trunc_f32_s")
    public static native long f2lNative(float v);

    @NoThrow
    @Alias(names = "d2i")
    public static int d2i(double v) {
        if (v < -2147483648.0) return Integer.MIN_VALUE;
        if (v > 2147483647.0) return Integer.MAX_VALUE;
        if (Double.isNaN(v)) return 0;
        return d2iNative(v);
    }

    @NoThrow
    @WASM(code = "i32.trunc_f64_s")
    public static native int d2iNative(double v);

    @NoThrow
    @Alias(names = "d2l")
    public static long d2l(double v) {
        if (v < -9223372036854775808.0) return Long.MIN_VALUE;
        if (v > 9223372036854775807.0) return Long.MAX_VALUE;
        if (Double.isNaN(v)) return 0L;
        return _d2l(v);
    }

    @NoThrow
    @WASM(code = "i64.trunc_f64_s")
    public static native long _d2l(double v);

    @Export
    @NoThrow
    @UsedIfIndexed
    @Alias(names = "oo")
    public static int getObjectOverhead() {
        return objectOverhead;
    }

    @NoThrow
    @WASM(code = "global.get $classSize")
    public static native int getClassSize();

    @NoThrow
    @WASM(code = "global.get $classInstanceTable")
    public static native Pointer getClassInstanceTable();

    static void failCastCheck(Object instance, int classId) {
        Class<Object> isClass = classIdToInstance(readClassId(instance));
        Class<Object> checkClass = classIdToInstance(classId);
        log(isClass.getName(), "is not instance of", checkClass.getName(), castToPtr(instance));
        throw new ClassCastException();
    }

    @NoThrow
    @Alias(names = "stackPop")
    public static void stackPop() {
        setStackPtr(add(getStackPtr(), 4));
    }

    @NoThrow
    @Alias(names = "getStackDepth")
    public static int getStackDepth() {
        return getStackDepth(getStackPtr());
    }

    @NoThrow
    public static int getStackDepth(Pointer stackPointer) {
        return (int) (diff(getStackStart(), stackPointer) >> 2);
    }

    @NoThrow
    @WASM(code = "global.get $stackPointer")
    public static native Pointer getStackPtr();

    @NoThrow
    @WASM(code = "global.get $stackPointerStart")
    public static native Pointer getStackStart();

    @NoThrow
    @WASM(code = "global.get $stackEndPointer")
    public static native Pointer getStackLimit();

    @NoThrow
    @WASM(code = "global.set $stackPointer")
    public static native void setStackPtr(Pointer addr);

    private static boolean canWarnStackOverflow = true;

    @NoThrow
    @Alias(names = "stackPush")
    public static void stackPush(int idx) {
        if (false) printStackTraceLine(idx);
        Pointer stackPointer = sub(getStackPtr(), 4);
        Pointer limit = getStackLimit();
        if (Pointer.unsignedGreaterThanEqual(stackPointer, limit)) {
            write32(stackPointer, idx);
        } else if (stackPointer == sub(limit, 4)) {
            // stack overflow
            // we can do different things here -> let's just keep running;
            // just the stack is no longer tracked :)
            if (canWarnStackOverflow) {
                canWarnStackOverflow = false;
                log("Warning: Exited stack space, meaning",
                        (int) (diff(getStackStart(), getStackLimit()) >> 2), " recursive calls");
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
        getAddrS(System.out);
        // could be used to initialize classes or io
        System.setOut(new PrintStream(new JavaLang.JSOutputStream(true)));
        System.setErr(new PrintStream(new JavaLang.JSOutputStream(false)));
        SharedSecrets.setJavaLangAccess(new JavaLangAccessImpl());
    }

    /**
     * method contents will be replaced programmatically,
     * if the callStaticInitOnce flag is set
     */
    private static void staticInit() {
        /* !!!DO NOT PLACE CODE HERE!!! */
    }

    @Alias(names = "throwAME")
    public static void throwAbstractMethodError() {
        throw new AbstractMethodError();
    }

    @NoThrow
    @WASM(code = "i64.store")
    public static native void write64(Pointer addr, long value);

    @NoThrow
    @WASM(code = "f64.store")
    public static native void write64(Pointer addr, double value);

    @NoThrow
    @WASM(code = "i32.store")
    public static native void write32(Pointer addr, int value);

    @NoThrow
    @WASM(code = "f32.store")
    public static native void write32(Pointer addr, float value);

    @NoThrow
    @WASM(code = "i32.store16")
    public static native void write16(Pointer addr, short value);

    @NoThrow
    @WASM(code = "i32.store16")
    public static native void write16(Pointer addr, char value);

    @NoThrow
    @WASM(code = "i32.store8")
    public static native void write8(Pointer addr, byte value);

    @NoThrow
    @WASM(code = "i64.load")
    public static native long read64(Pointer addr);

    @NoThrow
    @WASM(code = "i32.load")
    public static native int read32(Pointer addr);

    public static Pointer read32Ptr(Pointer addr) {
        return ptrTo(read32(addr));
    }

    @NoThrow
    @WASM(code = "f64.load")
    public static native double read64f(Pointer addr);

    @NoThrow
    @WASM(code = "f32.load")
    public static native float read32f(Pointer addr);

    @NoThrow
    @WASM(code = "i32.load16_s")
    public static native short read16s(Pointer addr);

    @NoThrow
    @WASM(code = "i32.load16_u")
    public static native char read16u(Pointer addr);

    @NoThrow
    @WASM(code = "i32.load8_s")
    public static native byte read8(Pointer addr);

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
    public static native Pointer getStaticInitTable();

    @Alias(names = "wasStaticInited")
    public static boolean wasStaticInited(int index) {
        validateClassId(index);
        Pointer address = add(getStaticInitTable(), index);
        boolean answer = read8(address) != 0;
        write8(address, (byte) 1);
        return answer;
    }

    @NoThrow
    @WASM(code = "i32.ge_u")
    static native boolean ge_ub(int a, int b);

    @NoThrow
    public static int getTypeShiftUnsafe(int classId) {
        if (classId == OBJECT_ARRAY) return ptrSizeBits;
        if (classId == INT_ARRAY || classId == FLOAT_ARRAY) return 2;
        if (classId == LONG_ARRAY || classId == DOUBLE_ARRAY) return 3;
        if (classId == SHORT_ARRAY || classId == CHAR_ARRAY) return 1;
        return 0; // byte, boolean
    }

    public static boolean isArrayClassId(int classId) {
        return classId >= FIRST_ARRAY && classId <= LAST_ARRAY;
    }

    public static int getTypeShift(int classId) {
        if (!isArrayClassId(classId)) throw new IllegalArgumentException();
        return getTypeShiftUnsafe(classId);
    }

    @NoThrow
    @WASM(code = "") // automatically done
    public static native int b2i(boolean flag);

    @NoThrow
    public static int getSuperClassId(int classId) {
        Pointer tableAddress = getInheritanceTableEntry(classId);
        return getSuperClassIdFromInheritanceTableEntry(tableAddress);
    }

    @NoThrow
    public static int getInstanceSizeNonArray(int classId) {
        // look up class table for size
        Pointer tableAddress = getInheritanceTableEntry(classId);
        return read32(add(tableAddress, 4));
    }

    @NoThrow
    @Nullable
    static Pointer getInheritanceTableEntry(int classId) {
        return read32Ptr(add(inheritanceTable(), classId << 2));
    }

    @NoThrow
    private static int getSuperClassIdFromInheritanceTableEntry(Pointer tableAddress) {
        return read32(tableAddress);
    }

    @NoThrow
    public static boolean isChildOrSameClass(int childClassIdx, int parentClassIdx) {
        while (true) {
            if (childClassIdx == parentClassIdx) return true;
            // find super classes in table
            validateClassId(childClassIdx);
            Pointer tableAddress = getInheritanceTableEntry(childClassIdx);
            // we can return here if childClassIdx = 0, because java/lang/Object has no interfaces
            if (childClassIdx == 0) return false;
            // check for interfaces
            if (isInInterfaceTable(tableAddress, parentClassIdx)) return true;
            childClassIdx = getSuperClassIdFromInheritanceTableEntry(tableAddress); // switch to parent class
        }
    }

    @NoThrow
    public static boolean isChildOrSameClassNonInterface(int childClassIdx, int parentClassIdx) {
        while (true) {
            if (childClassIdx == parentClassIdx) return true;
            validateClassId(childClassIdx);
            Pointer tableAddress = getInheritanceTableEntry(childClassIdx);
            // we can return here if childClassIdx = 0, because java/lang/Object has no interfaces
            if (childClassIdx == 0) return false;
            childClassIdx = read32(tableAddress); // switch to parent class
        }
    }

    @NoThrow
    public static boolean isInInterfaceTable(Pointer tableAddress, int interfaceClassIdx) {
        // handle interfaces
        tableAddress = add(tableAddress, 8); // skip over super class and instance size
        int length = read32(tableAddress);
        for (int i = 0; i < length; i++) {
            tableAddress = add(tableAddress, 4); // skip to next entry
            if (read32(tableAddress) == interfaceClassIdx) return true;
        }
        return false;
    }

    @NoThrow
    public static void validateClassId(int childClassIdx) {
        if (ge_ub(childClassIdx, numClasses())) {
            log("class index out of bounds", childClassIdx, numClasses());
            throwJs();
        }
    }

    public static int resolveInterfaceByClass(int classId, int methodId) {
        // log("resolveInterfaceByClass", classId, methodId);
        validateClassId(classId);
        Pointer tablePtr = getInheritanceTableEntry(classId);
        // log("tablePtr", tablePtr);
        if (tablePtr == null) {
            log("No class table entry was found!", classId);
            log("method:", methodId);
            throwJs("No class table entry was found!");
        }
        int numInterfaces = read32(add(tablePtr, 8));
        /*log("#interfaces:", numInterfaces);
        for (int i = 0; i < numInterfaces; i++) {
            log("interface[i]:", read32(tablePtr + 12 + i * 4));
        }*/
        tablePtr = add(tablePtr, 12 + (numInterfaces << 2)); // 12 for super class + instance size + numInterfaces

        int tableLength = read32(tablePtr);
        tablePtr = add(tablePtr, 4); // table length
        /*log("#interfaceMethods:", tableLength);
        for (int i = 0; i < tableLength; i++) {
            log("interfaceMethod[i]:", read32(tablePtr + i * 8), read32(tablePtr + i * 8 + 4));
        }*/

        int min = 0;
        int max = tableLength - 1;

        int id = max - min < 16 ?
                searchInterfaceLinear(min, max, methodId, tablePtr) :
                searchInterfaceBinary(min, max, methodId, tablePtr);

        if (id == -1) {
            // return -1 - min
            reportNotFound(classId, methodId, tableLength, tablePtr);
        }

        return id;
    }

    static void reportNotFound(int clazz, int methodId, int tableLength, Pointer tablePtr) {
        // return -1 - min
        log("Method could not be found", clazz);
        log("  id, length:", methodId, tableLength);
        for (int i = 0; i < tableLength; i++) {
            Pointer addr = add(tablePtr, i << 3);
            log("  ", read32(addr), read32(add(addr, 4)));
        }
        throwJs("Method could not be found");
    }

    private static int searchInterfaceBinary(int min, int max, int methodId, Pointer tablePtr) {
        while (max >= min) {
            int mid = (min + max) >> 1;
            Pointer addr = add(tablePtr, mid << 3);
            int cmp = read32(addr) - methodId;
            if (cmp == 0) {
                return read32(add(addr, 4));
            }
            if (cmp < 0) {
                // search right
                min = mid + 1;
            } else {
                // search left
                max = mid - 1;
            }
        }
        return -1;
    }

    private static int searchInterfaceLinear(int minI, int maxI, int methodId, Pointer tablePtr) {
        Pointer max = add(tablePtr, maxI << 3);
        Pointer min = add(tablePtr, minI << 3);
        while (Pointer.unsignedGreaterThanEqual(max, min)) {
            if (read32(min) == methodId) {
                return read32(add(min, 4));
            }
            min = add(min, 8);
        }
        return -1;
    }

    @NoThrow
    @Alias(names = "resolveInterface")
    public static int resolveInterface(Object instance, int methodId) {
        if (instance == null) {
            throwJs("Instance for resolveInterface is null");
            return -1;
        } else {
            return resolveInterfaceByClass(readClassId(instance), methodId);
        }
    }

    @Export
    @NoThrow
    @Alias(names = "instanceOf")
    public static boolean instanceOf(Object instance, int classId) {
        // log("instanceOf", instance, clazz);
        if (instance == null) return false;
        if (classId == 0) return true;
        // checkAddress(instance);
        int testedClass = readClassId(instance);
        return isChildOrSameClass(testedClass, classId);
    }

    @Export
    @NoThrow
    @Alias(names = "instanceOfNonInterface")
    public static boolean instanceOfNonInterface(Object instance, int clazz) {
        // log("instanceOf", instance, clazz);
        if (instance == null) return false;
        if (clazz == 0) return true;
        // checkAddress(instance);
        int testedClass = readClassId(instance);
        return isChildOrSameClassNonInterface(testedClass, clazz);
    }

    @NoThrow
    @Alias(names = "instanceOfExact")
    public static boolean instanceOfExact(Object instance, int clazz) {
        return (instance != null) & (readClassId(instance) == clazz);
    }

    @NoThrow
    @WASM(code = "global.get $resolveIndirectTable")
    private static native Pointer getResolveIndirectTable();

    @NoThrow
    public static int resolveIndirectByClass(int classId, int methodPtr) {
        // log("resolveIndirectByClass", classId, methodPtr);
        Pointer tablePtr = add(getResolveIndirectTable(), classId << 2);
        Pointer table = read32Ptr(tablePtr);
        int resolvedIndex = read32(add(table, methodPtr));
        if (resolvedIndex < 0) {
            Class<Object> class1 = classIdToInstance(classId);
            log("resolveIndirectByClass", class1.getName());
            throwJs("classIndex, methodPtr, resolved:", classId, methodPtr, resolvedIndex);
        }
        return resolvedIndex;
    }

    // instance, class -> instance, error
    @Alias(names = "checkCast")
    public static Object checkCast(Object instance, int classId) {
        if (instance == null) return null;
        if (!instanceOf(instance, classId)) {
            failCastCheck(instance, classId);
        }
        return instance;
    }

    @Alias(names = "checkCastExact")
    public static Object checkCastExact(Object instance, int clazz) {
        if (instance == null) return null;
        if (!instanceOfExact(instance, clazz)) {
            failCastCheck(instance, clazz);
        }
        return instance;
    }

    @NoThrow
    @WASM(code = "global.get $numClasses")
    public static native int numClasses();

    @NoThrow
    @Alias(names = "resolveIndirect")
    public static int resolveIndirect(Object instance, int signatureId) {
        if (instance == null) {
            throwJs("Instance for resolveIndirect is null");
        }
        return resolveIndirectByClass(readClassId(instance), signatureId);
    }

    @NoThrow
    @JavaScript(code = "" +
            "console.log('Growing by ' + (arg0<<6) + ' kiB, total: '+(memory.buffer.byteLength>>20)+' MiB');\n" +
            "try { memory.grow(arg0); return true; }\n" +
            "catch(e) { console.error(e.stack); return false; }")
    private static native boolean grow(int numPages);

    @NoThrow
    public static boolean growS(long numPages) {
        int limit = 1 << 30;
        while (numPages > limit) {
            if (!grow(limit)) return false;
            numPages -= limit;
        }
        return grow((int) numPages);
    }

    @NoThrow
    @WASM(code = "") // auto
    @Deprecated
    public static native <V> V unsafeCast(Object obj);

    @NoThrow
    @WASM(code = "") // auto
    public static native Pointer castToPtr(Object obj);

    @Alias(names = "createNativeArray2")
    public static Object[] createNativeArray2(int l0, int l1, int clazz) {
        Object[] array = createObjectArray(l0);
        for (int i = 0; i < l0; i++) {
            arrayStore(array, i, createNativeArray1(l1, clazz));
        }
        return array;
    }

    @Alias(names = "createNativeArray3")
    public static Object[] createNativeArray3(int l0, int l1, int l2, int clazz) {
        Object[] array = createObjectArray(l0);
        for (int i = 0; i < l0; i++) {
            arrayStore(array, i, createNativeArray2(l1, l2, clazz));
        }
        return array;
    }

    @Alias(names = "createNativeArray4")
    public static Object[] createNativeArray4(int l0, int l1, int l2, int l3, int clazz) {
        Object[] array = createObjectArray(l0);
        for (int i = 0; i < l0; i++) {
            arrayStore(array, i, createNativeArray3(l1, l2, l3, clazz));
        }
        return array;
    }

    @Alias(names = "createNativeArray5")
    public static Object[] createNativeArray5(int l0, int l1, int l2, int l3, int l4, int clazz) {
        Object[] array = createObjectArray(l0);
        for (int i = 0; i < l0; i++) {
            arrayStore(array, i, createNativeArray4(l1, l2, l3, l4, clazz));
        }
        return array;
    }

    @Alias(names = "createNativeArray6")
    public static Object[] createNativeArray6(int l0, int l1, int l2, int l3, int l4, int l5, int clazz) {
        Object[] array = createObjectArray(l0);
        for (int i = 0; i < l0; i++) {
            arrayStore(array, i, createNativeArray5(l1, l2, l3, l4, l5, clazz));
        }
        return array;
    }

    @NoThrow
    static void printStackTraceLine(int index) {
        Pointer lookupBasePtr = getStackTraceTablePtr();
        if (lookupBasePtr == null) return;
        Pointer throwableLookup = add(lookupBasePtr, index * 12);
        String className = unsafeCast(read32Ptr(throwableLookup));
        String methodName = unsafeCast(read32Ptr(add(throwableLookup, 4)));
        int line = read32(add(throwableLookup, 8));
        printStackTraceLine(getStackDepth(), className, methodName, line);
    }

    @NoThrow
    @JavaScript(code = "console.log('  '.repeat(arg0) + str(arg1) + '.' + str(arg2) + ':' + arg3)")
    private static native void printStackTraceLine(int depth, String className, String methodName, int lineNumber);

    @Alias(names = "resolveIndirectFail")
    public static void resolveIndirectFail(Object instance, String methodName) {
        throwJs("Resolving non constructable method", castToPtr(instance), methodName);
    }

    @NoThrow
    @Alias(names = "findClass")
    public static Class<Object> classIdToInstance(int classId) {
        return unsafeCast(add(getClassInstanceTable(), classId * getClassSize()));
    }

    @Alias(names = "createInstance")
    public static Object createInstance(int classId) {
        validateClassId(classId);

        int instanceSize = getInstanceSizeNonArray(classId);
        if (instanceSize < 0) throw new IllegalStateException("Non-constructable class cannot be instantiated");
        if (instanceSize == 0) return getClassIdPtr(classId); // pseudo-instance

        if (trackAllocations) trackCalloc(classId);
        Object newInstance = calloc(ptrTo(instanceSize));
        writeClass(newInstance, classId);
        // log("Created", classId, newInstance, instanceSize);
        // if (newInstance > 10_300_000) validateAllClassIds();
        return newInstance;
    }

    @Alias(names = "createObjectArray")
    public static Object[] createObjectArray(int length) {
        // probably a bit illegal; should be fine for us, saving allocations :)
        if (length == 0) {
            Object[] sth = emptyArray;
            if (sth != null) return sth;
            // else awkward, probably recursive trap
        }
        // log("creating array", length);
        return (Object[]) createNativeArray1(length, OBJECT_ARRAY);
    }
}
