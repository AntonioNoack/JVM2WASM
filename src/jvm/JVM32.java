package jvm;

import annotations.*;

import static jvm.ArrayAccessSafe.arrayStore;
import static jvm.GarbageCollector.largestGaps;
import static jvm.JVMShared.*;
import static jvm.JVMValues.emptyArray;
import static jvm.JavaLang.*;
import static jvm.NativeLog.log;
import static jvm.ThrowJS.throwJs;
import static utils.StaticFieldOffsets.OFFSET_CLASS_INDEX;

@SuppressWarnings("unused")
public class JVM32 {

    public static final int objectOverhead = 4;// 3x class, 1x GC
    public static final int arrayOverhead = objectOverhead + 4;// length
    public static final int ptrSize = 4; // this is JVM32, so this is correct
    public static boolean trackAllocations = true;

    // public static int fieldTableOffset = getFieldTableOffset(); // for GC

    @NoThrow
    @WASM(code = "global.get $inheritanceTable")
    public static native int inheritanceTable();

    @Export
    @NoThrow
    @UsedIfIndexed
    @Alias(names = "oo")
    public static int getObjectOverhead() {
        return objectOverhead;
    }

    @NoThrow
    @WASM(code = "global.get $staticTable")
    public static native int staticInstancesOffset();

    @NoThrow
    @Alias(names = "findClass")
    public static Class<Object> findClass(int classIdx) {
        return ptrTo(findClassPtr(classIdx));
    }

    @NoThrow
    @WASM(code = "global.get $classSize")
    public static native int getClassSize();

    @NoThrow
    @WASM(code = "global.get $classInstanceTable")
    public static native int getClassInstanceTable();

    @NoThrow
    public static int findClassPtr(int classId) {
        return classId * getClassSize() + getClassInstanceTable();
    }

    @NoThrow
    @Alias(names = "findStatic")
    public static int findStatic(int clazz, int offset) {
        // log("finding static", clazz, offset);
        return read32(staticInstancesOffset() + (clazz << 2)) + offset;
    }

    static void failCastCheck(Object instance, int clazz) {
        Class<Object> isClass = findClass(readClassIdI(instance));
        Class<Object> checkClass = findClass(clazz);
        log(isClass.getName(), "is not instance of", checkClass.getName(), getAddr(instance));
        throw new ClassCastException();
    }

    @Alias(names = "resolveIndirectFail")
    public static void resolveIndirectFail(Object instance, String methodName) {
        throwJs("Resolving non constructable method", getAddr(instance), methodName);
    }

    // todo mark some static fields as not needing <clinit>
    // private static int riLastClass, riLastMethod, riLastImpl;

    @NoThrow
    private static void checkAddress(int instance) {
        if (ge_ub(instance, getAllocatedSize())) {
            throwJs("Not a valid address!", instance);
        }
    }

    @Alias(names = "createInstance")
    public static int createInstance(int clazz) {
        validateClassIdx(clazz);
        if (trackAllocations) trackCalloc(clazz);
        int instanceSize = getInstanceSizeNonArray(clazz);
        if (instanceSize <= 0)
            throw new IllegalStateException("Non-constructable/abstract class cannot be instantiated");
        int newInstance = calloc(instanceSize);

        // log("Creating", clazz, newInstance);
        /*if (instanceSize > 1024) {
            log("Allocating", clazz, instanceSize, newInstance);
            printStackTrace();
        }*/

        writeClass(newInstance, clazz);
        return newInstance;
    }

    @NoThrow
    @JavaScript(code = "calloc[arg0] = (calloc[arg0]||0)+1")
    private static native void trackCalloc(int clazz);

    @Alias(names = "createObjectArray")
    public static int createObjectArray(int length) {
        // probably a bit illegal; should be fine for us, saving allocations :)
        if (length == 0) {
            Object sth = emptyArray;
            if (sth != null) return getAddr(sth);
            // else awkward, probably recursive trap
        }
        // log("creating array", length);
        return createNativeArray1(length, 1);
    }

    @Alias(names = "createNativeArray1")
    public static int createNativeArray1(int length, int clazz) {
        // log("creating native array", length, clazz);
        if (length < 0) throw new IllegalArgumentException();
        int typeShift = getTypeShiftUnsafe(clazz);
        int numDataBytes = length << typeShift;
        if ((numDataBytes >>> typeShift) != length) {
            throw new IllegalArgumentException("Length is too large for 32 bit memory");
        }
        int instanceSize = arrayOverhead + numDataBytes;
        int newInstance = calloc(instanceSize);

        /*if (instanceSize > 1024) {
            log("Allocating", clazz, instanceSize, newInstance);
            printStackTrace();
        }*/

        // log("calloc/2", clazz, instanceSize, newInstance);
        writeClass(newInstance, clazz); // [] has index 1
        write32(newInstance + objectOverhead, length); // array length
        // log("instance:", newInstance);
        return newInstance;
    }

    @Alias(names = "createNativeArray2")
    public static int createNativeArray2(int l0, int l1, int clazz) {
        if (l0 == 0) return 0;
        int array = createNativeArray1(l0, 1);
        for (int i = 0; i < l0; i++) {
            arrayStore(array, i, createNativeArray1(l1, clazz));
        }
        return array;
    }

    @Alias(names = "createNativeArray3")
    public static int createNativeArray3(int l0, int l1, int l2, int clazz) {
        if (l0 == 0) return 0;
        int array = createNativeArray1(l0, 1);
        for (int i = 0; i < l0; i++) {
            arrayStore(array, i, createNativeArray2(l1, l2, clazz));
        }
        return array;
    }

    @Alias(names = "createNativeArray4")
    public static int createNativeArray4(int l0, int l1, int l2, int l3, int clazz) {
        if (l0 == 0) return 0;
        int array = createNativeArray1(l0, 1);
        for (int i = 0; i < l0; i++) {
            arrayStore(array, i, createNativeArray3(l1, l2, l3, clazz));
        }
        return array;
    }

    @Alias(names = "createNativeArray5")
    public static int createNativeArray5(int l0, int l1, int l2, int l3, int l4, int clazz) {
        if (l0 == 0) return 0;
        int array = createNativeArray1(l0, 1);
        for (int i = 0; i < l0; i++) {
            arrayStore(array, i, createNativeArray4(l1, l2, l3, l4, clazz));
        }
        return array;
    }

    @Alias(names = "createNativeArray6")
    public static int createNativeArray6(int l0, int l1, int l2, int l3, int l4, int l5, int clazz) {
        if (l0 == 0) return 0;
        int array = createNativeArray1(l0, 1);
        for (int i = 0; i < l0; i++) {
            arrayStore(array, i, createNativeArray5(l1, l2, l3, l4, l5, clazz));
        }
        return array;
    }

    @NoThrow
    @WASM(code = "global.get $allocationStart")
    public static native int getAllocationStart();

    @NoThrow
    @WASM(code = "global.get $allocationPointer")
    public static native int getNextPtr();

    @NoThrow
    public static int queryGCed() {
        int sum = 0;
        int[] data = largestGaps;
        for (int i = 0, l = data.length; i < l; i += 2) {
            sum += data[i];
        }
        return sum;
    }

    @NoThrow
    @WASM(code = "global.set $allocationPointer")
    public static native void setNextPtr(int value);

    // @WASM(code = "memory.size i32.const 16 i32.shl")
    @NoThrow
    @JavaScript(code = "return memory.buffer.byteLength;")
    public static native int getAllocatedSize();

    @NoThrow
    @JavaScript(code = "console.log('Growing by ' + (arg0<<6) + ' kiB, total: '+(memory.buffer.byteLength>>20)+' MiB'); try { memory.grow(arg0); return true; } catch(e) { console.error(e.stack); return false; }")
    public static native boolean grow(int numPages);

    @NoThrow

    public static int adjustCallocSize(int size) {
        // 4 is needed for GPU stuff, or we'd have to reallocate it on the JS side
        // 8 is needed for x86
        return ((size + 7) >>> 3) << 3;
    }

    public static boolean criticalAlloc = false;

    @NoThrow
    public static void writeClass(int ptr, int clazz) {
        write32(ptr, clazz | (GarbageCollector.iteration << 24));
    }

    public static int calloc(int size) {

        size = adjustCallocSize(size);

        int ptr;
        // enough space for the first allocations
        if (GarbageCollectorFlags.hasGaps) {
            // try to find a freed place in gaps first
            ptr = GarbageCollector.findGap(size);
            if (ptr == 0) {
                ptr = GarbageCollector.allocateNewSpace(size);
            }
        } else {
            ptr = GarbageCollector.allocateNewSpace(size);
        }

        int endPtr = ptr + size;
        fill64(ptr, endPtr, 0);
        return ptr;
    }

    @NoThrow
    public static void fill8(int start, int end, byte value) {
        int value1 = ((int) value) & 255;
        fill16(start, end, (short) ((value1 << 8) | value1));
    }

    @NoThrow
    public static void fill16(int start, int end, short value) {
        int value1 = ((int) value) & 0xffff;
        fill32(start, end, (value1 << 16) | value1);
    }

    @NoThrow
    public static void fill32(int start, int end, int value) {
        long value1 = ((long) value) & 0xffffffffL;
        fill64(start, end, (value1 << 32) | value1);
    }

    @NoThrow
    @WASM(code = "i64.ne")
    private static native boolean neq(long a, long b);

    @NoThrow
    @WASM(code = "i32.and")
    private static native boolean and(boolean a, int b);

    @NoThrow
    @WASM(code = "i32.and")
    private static native boolean and(boolean a, boolean b);

    /**
     * finds the position of the next different byte;
     * in start
     */
    @NoThrow
    public static int findDiscrepancy(int start, int end, int start2) {
        // align memory
        while (and(unsignedLessThan(start, end), start & 7)) {
            if (read8(start) != read8(start2)) return start;
            start++;
            start2++;
        }
        return findDiscrepancyAligned(start, end, start2);
    }

    @NoThrow
    private static int findDiscrepancyAligned(int start, int end, int start2) {
        // quickly find first different byte
        int end8 = end - 7;
        while (unsignedLessThan(start, end8)) {
            if (neq(read64(start), read64(start2))) {
                break;
            }
            start += 8;
            start2 += 8;
        }
        return findDiscrepancyAlignedEnd(start, end, start2);
    }

    /**
     * finds the position of the next different byte
     */
    @NoThrow
    private static int findDiscrepancyAlignedEnd(int start, int end, int start2) {
        if (and(unsignedLessThan(start, end - 3), read32(start) == read32(start2))) {
            start += 4;
            start2 += 4;
        }
        if (and(unsignedLessThan(start, end - 1), read16s(start) == read16s(start2))) {
            start += 2;
            start2 += 2;
        }
        if (and(unsignedLessThan(start, end), read8(start) == read8(start2))) {
            start++;
            // start2++;
        }
        return start;
    }

    @NoThrow
    public static void fill64(int start, int end, long value) {

        // benchmark:
        // let t0 = window.performance.now()
        // for(let i=0;i<100000;i++) instance.exports._c(65536)
        // let t1 = window.performance.now()

        // check extra performance of that
        // -> 6x total performance improvement :D
        // 740ms -> 310ms for 100k * 65kB
        // JavaScript speed: 440ms
        // -> so much for WASM being twice as fast 😅

        if ((start & 1) != 0 && unsignedLessThan(start, end)) {
            write8(start, (byte) value);
            start++;
        }
        if ((start & 2) != 0 && unsignedLessThan(start, end - 1)) {
            write16(start, (short) value);
            start += 2;
        }
        if ((start & 4) != 0 && unsignedLessThan(start, end - 3)) {
            write32(start, (int) value);
            start += 4;
        }
        if ((start & 8) != 0 && unsignedLessThan(start, end - 7)) {
            write64(start, value);
            start += 8;
        }

        final int endPtr64 = end - 63;
        for (; unsignedLessThan(start, endPtr64); start += 64) {
            // todo later detect simd features, and send corresponding build :)
            // https://v8.dev/features/simd
            /*clear128(p);
            clear128(p + 16);
            clear128(p + 32);
            clear128(p + 48);
            * */
            write64(start, value);
            write64(start + 8, value);
            write64(start + 16, value);
            write64(start + 24, value);
            write64(start + 32, value);
            write64(start + 40, value);
            write64(start + 48, value);
            write64(start + 56, value);
        }

        final int endPtr8 = end - 7;
        for (; unsignedLessThan(start, endPtr8); start += 8) {
            write64(start, value);
        }
        if (unsignedLessThan(start, end - 3)) {
            write32(start, (int) value);
            start += 4;
        }
        if (unsignedLessThan(start, end - 1)) {
            write16(start, (short) value);
            start += 2;
        }
        if (unsignedLessThan(start, end)) {
            write8(start, (byte) value);
        }
    }

    @NoThrow
    @WASM(code = "i32.lt_u")
    public static native boolean unsignedLessThan(int a, int b);

    @NoThrow
    @WASM(code = "i32.le_u")
    public static native boolean unsignedLessThanEqual(int a, int b);

    @NoThrow
    @WASM(code = "i32.gt_u")
    public static native boolean unsignedGreaterThan(int a, int b);

    @NoThrow
    @WASM(code = "i32.ge_u")
    public static native boolean unsignedGreaterThanEqual(int a, int b);

    @Alias(names = "isOOB")
    public static void checkOutOfBounds(int instance, int index) {
        if (instance == 0) throw new NullPointerException("isOOB");
        // checkAddress(instance);
        int length = read32(instance + objectOverhead);
        if (ge_ub(index, length)) {
            throw new IndexOutOfBoundsException();
        }
    }

    @Alias(names = "isOOB2")
    public static void checkOutOfBounds(int instance, int index, int clazz) {
        if (instance == 0) throw new NullPointerException("isOOB2");
        // checkAddress(instance);
        if (readClassId(instance) != clazz) throwJs("Incorrect clazz!", instance, readClassId(instance), clazz);
        int length = read32(instance + objectOverhead);
        if (ge_ub(index, length)) {
            throw new IndexOutOfBoundsException();
        }
    }

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

    /**
     * returns the class index for the given instance;
     * preventing inlining, so we can call it from JS
     */
    @NoThrow
    @Alias(names = "readClass")
    public static int readClass1(int addr) {
        return readClassId(addr);
    }

    /**
     * returns the class index for the given instance
     */
    @NoThrow
    @WASM(code = "i32.load i32.const 16777215 i32.and")
    public static native int readClassId(int addr);

    /**
     * returns the class index for the given instance
     */
    @NoThrow
    public static int readClassIdI(Object instance) {
        return readClassId(getAddr(instance));
    }

    @NoThrow
    static void printStackTraceLine(int index) {
        int lookupBasePtr = getStackTraceTablePtr();
        if (lookupBasePtr <= 0) return;
        int throwableLookup = lookupBasePtr + index * 12;
        String className = ptrTo(read32(throwableLookup));
        String methodName = ptrTo(read32(throwableLookup + 4));
        int line = read32(throwableLookup + 8);
        printStackTraceLine(getStackDepth(), className, methodName, line);
    }

    @NoThrow
    public static int readI32AtOffset(Object instance, int offset) {
        return read32(getAddr(instance) + offset);
    }

    @NoThrow
    public static <V> V readPtrAtOffset(Object instance, int offset) {
        return ptrTo(readI32AtOffset(instance, offset));
    }

    @NoThrow
    public static void writeI32AtOffset(Object instance, int offset, int value) {
        write32(getAddr(instance) + offset, value);
    }

    @NoThrow
    public static void writePtrAtOffset(Object instance, int offset, Object value) {
        writeI32AtOffset(instance, offset, getAddr(value));
    }

    @NoThrow
    @JavaScript(code = "console.log('  '.repeat(arg0) + str(arg1) + '.' + str(arg2) + ':' + arg3)")
    private static native void printStackTraceLine(int depth, String clazz, String method, int line);

    @NoThrow
    @Alias(names = "getClassIndexPtr")
    public static <V> int getClassIndexPtr(int classIndex) {
        validateClassIdx(classIndex);
        int addr = findClassPtr(classIndex) + OFFSET_CLASS_INDEX;
        int actualIndex = read32(addr);
        // log("getClassIndexPtr", classIndex, addr, actualIndex);
        if (actualIndex != classIndex) {
            log("addr = {} * {}", classIndex, getClassSize());
            log("+ {} + {}", getClassInstanceTable(), OFFSET_CLASS_INDEX);
            throwJs("Expected {} at {}, got {}", classIndex, addr, actualIndex);
        }
        return addr;
    }

    @NoThrow
    @WASM(code = "") // auto
    public static native int getAddr(Object obj);

}
