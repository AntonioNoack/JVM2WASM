package jvm;

import annotations.*;
import jvm.lang.JavaLangAccessImpl;
import sun.misc.SharedSecrets;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static jvm.ArrayAccessSafe.arrayStore;
import static jvm.GarbageCollector.largestGaps;
import static jvm.JVMShared.*;
import static jvm.JVMValues.emptyArray;
import static jvm.JavaLang.*;
import static jvm.NativeLog.log;

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
    public static int findClass(int classIdx) {
        return findClass2(classIdx);
    }

    @NoThrow
    @Alias(names = "findClass2")
    @WASM(code = "global.get $classSize i32.mul global.get $classInstanceTable i32.add")
    public static native int findClass2(int idx);

    @NoThrow
    @Alias(names = "findStatic")
    public static int findStatic(int clazz, int offset) {
        // log("finding static", clazz, offset);
        return read32(staticInstancesOffset() + (clazz << 2)) + offset;
    }

    // instance, class -> instance, error
    @Alias(names = "checkCast")
    public static int checkCast(int instance, int classId) {
        if (instance == 0) return 0;
        if (!instanceOf(instance, classId)) {
            failCastCheck(instance, classId);
        }
        return instance;
    }

    @Alias(names = "checkCastExact")
    public static int checkCastExact(int instance, int clazz) {
        if (instance == 0) return 0;
        if (!instanceOfExact(instance, clazz)) {
            failCastCheck(instance, clazz);
        }
        return instance;
    }

    private static void failCastCheck(int instance, int clazz) {
        Class<Object> isClass = ptrTo(findClass(readClass(instance)));
        Class<Object> checkClass = ptrTo(findClass(clazz));
        log(isClass.getName(), "is not instance of", checkClass.getName(), instance);
        throw new ClassCastException();
    }

    // can be removed in the future, just good for debugging
    @Alias(names = "debug")
    @SuppressWarnings("rawtypes")
    public static void debug(Object instance, boolean staticToo) throws IllegalAccessException {
        if (instance == null) {
            log("null");
        } else {
            Class clazz = instance.getClass();
            log("Class", clazz.getName());
            Field[] fields = clazz.getFields();
            //noinspection ConstantValue
            if (fields != null) for (Field f : fields) {
                if (f == null) continue;
                if (staticToo || !Modifier.isStatic(f.getModifiers())) {
                    debugField(instance, staticToo, f);
                }
            }
            if (instance instanceof Object[]) {
                debugArray(instance);
            }
        }
    }

    public static void debugField(Object instance, boolean staticToo, Field f) throws IllegalAccessException {
        String name = f.getName();
        String type = f.getType().getName();
        switch (type) {
            case "byte":
                log(type, name, f.getByte(instance));
                break;
            case "short":
                log(type, name, f.getShort(instance));
                break;
            case "char":
                log(type, name, f.getChar(instance));
                break;
            case "int":
                log(type, name, f.getInt(instance));
                break;
            case "long":
                log(type, name, f.getLong(instance));
                break;
            case "float":
                log(type, name, f.getFloat(instance));
                break;
            case "double":
                log(type, name, f.getDouble(instance));
                break;
            case "boolean":
                log(type, name, f.getBoolean(instance));
                break;
            default:
                Object value = f.get(instance);
                if (value == null) log(type, name, 0);
                else if (value instanceof String) log(type, name, getAddr(value), value.toString());
                else log(type, name, getAddr(value), value.getClass().getName());
                break;
        }
    }

    @NoThrow
    @JavaScript(code = "let lib=window.lib,len=Math.min(lib.r32(arg0+objectOverhead),100),arr=[];\n" +
            "for(let i=0;i<len;i++) arr.push(lib.r32(arg0+arrayOverhead+(i<<2)));\n" +
            "console.log(arr)")
    private static native void debugArray(Object instance);

    @NoThrow
    @WASM(code = "global.get $resolveIndirectTable")
    public static native int resolveIndirectTable();

    @NoThrow
    @WASM(code = "global.get $numClasses")
    public static native int numClasses();

    @Alias(names = "resolveIndirect")
    public static int resolveIndirect(int instance, int signatureId) {
        if (instance == 0) {
            throw new NullPointerException("Instance for resolveIndirect is null");
        }
        return resolveIndirectByClass(readClass(instance), signatureId);
    }

    @Alias(names = "resolveIndirectFail")
    public static void resolveIndirectFail(int instance, String methodName) {
        throwJs("Resolving non constructable method", instance, methodName);
    }

    // todo mark some static fields as not needing <clinit>
    // private static int riLastClass, riLastMethod, riLastImpl;

    @NoThrow
    @WASM(code = "" +
            "  local.get 0 i32.const 2 i32.shl\n" + // clazz << 2
            "  global.get $resolveIndirectTable i32.add i32.load\n" + // memory[resolveIndirectTable + clazz << 2]
            "  local.get 1 i32.add i32.load") // memory[signatureId + memory[resolveIndirectTable + clazz << 2]]
    private static native int resolveIndirectByClassUnsafe(int clazz, int signatureId);

    @NoThrow
    public static int resolveIndirectByClass(int clazzIdx, int methodPtr) {
        // unsafe memory -> from ~8ms/frame to ~6.2ms/frame
        // log("resolveIndirect", clazz, methodPtr);
        int x = resolveIndirectByClassUnsafe(clazzIdx, methodPtr);
        if (x < 0) {
            Class<Object> class1 = ptrTo(findClass(clazzIdx));
            log("resolveIndirectByClass", class1.getName());
            throwJs("classIndex, methodPtr, resolved:", clazzIdx, methodPtr, x);
        }
        return x;
    }

    @NoThrow
    public static int getDynamicTableSize(int classIdx) {
        return resolveIndirectByClass(classIdx, 0);
    }

    @NoThrow
    @WASM(code = "unreachable")
    public static native void crash();

    @NoThrow
    public static void throwJs() {
        log("Internal VM error!");
        crash();
    }

    @NoThrow
    public static void throwJs(String s) {
        log(s);
        crash();
    }

    @NoThrow
    public static void throwJs(String s, int a) {
        log(s, a);
        crash();
    }

    @NoThrow
    public static void throwJs(String s, int a, int b) {
        log(s, a, b);
        crash();
    }

    @NoThrow
    public static void throwJs(String s, int a, String b) {
        log(s, a, b);
        crash();
    }

    @NoThrow
    public static void throwJs(String s, int a, int b, int c) {
        log(s, a, b, c);
        crash();
    }

    @NoThrow
    public static void throwJs(int s) {
        log(s);
        crash();
    }

    @NoThrow
    @Alias(names = "panic")
    private static void panic(Object throwable) {
        if (throwable != null) {
            throwJs(getAddr(throwable));
        }
    }

    public static int resolveInterfaceByClass(int clazz, int methodId) {
        // log("resolve2", clazz, methodId);
        // log("class:", clazz);
        validateClassIdx(clazz);
        int tablePtr = getInheritanceTableEntry(clazz);
        // log("tablePtr", tablePtr);
        if (tablePtr == 0) {
            log("No class table entry was found!", clazz);
            log("method:", methodId);
            throwJs("No class table entry was found!");
        }
        int numInterfaces = read32(tablePtr + 8);
        /*log("#interfaces:", numInterfaces);
        for (int i = 0; i < numInterfaces; i++) {
            log("interface[i]:", read32(tablePtr + 12 + i * 4));
        }*/
        tablePtr += 12 + (numInterfaces << 2); // 12 for super class + instance size + numInterfaces
        int tableLength = read32(tablePtr);
        tablePtr += 4; // table length

        int min = 0;
        int max = tableLength - 1;

        // log("resolveInterface", clazz, methodId, tableLength);
        int id = max - min < 16 ?
                searchInterfaceLinear(min, max, methodId, tablePtr, tableLength) :
                searchInterfaceBinary(min, max, methodId, tablePtr, tableLength);

        if (id == -1) {
            // return -1 - min
            reportNotFound(clazz, methodId, tableLength, tablePtr);
        }

        return id;
    }

    private static int searchInterfaceBinary(int min, int max, int methodId, int tablePtr, int tableLength) {
        while (max >= min) {
            int mid = (min + max) >> 1;
            int addr = tablePtr + (mid << 3);
            int cmp = read32(addr) - methodId;
            if (cmp == 0) {
                return read32(addr + 4);
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

    private static int searchInterfaceLinear(int min, int max, int methodId, int tablePtr, int tableLength) {
        max = (max << 3) + tablePtr;
        min = (min << 3) + tablePtr;
        while (max >= min) {
            if (read32(min) == methodId) {
                return read32(min + 4);
            }
            min += 8;
        }
        return -1;
    }

    private static void reportNotFound(int clazz, int methodId, int tableLength, int tablePtr) {
        // return -1 - min
        log("Method could not be found", clazz);
        log("  id, length:", methodId, tableLength);
        for (int i = 0; i < tableLength; i++) {
            int addr = tablePtr + (i << 3);
            log("  ", read32(addr), read32(addr + 4));
        }
        throwJs("Method could not be found");
    }

    @Alias(names = "resolveInterface")
    public static int resolveInterface(int instance, int methodId) {
        if (instance == 0) {
            throw new NullPointerException("Instance for resolveInterface is null");
        } else {
            return resolveInterfaceByClass(readClass(instance), methodId);
        }
    }

    @Export
    @NoThrow
    @Alias(names = "instanceOf")
    public static boolean instanceOf(int instance, int classId) {
        // log("instanceOf", instance, clazz);
        if (instance == 0) return false;
        if (classId == 0) return true;
        // checkAddress(instance);
        int testedClass = readClass(instance);
        return isChildOrSameClass(testedClass, classId);
    }


    @Export
    @NoThrow
    @Alias(names = "instanceOfNonInterface")
    public static boolean instanceOfNonInterface(int instance, int clazz) {
        // log("instanceOf", instance, clazz);
        if (instance == 0) return false;
        if (clazz == 0) return true;
        // checkAddress(instance);
        int testedClass = readClass(instance);
        return isChildOrSameClassNonInterface(testedClass, clazz);
    }

    @NoThrow
    @Alias(names = "instanceOfExact")
    public static boolean instanceOfExact(int instance, int clazz) {
        return (instance != 0) & (readClass(instance) == clazz);
    }

    @NoThrow
    private static void checkAddress(int instance) {
        if (ge_ub(instance, getAllocatedSize())) {
            throwJs("Not a valid address!", instance);
        }
    }

    @NoThrow
    private static int getInheritanceTableEntry(int classId) {
        return read32(inheritanceTable() + (classId << 2));
    }

    @NoThrow
    private static int getSuperClassIdFromInheritanceTableEntry(int tableAddress) {
        return read32(tableAddress);
    }

    @NoThrow
    public static boolean isChildOrSameClass(int childClassIdx, int parentClassIdx) {
        while (true) {
            if (childClassIdx == parentClassIdx) return true;
            // find super classes in table
            validateClassIdx(childClassIdx);
            int tableAddress = getInheritanceTableEntry(childClassIdx);
            // we can return here if childClassIdx = 0, because java/lang/Object has no interfaces
            if (tableAddress == 0 || childClassIdx == 0) return false;
            // check for interfaces
            if (isInInterfaceTable(tableAddress, parentClassIdx)) return true;
            childClassIdx = getSuperClassIdFromInheritanceTableEntry(tableAddress); // switch to parent class
        }
    }

    @NoThrow
    public static boolean isChildOrSameClassNonInterface(int childClassIdx, int parentClassIdx) {
        while (true) {
            if (childClassIdx == parentClassIdx) return true;
            validateClassIdx(childClassIdx);
            int tableAddress = getInheritanceTableEntry(childClassIdx);
            // we can return here if childClassIdx = 0, because java/lang/Object has no interfaces
            if (tableAddress == 0 || childClassIdx == 0) return false;
            childClassIdx = read32(tableAddress); // switch to parent class
        }
    }

    @NoThrow
    static void validateClassIdx(int childClassIdx) {
        if (ge_ub(childClassIdx, numClasses())) {
            log("class index out of bounds", childClassIdx, numClasses());
            throwJs();
        }
    }

    @NoThrow
    public static boolean isInInterfaceTable(int tableAddress, int interfaceClassIdx) {
        // handle interfaces
        int length = read32(tableAddress + 8);
        tableAddress += 12;
        for (int i = 0; i < length; i++) {
            if (read32(tableAddress) == interfaceClassIdx)
                return true;
            tableAddress += 4;
        }
        return false;
    }

    @NoThrow
    public static int getSuperClass(int classIdx) {
        int tableAddress = getInheritanceTableEntry(classIdx);
        if (tableAddress == 0) return 0;
        return getSuperClassIdFromInheritanceTableEntry(tableAddress);
    }

    @NoThrow
    public static int getInstanceSize(int classIndex) {
        // look up class table for size
        if (classIndex == 0) return objectOverhead;
        if (isNativeArray(classIndex)) return arrayOverhead;
        int tableAddress = getInheritanceTableEntry(classIndex);
        return read32(tableAddress + 4);
    }

    @NoThrow
    private static boolean isNativeArray(int classIndex) {
        return classIndex >= 17 && classIndex < 25;
    }

    @Alias(names = "createInstance")
    public static int createInstance(int clazz) {
        validateClassIdx(clazz);
        if (trackAllocations) trackCalloc(clazz);
        int instanceSize = getInstanceSize(clazz);
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

    @NoThrow
    public static int getTypeShiftUnsafe(int clazz) {
        // 0 1 2 3 4 5 6 7 8 9
        // 2 2 2 2 0 0 1 1 3 3
        int flag0 = ge_ui(clazz, 4) & lt(clazz, 6);
        int flag1 = ge_ui(clazz, 8);
        return 2 - flag0 - flag0 - ge_ui(clazz, 6) + flag1 + flag1;
    }

    @NoThrow
    @WASM(code = "i32.ge_u")
    private static native int ge_ui(int a, int b);

    @NoThrow
    @WASM(code = "i32.ge_u")
    private static native boolean ge_ub(int a, int b);

    @NoThrow
    @WASM(code = "i32.lt_u")
    private static native int lt(int a, int b);

    public static int getTypeShift(int clazz) {
        if (clazz < 0 || clazz > 9) throw new IllegalArgumentException();
        return getTypeShiftUnsafe(clazz);
    }

    @NoThrow
    @WASM(code = "") // automatically done
    public static native int b2i(boolean flag);

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
        return ((size + 3) >>> 2) << 2;
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
        if (readClass(instance) != clazz) throwJs("Incorrect clazz!", instance, readClass(instance), clazz);
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

    /*@NoThrow
    @WASM(code = "v128.const i64x2 0 0 v128.store")
    public static native void clear128(int addr);*/


    /**
     * returns the class index for the given instance
     */
    @NoThrow
    @Alias(names = "readClass")
    public static int readClass1(int addr) {
        return readClass(addr);
    }

    /**
     * returns the class index for the given instance
     */
    @NoThrow
    @WASM(code = "i32.load i32.const 16777215 i32.and")
    public static native int readClass(int addr);

    @NoThrow
    static void printStackTraceLine(int idx) {
        int lookupBasePtr = getStackTraceTablePtr();
        if (lookupBasePtr <= 0) return;
        int throwableLookup = lookupBasePtr + idx * 12;
        String className = ptrTo(read32(throwableLookup));
        String methodName = ptrTo(read32(throwableLookup + 4));
        int line = read32(throwableLookup + 8);
        printStackTraceLine(getStackDepth(), className, methodName, line);
    }

    @NoThrow
    @JavaScript(code = "console.log('  '.repeat(arg0) + str(arg1) + '.' + str(arg2) + ':' + arg3)")
    private static native void printStackTraceLine(int depth, String clazz, String method, int line);

    /**
     * Pseudo-Instance, which can be used to avoid allocations,
     * when no fields of a type are used; only used for lambdas, because
     * identity checks might cause trouble otherwise
     */
    @NoThrow
    @Alias(names = "getClassIndexPtr")
    public static <V> int getClassIndexPtr(int classIndex) {
        return findClass(classIndex) + objectOverhead + 12;
    }

}
