package jvm;

import annotations.Alias;
import annotations.JavaScript;
import annotations.NoThrow;
import annotations.WASM;
import jvm.lang.JavaLangAccessImpl;
import sun.misc.SharedSecrets;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static jvm.GC.largestGaps;
import static jvm.JVMValues.*;
import static jvm.JavaLang.getAddr;
import static jvm.JavaLang.ptrTo;

@SuppressWarnings("unused")
public class JVM32 {

    // todo objectOverhead isn't working yet with = 4 -> where is the error?
    public static int objectOverhead = 4;// 3x class, 1x GC
    public static int arrayOverhead = objectOverhead + 4;// length
    public static final int ptrSize = 4;
    public static boolean trackAllocations = true;

    // public static int fieldTableOffset = getFieldTableOffset(); // for GC

    @NoThrow
    @WASM(code = "global.get $c")
    public static native int inheritanceTable();

    @NoThrow
    @Alias(name = "oo")
    public static int getObjectOverhead() {
        return objectOverhead;
    }

    @NoThrow
    @WASM(code = "global.get $s")
    public static native int staticInstancesOffset();

    @NoThrow
    @Alias(name = "findClass")
    public static int findClass(int idx) {
        return findClass2(idx);
    }

    @NoThrow
    @Alias(name = "findClass2")
    @WASM(code = "global.get $YS i32.mul global.get $Y i32.add")
    public static native int findClass2(int idx);

    @NoThrow
    @Alias(name = "findStatic")
    public static int findStatic(int clazz, int offset) {
        // log("finding static", clazz, offset);
        return read32(staticInstancesOffset() + (clazz << 2)) + offset;
    }

    @NoThrow
    @JavaScript(code = "console.log(arg0, arg1); return arg1;")
    public static native int log(int code, int v);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), arg1);")
    public static native void log(String msg, int param);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), arg1);")
    public static native void log(String msg, double param);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), arg1, arg2);")
    public static native void log(String msg, int param, int param2);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), str(arg1), str(arg2));")
    public static native void log(String msg, String param, String param2);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), str(arg1), arg2);")
    public static native void log(String msg, String param, int param2);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), str(arg1), arg2);")
    public static native void log(String msg, String param, long param2);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), str(arg1), arg2);")
    public static native void log(String msg, String param, double param2);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), str(arg1), arg2);")
    public static native void log(String msg, String param, boolean param2);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), arg1, arg2, arg3);")
    public static native void log(String msg, int param, int param2, int param3);

    @NoThrow
    @JavaScript(code = "console.log(arg0, str(arg1), str(arg2), arg3);")
    public static native void log(int i, String msg, String param, int param2);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), str(arg1), str(arg2), arg3);")
    public static native void log(String i, String msg, String param, int param2);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), arg1, str(arg2));")
    public static native void log(String msg, int param, String param2);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0));")
    public static native void log(String msg);

    @NoThrow
    @JavaScript(code = "console.log(arg0);")
    public static native void log(double v);

    @NoThrow
    @JavaScript(code = "console.log(str(arg0), str(arg1));")
    public static native void log(String msg, String param);

    @Alias(name = "cc")
    public static int checkCast(int instance, int clazz) {
        if (instance == 0) return 0;
        if (!instanceOf(instance, clazz)) {
            Class<Object> isClass = ptrTo(findClass(readClass(instance)));
            Class<Object> checkClass = ptrTo(findClass(clazz));
            log(isClass.getName(), "is not instance of", checkClass.getName(), instance);
            throw new ClassCastException();
        }
        return instance;
    }

    // can be removed in the future, just good for debugging
    @Alias(name = "debug")
    public static void debug(Object instance, boolean staticToo) throws IllegalAccessException {
        if (instance == null) {
            log("null");
        } else {
            Class clazz = instance.getClass();
            log("Class", clazz.getName());
            Field[] fields = clazz.getFields();
            if (fields != null) for (Field f : fields) {
                if (f == null) continue;
                if (staticToo || !Modifier.isStatic(f.getModifiers())) {
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
                            log(type, name, getAddr(f.get(instance)));
                            break;
                    }
                }
            }
        }
    }

    @NoThrow
    @WASM(code = "global.get $M")
    public static native int indirectTable();

    @NoThrow
    @WASM(code = "global.get $X")
    public static native int numClasses();

    @Alias(name = "resolveIndirect")
    public static int resolveIndirect(int instance, int methodPtr) {
        if (instance == 0) {
            log("instance for call is null", methodPtr);
            // throw new NullPointerException("instance for call is null");
            throwJs();
        }
        invokeBlackMagic();
        return resolveIndirectByClass(readClass(instance), methodPtr);
    }

    // todo mark some static fields as not needing <clinit>
    // private static int riLastClass, riLastMethod, riLastImpl;

    @NoThrow
    @WASM(code = "global.get $M\n" +
            "  local.get 0\n" +
            "  i32.const 2\n" +
            "  i32.shl\n" +
            "  i32.add\n" +
            "  i32.load\n" +
            "  local.get 1\n" +
            "  i32.add\n" +
            "  i32.load")
    private static native int resolveIndirectByClassUnsafe(int clazz, int methodPtr);

    @NoThrow
    private static int resolveIndirectByClass(int clazz, int methodPtr) {
        // unsafe memory -> from ~8ms/frame to ~6.2ms/frame
        int x = resolveIndirectByClassUnsafe(clazz, methodPtr);
        if (x < 0) {
            log("ribc", clazz, methodPtr, x);
            throwJs();
        }
        return x;
        /* log("resolve", clazz, methodIndex);
        // log("resolveIndirect", clazz);
        // log("resolveIndirect-table0", indirectTable());
        // fast path for loops
        if (clazz == riLastClass && methodPtr == riLastMethod) return riLastImpl;
        if (ge_ub(clazz, numClasses())) {
            log("Memory corruption!", clazz, numClasses());
            throw new MemoryCorruption();
        }
        int methodTable = read32(indirectTable() + (clazz << 2));
        // log("resolveIndirect-table", methodTable);
        if (methodTable == 0) {
            log("Illegal class:", clazz);
            throw new IllegalStateException("Class is not constructable");
        }
        int tableLength = read32(methodTable);
        if (methodPtr <= 0 || methodPtr > tableLength) {
            log("Class:", clazz);
            log("Illegal index, length:", methodPtr, tableLength);
            throwJs();
        }
        int impl = read32(methodTable + methodPtr);
        if (impl < 0) {
            log("resolveIndirect-class", clazz);
            log("resolveIndirect-table0", indirectTable());
            log("resolveIndirect-table", methodTable);
            log("resolveIndirect-methodIdx", methodPtr);
            throw new AbstractMethodError();
        }
        riLastClass = clazz;
        riLastMethod = methodPtr;
        riLastImpl = impl;
        return impl;*/
    }

    @NoThrow
    @JavaScript(code = "throw 'Internal VM error!'")
    public static native void throwJs();

    @NoThrow
    @JavaScript(code = "throw str(arg0)")
    public static native void throwJs(String s);

    @NoThrow
    @JavaScript(code = "throw [str(arg0),arg1]")
    public static native void throwJs(String s, int a);

    @NoThrow
    @JavaScript(code = "throw [str(arg0),arg1,arg2]")
    public static native void throwJs(String s, int a, int b);

    @NoThrow
    @JavaScript(code = "throw [str(arg0),arg1,arg2,arg3]")
    public static native void throwJs(String s, int a, int b, int c);

    @NoThrow
    @JavaScript(code = "throw arg0")
    public static native void throwJs(int s);

    @NoThrow
    @Alias(name = "panic")
    private static void panic(Object throwable) {
        if (throwable != null) {
            throwJs(getAddr(throwable));
        }
    }

    public static int resolveInterfaceByClass(int clazz, int methodId) {
        // log("resolve2", clazz, methodId);
        // log("class:", clazz);
        if (clazz < 0 || clazz >= numClasses()) throw new IllegalStateException("Class index out of bounds");
        int tablePtr = read32(inheritanceTable() + (clazz << 2));
        // log("tablePtr", tablePtr);
        if (tablePtr == 0) {
            log("No class table entry was found!", clazz);
            log("method:", methodId);
            throw new RuntimeException("No class table entry was found!");
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

        while (max >= min) {
            int mid = (min + max) >> 1;
            int addr = tablePtr + (mid << 3);
            int cmp = read32(addr) - methodId;
            if (cmp == 0) {
                // log("resolved interface", clazz, methodId);
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

        // return -1 - min
        log("Method could not been found", clazz);
        log("method, length:", methodId, tableLength);
        for (int i = 0; i < tableLength; i++) {
            int addr = tablePtr + (i << 3);
            log("method[i]", read32(addr), read32(addr + 4));
        }
        throwJs("Method could not been found");
        return -1;
    }

    @Alias(name = "resolveInterface")
    public static int resolveInterface(int instance, int methodId) {
        if (instance == 0) {
            throwJs("resolveInterface is null", instance, methodId);
        }
        // beware! ++ black magic ++
        invokeBlackMagic();
        // normal code
        int clazz = readClass(instance);
        return resolveInterfaceByClass(clazz, methodId);
    }

    @NoThrow
    private static void invokeBlackMagic() {
    }

    @NoThrow
    @Alias(name = "io")
    public static boolean instanceOf(int instance, int clazz) {
        // log("io", instance, clazz);
        if (instance == 0) return false;
        if (clazz == 0) return true;
        // checkAddress(instance);
        int testedClass = readClass(instance);
        if (ge_ub(testedClass, numClasses())) {
            log("class index out of bounds", testedClass, numClasses());
            throwJs();
        }
        return instanceOfByClass(testedClass, clazz);
    }

    @NoThrow
    private static void checkAddress(int instance) {
        if (ge_ub(instance, getAllocatedSize())) {
            throwJs("Not a valid address!", instance);
        }
    }

    @NoThrow
    @Alias(name = "instanceOfByClass")
    public static boolean instanceOfByClass(int instanceClass, int clazz) {
        if (instanceClass == clazz) return true;
        // find super classes in table
        // log("instanceOf", instanceClass, clazz);
        if (ge_ub(instanceClass, numClasses())) {
            log("class index out of bounds", instanceClass, numClasses());
            throwJs();
        }
        int tableAddress = read32(inheritanceTable() + (instanceClass << 2));
        // log("testing class, ptr", testedClass, tableAddress);
        if (tableAddress == 0) return false;
        int instanceSuperClass = read32(tableAddress);
        if (instanceOfByClass(instanceSuperClass, clazz)) return true;
        // handle interfaces
        int length = read32(tableAddress + 8);
        tableAddress += 12;
        for (int i = 0; i < length; i++) {
            if (read32(tableAddress) == clazz)
                return true;
            tableAddress += 4;
        }
        return false;
    }

    @NoThrow
    public static int getSuperClass(int instanceClass) {
        int tableAddress = read32(inheritanceTable() + (instanceClass << 2));
        if (tableAddress == 0) return 0;
        return read32(tableAddress);
    }

    @Alias(name = "gis")
    public static int getInstanceSize(int clazz) {
        // look up class table for size
        if (clazz == 0 || (clazz >= 17 && clazz < 25)) return arrayOverhead;
        int tableAddress = read32(inheritanceTable() + (clazz << 2));
        return read32(tableAddress + 4);
    }

    @Alias(name = "cr")
    public static int create(int clazz) {
        // log("creating", clazz);
        if (ge_ub(clazz, numClasses())) {
            log("class index out of bounds", clazz, numClasses());
            throwJs();
        }
        // this probably can be removed, as we never used it
        int methodTable = read32(indirectTable() + (clazz << 2));
        if (methodTable == 0) {
            log("class to create:", clazz);
            throwJs("Class cannot be created");
        }
        if (trackAllocations) trackCalloc(clazz);
        int instanceSize = getInstanceSize(clazz);
        if (instanceSize <= 0)
            throw new IllegalStateException("Non-constructable/abstract class cannot be instantiated");
        int newInstance = calloc(instanceSize);

        /*if (instanceSize > 1024) {
            log("Allocating", clazz, instanceSize, newInstance);
            printStackTrace();
        }*/

        write32(newInstance, clazz | (GC.iteration << 24));
        return newInstance;
    }

    @NoThrow
    @JavaScript(code = "calloc[arg0] = (calloc[arg0]||0)+1")
    private static native void trackCalloc(int clazz);

    @Alias(name = "ca")
    public static int createArray(int length) {
        // probably a bit illegal; should be fine for us, saving allocations :)
        if (length == 0) {
            Object sth = emptyArray;
            if (sth != null) return getAddr(sth);
            // else awkward, probably recursive trap
        }
        // log("creating array", length);
        return createNativeArray(length, 1);
    }

    @NoThrow
    public static int getTypeShiftNoThrow(int clazz) {
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
        return getTypeShiftNoThrow(clazz);
    }

    @NoThrow
    @WASM(code = "") // automatically done
    public static native int b2i(boolean flag);

    @Alias(name = "cna")
    public static int createNativeArray(int length, int clazz) {
        // log("creating native array", length, clazz);
        if (length < 0) throw new IllegalArgumentException();
        int typeShift = getTypeShiftNoThrow(clazz);
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
        write32(newInstance, clazz); // [] has index 1
        write32(newInstance + objectOverhead, length); // array length
        // log("instance:", newInstance);
        return newInstance;
    }

    @Alias(name = "cma2")
    public static int createMultiArray(int l0, int l1, int clazz) {
        if (l0 == 0) return 0;
        int array = createNativeArray(l0, 1);
        for (int i = 0; i < l0; i++) {
            arrayStore(array, i, createNativeArray(l1, clazz));
        }
        return array;
    }

    @Alias(name = "cma3")
    public static int createMultiArray(int l0, int l1, int l2, int clazz) {
        if (l0 == 0) return 0;
        int array = createNativeArray(l0, 1);
        for (int i = 0; i < l0; i++) {
            arrayStore(array, i, createMultiArray(l1, l2, clazz));
        }
        return array;
    }

    @Alias(name = "cma4")
    public static int createMultiArray(int l0, int l1, int l2, int l3, int clazz) {
        if (l0 == 0) return 0;
        int array = createNativeArray(l0, 1);
        for (int i = 0; i < l0; i++) {
            arrayStore(array, i, createMultiArray(l1, l2, l3, clazz));
        }
        return array;
    }

    @Alias(name = "cma5")
    public static int createMultiArray(int l0, int l1, int l2, int l3, int l4, int clazz) {
        if (l0 == 0) return 0;
        int array = createNativeArray(l0, 1);
        for (int i = 0; i < l0; i++) {
            arrayStore(array, i, createMultiArray(l1, l2, l3, l4, clazz));
        }
        return array;
    }

    @Alias(name = "cma6")
    public static int createMultiArray(int l0, int l1, int l2, int l3, int l4, int l5, int clazz) {
        if (l0 == 0) return 0;
        int array = createNativeArray(l0, 1);
        for (int i = 0; i < l0; i++) {
            arrayStore(array, i, createMultiArray(l1, l2, l3, l4, l5, clazz));
        }
        return array;
    }

    @NoThrow
    @WASM(code = "global.get $G0")
    public static native int getAllocationStart();

    @NoThrow
    @WASM(code = "global.get $G")
    public static native int getNextPtr();

    @NoThrow
    @Alias(name = "qa")
    public static int queryAllocated() {
        return getNextPtr();
    }

    @NoThrow
    @Alias(name = "qg")
    public static int queryGCed() {
        int sum = 0;
        int[] data = largestGaps;
        for (int i = 0, l = data.length; i < l; i += 2) {
            sum += data[i];
        }
        return sum;
    }

    @NoThrow
    @WASM(code = "global.set $G")
    public static native void setNextPtr(int value);

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

    private static int allocateNewSpace(int size) {
        int ptr = getNextPtr();
        // log("allocated", ptr, size);

        // clean memory
        int endPtr = ptr + size;

        // if ptr + size has a memory overflow over 0, throw OOM
        if (unsignedLessThan(endPtr, ptr)) {
            log("Memory overflow, size is probably too large", ptr, size);
            throw reachedMemoryLimit;
        }

        // check if we have enough size
        int allocatedSize = getAllocatedSize() - (b2i(!criticalAlloc) << 16);
        if (unsignedLessThan(allocatedSize, endPtr)) {

            // if not, call grow()
            // how much do we want to grow?
            int allocatedPages = allocatedSize >>> 16;
            // once this limit has been hit, only throw once
            int maxNumPages = 65536;// 128 ~ 16 MB; 4 GB = 65536;
            int remainingPages = maxNumPages - allocatedPages;
            int amountToGrow = allocatedPages >> 1;
            int minPagesToGrow = (endPtr >>> 16) + 1 - allocatedPages;
            if (amountToGrow > remainingPages) amountToGrow = remainingPages;
            // should be caught by ptr<0 && endPtr > 0
            if (minPagesToGrow > remainingPages) {
                log("Cannot grow enough", minPagesToGrow, remainingPages);
                throw reachedMemoryLimit;
            }
            if (amountToGrow < minPagesToGrow) amountToGrow = minPagesToGrow;
            if (!grow(amountToGrow)) {
                // if grow() fails, throw OOM error
                log("grow() failed", amountToGrow);
                throw failedToAllocateMemory;
            } else {
                // log("Grew", minPagesToGrow, amountToGrow);
                if (reachedMemoryLimit == null) log("Mmh, awkward");
            }
        }

        // prevent the very first allocations from overlapping
        ptr = getNextPtr();
        setNextPtr(ptr + size);
        return ptr;
    }

    @Alias(name = "_c")
    public static int calloc(int size) {

        // log("calloc", size);
        size = adjustCallocSize(size);

        int ptr;

        // enough space for the first allocations
        if (!GCX.isInited) {
            ptr = allocateNewSpace(size);
        } else {
            // try to find a freed place in GC first
            ptr = GC.findGap(size);
            if (ptr == 0) ptr = allocateNewSpace(size);
        }

        int endPtr = ptr + size;
        fill64(ptr, endPtr, 0);
        return ptr;
    }

    public static void fill8(int start, int end, byte value) {
        fill16(start, end, (short) ((((int) value) << 8) | value));
    }

    public static void fill16(int start, int end, short value) {
        fill32(start, end, (((int) value) << 16) | value);
    }

    public static void fill32(int start, int end, int value) {
        fill64(start, end, (((long) value) << 32) | value);
    }

    @NoThrow
    public static int findDiscrepancy(int start, int end, int start2) {
        // todo align memory?
        int end8 = end - 7;
        while (unsignedLessThan(start, end8)) {
            if (read64(start) != read64(start2)) {
                break;
            }
            start += 8;
            start2 += 8;
        }
        if (unsignedLessThan(start, end - 3)) {
            if (read32(start) == read32(start2)) {
                start += 4;
                start2 += 4;
            }
        }
        if (unsignedLessThan(start, end - 1)) {
            if (read16s(start) == read16s(start2)) {
                start += 2;
                start2 += 2;
            }
        }
        if (unsignedLessThan(start, end)) {
            if (read8(start) == read8(start2)) {
                start++;
                // start2++;
            }
        }
        return start;
    }

    @NoThrow
    public static void fill64(int start, int end, long value) {

        // benchmark:
        // var t0 = window.performance.now()
        // for(var i=0;i<100000;i++) instance.exports._c(65536)
        // var t1 = window.performance.now()

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

    @Alias(name = "al")
    public static int arrayLength(int instance) {
        if (instance == 0) throw new NullPointerException("[].length");
        return read32(instance + objectOverhead);
    }

    @Alias(name = "isOOB")
    public static void checkOutOfBounds(int instance, int index) {
        if (instance == 0) throw new NullPointerException("isOOB");
        // checkAddress(instance);
        int length = read32(instance + objectOverhead);
        if (ge_ub(index, length)) {
            throw new IndexOutOfBoundsException();
        }
    }

    @Alias(name = "isOOB2")
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
    @Alias(name = "f2i")
    public static int f2i(float v) {
        if (v < -2147483648f) return Integer.MIN_VALUE;
        if (v > 2147483647f) return Integer.MAX_VALUE;
        if (Float.isNaN(v)) return 0;
        return _f2i(v);
    }

    @NoThrow
    @WASM(code = "i32.trunc_f32_s")
    public static native int _f2i(float v);

    @NoThrow
    @Alias(name = "f2l")
    public static long f2l(float v) {
        if (v < -9223372036854775808f) return Long.MIN_VALUE;
        if (v > 9223372036854775807f) return Long.MAX_VALUE;
        if (Float.isNaN(v)) return 0L;
        return _f2l(v);
    }

    @NoThrow
    @WASM(code = "i64.trunc_f32_s")
    public static native long _f2l(float v);

    @NoThrow
    @Alias(name = "d2i")
    public static int d2i(double v) {
        if (v < -2147483648.0) return Integer.MIN_VALUE;
        if (v > 2147483647.0) return Integer.MAX_VALUE;
        if (Double.isNaN(v)) return 0;
        return _d2i(v);
    }

    @NoThrow
    @WASM(code = "i32.trunc_f64_s")
    public static native int _d2i(double v);

    @NoThrow
    @Alias(name = "d2l")
    public static long d2l(double v) {
        if (v < -9223372036854775808.0) return Long.MIN_VALUE;
        if (v > 9223372036854775807.0) return Long.MAX_VALUE;
        if (Double.isNaN(v)) return 0L;
        return _d2l(v);
    }

    @NoThrow
    @WASM(code = "i64.trunc_f64_s")
    public static native long _d2l(double v);

    @Alias(name = "i64ArrayStore")
    public static void arrayStore(int instance, int index, long value) {
        checkOutOfBounds(instance, index, 8);
        write64(instance + arrayOverhead + (index << 3), value);
    }

    @Alias(name = "i32ArrayStore")
    public static void arrayStore(int instance, int index, int value) {
        checkOutOfBounds(instance, index);
        int clazz = readClass(instance);
        if (clazz != 1 && clazz != 2) throwJs("Incorrect clazz! i32", instance, clazz);
        write32(instance + arrayOverhead + (index << 2), value);
    }

    @Alias(name = "f64ArrayStore")
    public static void arrayStore(int instance, int index, double value) {
        checkOutOfBounds(instance, index, 9);
        write64(instance + arrayOverhead + (index << 3), value);
    }

    @Alias(name = "f32ArrayStore")
    public static void arrayStore(int instance, int index, float value) {
        checkOutOfBounds(instance, index, 3);
        write32(instance + arrayOverhead + (index << 2), value);
    }

    @Alias(name = "i16ArrayStore")
    public static void arrayStore(int instance, int index, short value) {
        checkOutOfBounds(instance, index);
        int clazz = readClass(instance);
        if (clazz != 6 && clazz != 7) throwJs("Incorrect clazz! i16", instance, clazz);
        write16(instance + arrayOverhead + (index << 1), value);
    }

    @Alias(name = "i8ArrayStore")
    public static void arrayStore(int instance, int index, byte value) {
        checkOutOfBounds(instance, index);
        int clazz = readClass(instance);
        if (clazz != 4 && clazz != 5) throw new ClassCastException("Incorrect clazz!");
        write8(instance + arrayOverhead + index, value);
    }

    @Alias(name = "i64ArrayLoad")
    public static long arrayLoad64(int instance, int index) {
        checkOutOfBounds(instance, index, 8);
        return read64(instance + arrayOverhead + (index << 3));
    }

    @Alias(name = "i32ArrayLoad")
    public static int arrayLoad32(int instance, int index) {
        checkOutOfBounds(instance, index);
        int clazz = readClass(instance);
        if (clazz != 1 && clazz != 2) throw new ClassCastException("Incorrect clazz!");
        return read32(instance + arrayOverhead + (index << 2));
    }

    @Alias(name = "f64ArrayLoad")
    public static double arrayLoad64f(int instance, int index) {
        checkOutOfBounds(instance, index, 9);
        return read64f(instance + arrayOverhead + (index << 3));
    }

    @Alias(name = "f32ArrayLoad")
    public static float arrayLoad32f(int instance, int index) {
        checkOutOfBounds(instance, index, 3);
        return read32f(instance + arrayOverhead + (index << 2));
    }

    @Alias(name = "u16ArrayLoad")
    public static char arrayLoad16u(int instance, int index) {
        checkOutOfBounds(instance, index, 6);
        return read16u(instance + arrayOverhead + (index << 1));
    }

    @Alias(name = "s16ArrayLoad")
    public static short arrayLoad16s(int instance, int index) {
        checkOutOfBounds(instance, index, 7);
        return read16s(instance + arrayOverhead + (index << 1));
    }

    @Alias(name = "i8ArrayLoad")
    public static byte arrayLoad8(int instance, int index) {
        checkOutOfBounds(instance, index);
        int clazz = readClass(instance);
        if (clazz != 4 && clazz != 5) throw new ClassCastException("Incorrect clazz!");
        return read8(instance + arrayOverhead + index);
    }

    @NoThrow
    @WASM(code = "v128.const i64x2 0 0 v128.store")
    public static native void clear128(int addr);

    @NoThrow
    @WASM(code = "i64.const 0 i64.store")
    public static native void clear64(int addr);

    @NoThrow
    @WASM(code = "i64.store")
    public static native void write64(int addr, long value);

    @NoThrow
    @WASM(code = "f64.store")
    public static native void write64(int addr, double value);

    @NoThrow
    @WASM(code = "i32.store")
    public static native void write32(int addr, int value);

    @NoThrow
    @WASM(code = "f32.store")
    public static native void write32(int addr, float value);

    @NoThrow
    @WASM(code = "i32.store16")
    public static native void write16(int addr, short value);

    @NoThrow
    @WASM(code = "i32.store16")
    public static native void write16(int addr, char value);

    @NoThrow
    @WASM(code = "i32.store8")
    public static native void write8(int addr, byte value);

    @NoThrow
    @WASM(code = "i64.load")
    public static native long read64(int addr);

    @NoThrow
    @WASM(code = "i32.load")
    public static native int read32(int addr);

    @NoThrow
    @WASM(code = "i32.load i32.const 16777215 i32.and")
    public static native int readClass(int addr);

    @NoThrow
    @WASM(code = "f64.load")
    public static native double read64f(int addr);

    @NoThrow
    @WASM(code = "f32.load")
    public static native float read32f(int addr);

    @NoThrow
    @WASM(code = "i32.load16_s")
    public static native short read16s(int addr);

    @NoThrow
    @WASM(code = "i32.load16_u")
    public static native char read16u(int addr);

    @NoThrow
    @WASM(code = "i32.load8_s")
    public static native byte read8(int addr);

    @NoThrow
    @WASM(code = "i32.div_s")
    public static native int div(int a, int b);

    @NoThrow
    @WASM(code = "i64.div_s")
    public static native long div(long a, long b);

    @Alias(name = "safeDiv32")
    public static int safeDiv32(int a, int b) {
        if (b == 0) throw new ArithmeticException();
        if ((a == Integer.MIN_VALUE) & (b == -1)) return Integer.MIN_VALUE;
        return div(a, b);
    }

    @Alias(name = "safeDiv64")
    public static long safeDiv64(long a, long b) {
        if (b == 0) throw new ArithmeticException();
        if ((a == Long.MIN_VALUE) & (b == -1)) return Long.MIN_VALUE;
        return div(a, b);
    }

    @Alias(name = "checkNonZero32")
    public static int checkNonZero32(int b) {
        if (b == 0) throw new ArithmeticException();
        return b;
    }

    @Alias(name = "checkNonZero64")
    public static long checkNonZero64(long b) {
        if (b == 0) throw new ArithmeticException();
        return b;
    }

    @NoThrow
    @Alias(name = "stackPush")
    public static void pushCall(int idx) {
        int stackPointer = getStackPtr() - 4;
        // else stack overflow
        // we can do different things here -> let's just keep running;
        // just the stack is no longer tracked :)
        int limit = getStackLimit();
        if (unsignedGreaterThanEqual(stackPointer, limit)) {
            write32(stackPointer, idx);
        } else if (stackPointer == limit - 4) {
            log("Warning: Exited stack space, meaning 256k recursive calls");
        }
        setStackPtr(stackPointer);
    }

    @NoThrow
    @WASM(code = "global.get $Q")
    public static native int getStackPtr();

    @NoThrow
    @WASM(code = "global.get $Q0")
    public static native int getStackPtr0();

    @NoThrow
    @WASM(code = "global.get $q")
    public static native int getStackLimit();

    @NoThrow
    @WASM(code = "global.set $Q")
    public static native void setStackPtr(int addr);

    @NoThrow
    @Alias(name = "stackPop")
    public static void popCall() {
        popCall0();
    }

    @NoThrow
    @Alias(name = "stackPop0")
    @WASM(code = "global.get $Q i32.const 4 i32.add global.set $Q")
    public static native void popCall0();

    @NoThrow
    @Alias(name = "createNullptr")
    public static Throwable createNullptr(String name) {
        return new NullPointerException(name);
    }

    @Alias(name = "checkNotNull")
    public static void checkNotNull(Object obj, String clazzName, String fieldName) {
        if (obj == null) {
            log("NullPointer@class.field:", clazzName, fieldName);
            throw new NullPointerException("Instance must not be null");
        }
    }

    @NoThrow
    @Alias(name = "cip")
    public static <V> int getClassIndexPtr(int classIndex) {// pseudo instance, avoiding allocations
        return findClass(classIndex) + objectOverhead + 12;
    }

    @SuppressWarnings("CommentedOutCode")
    @Alias(name = "init")
    public static void init() {
        // access static, so it is initialized
        getAddr(System.out);
        /*try { // class has been replaced :)
            // reduce this, as it's probably pretty useless in the browser, and memory is valuable
            // default: 8192, so 16 kiB
            BufferedWriter.class.getDeclaredField("defaultCharBufferSize").setInt(null, 64);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }*/
        // could be used to initialize classes or io
        System.setOut(new PrintStream(new JavaLang.JSOutputStream(true)));
        System.setErr(new PrintStream(new JavaLang.JSOutputStream(false)));
        SharedSecrets.setJavaLangAccess(new JavaLangAccessImpl());
    }

    @Alias(name = "throwAME")
    public static void throwAbstractMethodError() {
        throw new AbstractMethodError();
    }

}
