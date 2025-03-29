package jvm;

import annotations.*;

import static jvm.GCGapFinder.getInstanceSize;
import static jvm.GarbageCollector.largestGaps;
import static jvm.JVMShared.*;
import static jvm.JVMValues.emptyArray;
import static jvm.JavaLang.getStackTraceTablePtr;
import static jvm.NativeLog.log;
import static jvm.ThrowJS.throwJs;
import static utils.StaticClassIndices.FIRST_ARRAY;
import static utils.StaticClassIndices.LAST_ARRAY;
import static utils.StaticFieldOffsets.OFFSET_CLASS_INDEX;

@SuppressWarnings("unused")
public class JVM32 {

    public static final int objectOverhead = 4;// 3x class, 1x GC
    public static final int arrayOverhead = objectOverhead + 4;// length
    public static final int ptrSizeBits = 2; // this is JVM32, so this is correct
    public static final int ptrSize = 1 << ptrSizeBits;
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
    @WASM(code = "") // todo this method shall only be used by JVM32
    public static native <V> V ptrTo(int addr);

    @NoThrow
    public static <V> V ptrTo(long addr) {
        return ptrTo((int) addr);
    }

    @NoThrow
    @WASM(code = "") // automatically converted
    public static native int getAddr(Object obj);

    @NoThrow
    @WASM(code = "global.get $classSize")
    public static native int getClassSize();

    @NoThrow
    @WASM(code = "global.get $classInstanceTable")
    public static native int getClassInstanceTable();

    @NoThrow
    @Alias(names = "findClass")
    public static Class<Object> classIdToInstance(int classId) {
        return ptrTo(classIdToInstancePtr(classId));
    }

    @NoThrow
    public static int classIdToInstancePtr(int classId) {
        return classId * getClassSize() + getClassInstanceTable();
    }

    @NoThrow
    @Alias(names = "findStatic")
    public static int findStatic(int classId, int offset) {
        // log("finding static", clazz, offset);
        return read32(staticInstancesOffset() + (classId << 2)) + offset;
    }

    static void failCastCheck(Object instance, int clazz) {
        Class<Object> isClass = classIdToInstance(readClassId(instance));
        Class<Object> checkClass = classIdToInstance(clazz);
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
    public static Object createInstance(int classId) {
        validateClassId(classId);

        int instanceSize = getInstanceSizeNonArray(classId);
        if (instanceSize < 0) throw new IllegalStateException("Non-constructable class cannot be instantiated");
        if (instanceSize == 0) return getClassIdPtr(classId); // pseudo-instance

        if (trackAllocations) trackCalloc(classId);
        Object newInstance = calloc(instanceSize);
        writeClass(newInstance, classId);
        // log("Created", classId, newInstance, instanceSize);
        // if (newInstance > 10_300_000) validateAllClassIds();
        return newInstance;
    }

    @NoThrow
    @JavaScript(code = "calloc[arg0] = (calloc[arg0]||0)+1")
    private static native void trackCalloc(int clazz);

    @NoThrow
    @JavaScript(code = "calloc[arg0] = (calloc[arg0]||0)+1")
    private static native void trackCalloc(int clazz, int size);

    @Alias(names = "createObjectArray")
    public static Object[] createObjectArray(int length) {
        // probably a bit illegal; should be fine for us, saving allocations :)
        if (length == 0) {
            Object[] sth = emptyArray;
            if (sth != null) return sth;
            // else awkward, probably recursive trap
        }
        // log("creating array", length);
        return (Object[]) createNativeArray1(length, 1);
    }

    @Alias(names = "createNativeArray1")
    public static Object createNativeArray1(int length, int classId) {
        if (length < 0) throw new IllegalArgumentException();
        if (trackAllocations) trackCalloc(classId, length);
        int typeShift = getTypeShiftUnsafe(classId);
        int numDataBytes = length << typeShift;
        if ((numDataBytes >>> typeShift) != length) {
            throw new IllegalArgumentException("Length is too large for 32 bit memory");
        }
        int instanceSize = getArraySizeInBytes(length, classId);
        Object newInstance = calloc(instanceSize);

        writeClass(newInstance, classId); // [] has index 1
        writeI32AtOffset(newInstance, objectOverhead, length); // array length
        // log("Created[]", classId, newInstance, instanceSize);
        // if (newInstance > 10_300_000) validateAllClassIds();
        return newInstance;
    }

    public static int getArraySizeInBytes(int length, int classId) {
        if (classId < FIRST_ARRAY || classId > LAST_ARRAY) {
            log("Invalid classId for getArraySizeInBytes", classId);
            throw new IllegalArgumentException();
        }
        int typeShift = getTypeShiftUnsafe(classId);
        int numDataBytes = length << typeShift;
        if ((numDataBytes >>> typeShift) != length) {
            log("Length is too large for 32 bit memory", length, typeShift);
            throw new IllegalArgumentException();
        }
        int size = arrayOverhead + numDataBytes;
        return adjustCallocSize(size);
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
    public static void writeClass(Object ptr, int clazz) {
        writeI32AtOffset(ptr, 0, clazz | (GarbageCollector.iteration << 24));
    }

    private static Object malloc(int size) {
        // enough space for the first allocations
        int ptr;
        if (GarbageCollectorFlags.hasGaps) {
            // try to find a freed place in gaps first
            ptr = GarbageCollector.findGap(size);
            if (ptr == 0) {
                ptr = GarbageCollector.allocateNewSpace(size);
            }
        } else ptr = GarbageCollector.allocateNewSpace(size);
        return ptrTo(ptr);
    }

    public static Object calloc(int size) {
        Object ptr = malloc(size);
        fill64(getAddr(ptr), getAddr(ptr) + size, 0);
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

    @NoThrow
    public static boolean isDynamicInstance(Object instance) {
        return unsignedGreaterThanEqual(getAddr(instance), getAllocationStart());
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
     * returns the class index for the given instance
     */
    @NoThrow
    @WASM(code = "i32.load i32.const 16777215 i32.and")
    public static native int readClassIdImpl(int addr);

    /**
     * returns the class index for the given instance
     */
    @NoThrow
    @Alias(names = "readClass")
    public static int readClassId(Object instance) {
        return readClassIdImpl(getAddr(instance));
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
    public static void writeI8AtOffset(Object instance, int offset, byte value) {
        write8(getAddr(instance) + offset, value);
    }

    @NoThrow
    public static void writeI16AtOffset(Object instance, int offset, short value) {
        write16(getAddr(instance) + offset, value);
    }

    @NoThrow
    public static byte readI8AtOffset(Object instance, int offset) {
        return read8(getAddr(instance) + offset);
    }

    @NoThrow
    public static short readS16AtOffset(Object instance, int offset) {
        return read16s(getAddr(instance) + offset);
    }

    @NoThrow
    public static char readU16AtOffset(Object instance, int offset) {
        return read16u(getAddr(instance) + offset);
    }

    @NoThrow
    public static float readF32AtOffset(Object instance, int offset) {
        return read32f(getAddr(instance) + offset);
    }

    @NoThrow
    public static double readF64AtOffset(Object instance, int offset) {
        return read64f(getAddr(instance) + offset);
    }

    @NoThrow
    public static int readI32AtOffset(Object instance, int offset) {
        // todo offset must become long for ptrSize=8
        return read32(getAddr(instance) + offset);
    }

    @NoThrow
    public static long readI64AtOffset(Object instance, int offset) {
        return read64(getAddr(instance) + offset);
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
    public static void writeI64AtOffset(Object instance, int offset, long value) {
        write64(getAddr(instance) + offset, value);
    }

    @NoThrow
    public static void writeF32AtOffset(Object instance, int offset, float value) {
        write32(getAddr(instance) + offset, value);
    }

    @NoThrow
    public static void writeF64AtOffset(Object instance, int offset, double value) {
        write64(getAddr(instance) + offset, value);
    }

    @NoThrow
    public static void writePtrAtOffset(Object instance, int offset, Object value) {
        writeI32AtOffset(instance, offset, getAddr(value));
    }

    @NoThrow
    @JavaScript(code = "console.log('  '.repeat(arg0) + str(arg1) + '.' + str(arg2) + ':' + arg3)")
    private static native void printStackTraceLine(int depth, String clazz, String method, int line);

    @NoThrow
    @Alias(names = "getClassIdPtr")
    public static <V> Object getClassIdPtr(int classIndex) {
        validateClassId(classIndex);
        int classIdPtr = classIdToInstancePtr(classIndex) + OFFSET_CLASS_INDEX;

        int actualIndex = read32(classIdPtr);
        // log("getClassIndexPtr", classIndex, addr, actualIndex);
        if (actualIndex != classIndex) {
            log("addr = {} * {}", classIndex, getClassSize());
            log("+ {} + {}", getClassInstanceTable(), OFFSET_CLASS_INDEX);
            throwJs("Expected {} at {}, got {}", classIndex, classIdPtr, actualIndex);
        }
        return ptrTo(classIdPtr);
    }

    @NoThrow
    @Alias(names = "checkWrite")
    public static void checkWrite(int addr0, int offset, int length) {
        int fieldAddr = addr0 + offset;
        if (addr0 > 0) {
            // not static
            int classId = readClassIdImpl(addr0);
            int instanceSize = getInstanceSize(addr0, classId);
            if (instanceSize < offset + length || offset < 0) {
                log("Invalid write!!!", addr0, classId, offset);
                throwJs();
            }
        }

        int magicAddress = 10386896;
        int magicLength = 8;
        if (fieldAddr < magicAddress + magicLength &&
                fieldAddr + length > magicAddress) {
            log("Invalid Write???", addr0, offset, length);
            new RuntimeException("Invalid Write???").printStackTrace();
        }
    }

}
