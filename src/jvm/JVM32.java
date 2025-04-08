package jvm;

import annotations.Alias;
import annotations.JavaScript;
import annotations.NoThrow;
import annotations.WASM;
import jvm.gc.GarbageCollector;
import jvm.gc.GarbageCollectorFlags;

import static jvm.JVMFlags.is32Bits;
import static jvm.JVMShared.*;
import static jvm.NativeLog.log;
import static jvm.Pointer.*;
import static jvm.ThrowJS.throwJs;
import static utils.StaticClassIndices.FIRST_ARRAY;
import static utils.StaticClassIndices.LAST_ARRAY;
import static utils.StaticFieldOffsets.OFFSET_CLASS_INDEX;

@SuppressWarnings("unused")
public class JVM32 {

    // todo mark some static fields as not needing <clinit>
    // private static int riLastClass, riLastMethod, riLastImpl;

    @Alias(names = "createNativeArray1")
    public static Object createNativeArray1(int length, int classId) {
        if (length < 0) throw new IllegalArgumentException();
        if (trackAllocations) trackCalloc(classId, length);
        int typeShift = getTypeShiftUnsafe(classId);
        Pointer instanceSize = getArraySizeInBytes(length, classId);
        Object newInstance = calloc(instanceSize);
        writeClass(newInstance, classId); // [] has index 1
        writeI32AtOffset(newInstance, objectOverhead, length); // array length
        // log("Created[]", classId, newInstance, instanceSize);
        // if (newInstance > 10_300_000) validateAllClassIds();
        return newInstance;
    }

    public static Pointer getArraySizeInBytes(int length, int classId) {
        if (classId < FIRST_ARRAY || classId > LAST_ARRAY) {
            log("Invalid classId for getArraySizeInBytes", classId);
            throw new IllegalArgumentException();
        }
        int typeShift = getTypeShiftUnsafe(classId);
        long numDataBytes = (long) length << typeShift;
        long size = adjustCallocSize(arrayOverhead + numDataBytes);
        if (is32Bits && size > Integer.MAX_VALUE) {
            log("Length is too large for 32 bit memory", length, typeShift);
            throw new IllegalArgumentException();
        }
        return ptrTo(size);
    }

    @NoThrow
    @WASM(code = "global.get $allocationStart")
    public static native Pointer getAllocationStart();

    @NoThrow
    @WASM(code = "global.get $allocationPointer")
    public static native Pointer getNextPtr();

    @NoThrow
    @WASM(code = "global.set $allocationPointer")
    public static native void setNextPtr(Pointer value);

    // @WASM(code = "memory.size i32.const 16 i32.shl")
    @NoThrow
    @JavaScript(code = "return memory.buffer.byteLength;")
    public static native Pointer getAllocatedSize();

    @NoThrow
    public static long adjustCallocSize(long size) {
        // 4 is needed for GPU stuff, or we'd have to reallocate it on the JS side
        // 8 is needed for x86
        return ((size + 7) >>> 3) << 3;
    }

    public static boolean criticalAlloc = false;

    @NoThrow
    public static void writeClass(Object ptr, int clazz) {
        writeI32AtOffset(ptr, 0, clazz | (GarbageCollector.iteration << 24));
    }

    private static Pointer malloc(Pointer size) {
        // enough space for the first allocations
        Pointer ptr;
        if (GarbageCollectorFlags.hasGaps &&
                getAddrS(size) <= Integer.MAX_VALUE) {
            // try to find a freed place in gaps first
            ptr = GarbageCollector.findGap((int) getAddrS(size));
            if (ptr == null) {
                ptr = GarbageCollector.allocateNewSpace(size);
            }
        } else ptr = GarbageCollector.allocateNewSpace(size);
        return ptr;
    }

    public static Object calloc(Pointer size) {
        Pointer ptr = malloc(size);
        fill64(ptr, add(ptr, size), 0);
        return ptr;
    }

    @NoThrow
    @WASM(code = "i32.and i32.eqz i32.eqz")
    public static native boolean hasFlag(Pointer addr, int mask);

    /**
     * finds the position of the next different byte;
     * in start
     */
    @NoThrow
    public static Pointer findDiscrepancy(Pointer start, Pointer end, Pointer start2) {
        // align memory
        while (unsignedLessThan(start, end) && hasFlag(start, 7)) {
            if (read8(start) != read8(start2)) return start;
            start = add(start, 1);
            start2 = add(start2, 1);
        }
        return findDiscrepancyAligned(start, end, start2);
    }

    @NoThrow
    private static Pointer findDiscrepancyAligned(Pointer start, Pointer end, Pointer start2) {
        // quickly find first different byte
        Pointer end8 = sub(end, 7);
        while (unsignedLessThan(start, end8)) {
            if (neq(read64(start), read64(start2))) {
                break;
            }
            start = add(start, 8);
            start2 = add(start2, 8);
        }
        return findDiscrepancyAlignedEnd(start, end, start2);
    }

    /**
     * finds the position of the next different byte
     */
    @NoThrow
    private static Pointer findDiscrepancyAlignedEnd(Pointer start, Pointer end, Pointer start2) {
        if (unsignedLessThan(start, sub(end, 3)) && read32(start) == read32(start2)) {
            start = add(start, 4);
            start2 = add(start2, 4);
        }
        if (unsignedLessThan(start, sub(end, 1)) && read16s(start) == read16s(start2)) {
            start = add(start, 2);
            start2 = add(start2, 2);
        }
        if (unsignedLessThan(start, end) && read8(start) == read8(start2)) {
            start = add(start, 1);
            // start2++;
        }
        return start;
    }

    @NoThrow
    public static void fill64(Pointer start, Pointer end, long value) {

        // benchmark:
        // let t0 = window.performance.now()
        // for(let i=0;i<100000;i++) instance.exports._c(65536)
        // let t1 = window.performance.now()

        // check extra performance of that
        // -> 6x total performance improvement :D
        // 740ms -> 310ms for 100k * 65kB
        // JavaScript speed: 440ms
        // -> so much for WASM being twice as fast 😅

        if (hasFlag(start, 1) && unsignedLessThan(start, end)) {
            write8(start, (byte) value);
            start = add(start, 1);
        }
        if (hasFlag(start, 2) && unsignedLessThan(start, sub(end, 1))) {
            write16(start, (short) value);
            start = add(start, 2);
        }
        if (hasFlag(start, 4) && unsignedLessThan(start, sub(end, 3))) {
            write32(start, (int) value);
            start = add(start, 4);
        }
        if (hasFlag(start, 8) && unsignedLessThan(start, sub(end, 7))) {
            write64(start, value);
            start = add(start, 8);
        }

        final Pointer endPtr64 = sub(end, 63);
        while (unsignedLessThan(start, endPtr64)) {
            // todo later detect simd features, and send corresponding build :)
            // https://v8.dev/features/simd
            /*clear128(p);
            clear128(p + 16);
            clear128(p + 32);
            clear128(p + 48);
            * */
            write64(start, value);
            write64(add(start, 8), value);
            write64(add(start, 16), value);
            write64(add(start, 24), value);
            write64(add(start, 32), value);
            write64(add(start, 40), value);
            write64(add(start, 48), value);
            write64(add(start, 56), value);
            start = add(start, 64);
        }

        final Pointer endPtr8 = sub(end, 7);
        while (unsignedLessThan(start, endPtr8)) {
            write64(start, value);
            start = add(start, 8);
        }
        if (unsignedLessThan(start, sub(end, 3))) {
            write32(start, (int) value);
            start = add(start, 4);
        }
        if (unsignedLessThan(start, sub(end, 1))) {
            write16(start, (short) value);
            start = add(start, 2);
        }
        if (unsignedLessThan(start, end)) {
            write8(start, (byte) value);
        }
    }

    @NoThrow
    public static boolean isDynamicInstance(Object instance) {
        return unsignedGreaterThanEqual(castToPtr(instance), getAllocationStart());
    }

    /**
     * returns the class index for the given instance
     */
    @NoThrow
    @WASM(code = "i32.load i32.const 16777215 i32.and")
    public static native int readClassIdImpl(Object addr);

    /**
     * returns the class index for the given instance
     */
    @NoThrow
    @Alias(names = "readClass")
    public static int readClassId(Object instance) {
        return readClassIdImpl(instance);
    }

    @NoThrow
    @WASM(code = "i32.add")
    private static native Pointer addr(Object p, int offset);

    @NoThrow
    public static void writeI8AtOffset(Object instance, int offset, byte value) {
        write8(addr(instance, offset), value);
    }

    @NoThrow
    public static void writeI16AtOffset(Object instance, int offset, short value) {
        write16(addr(instance, offset), value);
    }

    @NoThrow
    public static void writeI16AtOffset(Object instance, int offset, char value) {
        write16(addr(instance, offset), value);
    }

    @NoThrow
    public static byte readI8AtOffset(Object instance, int offset) {
        return read8(addr(instance, offset));
    }

    @NoThrow
    public static short readS16AtOffset(Object instance, int offset) {
        return read16s(addr(instance, offset));
    }

    @NoThrow
    public static char readU16AtOffset(Object instance, int offset) {
        return read16u(addr(instance, offset));
    }

    @NoThrow
    public static float readF32AtOffset(Object instance, int offset) {
        return read32f(addr(instance, offset));
    }

    @NoThrow
    public static double readF64AtOffset(Object instance, int offset) {
        return read64f(addr(instance, offset));
    }

    @NoThrow
    public static int readI32AtOffset(Object instance, int offset) {
        // todo offset must become long for ptrSize=8
        return read32(addr(instance, offset));
    }

    @NoThrow
    public static long readI64AtOffset(Object instance, int offset) {
        return read64(addr(instance, offset));
    }

    @NoThrow
    public static <V> V readPtrAtOffset(Object instance, int offset) {
        return ptrTo(readI32AtOffset(instance, offset));
    }

    @NoThrow
    public static void writeI32AtOffset(Object instance, int offset, int value) {
        write32(addr(instance, offset), value);
    }

    @NoThrow
    public static void writeI64AtOffset(Object instance, int offset, long value) {
        write64(addr(instance, offset), value);
    }

    @NoThrow
    public static void writeF32AtOffset(Object instance, int offset, float value) {
        write32(addr(instance, offset), value);
    }

    @NoThrow
    public static void writeF64AtOffset(Object instance, int offset, double value) {
        write64(addr(instance, offset), value);
    }

    @NoThrow
    public static void writePtrAtOffset(Object instance, int offset, Object value) {
        // only used by reflections, so this IF is fine
        long value1 = getAddrS(value);
        if (is32Bits) {
            writeI32AtOffset(instance, offset, (int) value1);
        } else {
            writeI64AtOffset(instance, offset, value1);
        }
    }

    @NoThrow
    @WASM(code = "i32.lt_u")
    public static native boolean unsignedLessThanI(Object a, Object b);

    @NoThrow
    @Alias(names = "getClassIdPtr")
    public static <V> Object getClassIdPtr(int classId) {
        // todo this won't be supported in a JavaScript implementation...
        validateClassId(classId);
        int classIdPtr = classIdToInstancePtr(classId) + OFFSET_CLASS_INDEX;

        int actualIndex = read32(classIdPtr);
        if (actualIndex != classId) {
            log("addr = {} * {}", classId, getClassSize());
            log("+ {} + {}", getClassInstanceTable(), OFFSET_CLASS_INDEX);
            throwJs("Expected {} at {}, got {}", classId, classIdPtr, actualIndex);
        }
        return ptrTo(classIdPtr);
    }
}
