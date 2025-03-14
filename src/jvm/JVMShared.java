package jvm;

import annotations.*;
import jvm.lang.JavaLangAccessImpl;
import sun.misc.SharedSecrets;

import java.io.PrintStream;

import static jvm.JVM32.*;
import static jvm.NativeLog.log;
import static jvm.ThrowJS.throwJs;

/**
 * Things that are shared between JVM32 and JVM64: stuff, that is independent of pointerSize/is32Bits/ptrType
 */
public class JVMShared {

    public static final int intSize = 4;
    public static final int longSize = 8;

    @NoThrow
    @Alias(names = "stackPop")
    public static void stackPop() {
        stackPopImpl();
    }

    @NoThrow
    @Alias(names = "getStackDepth")
    public static int getStackDepth() {
        return getStackDepth(getStackPtr());
    }

    @NoThrow
    public static int getStackDepth(int stackPointer) {
        return (getStackStart() - stackPointer) >> 2;
    }

    @NoThrow
    @WASM(code = "global.get $stackPointer i32.const 4 i32.add global.set $stackPointer")
    public static native void stackPopImpl();

    @NoThrow
    @WASM(code = "global.get $stackPointer")
    public static native int getStackPtr();

    @NoThrow
    @WASM(code = "global.get $stackPointerStart")
    public static native int getStackStart();

    @NoThrow
    @WASM(code = "global.get $stackEndPointer")
    public static native int getStackLimit();

    @NoThrow
    @WASM(code = "global.set $stackPointer")
    public static native void setStackPtr(int addr);

    private static boolean canWarnStackOverflow = true;

    @NoThrow
    @Alias(names = "stackPush")
    public static void stackPush(int idx) {
        JVM32.printStackTraceLine(idx);
        int stackPointer = getStackPtr() - 4;
        int limit = getStackLimit();
        if (unsignedGreaterThanEqual(stackPointer, limit)) {
            write32(stackPointer, idx);
        } else if (stackPointer == limit - 4) {
            // stack overflow
            // we can do different things here -> let's just keep running;
            // just the stack is no longer tracked :)
            if (canWarnStackOverflow) {
                canWarnStackOverflow = false;
                log("Warning: Exited stack space, meaning",
                        (getStackStart() - getStackLimit()) >> 2, " recursive calls");
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
        getAddr(System.out);
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
    public static native int getStaticInitTable();

    @Alias(names = "wasStaticInited")
    public static boolean wasStaticInited(int index) {
        validateClassIdx(index);
        int address = getStaticInitTable() + index;
        boolean answer = read8(address) != 0;
        write8(address, (byte) 1);
        return answer;
    }

    @NoThrow
    @WASM(code = "i32.ge_u")
    private static native int ge_ui(int a, int b);

    @NoThrow
    @WASM(code = "i32.ge_u")
    static native boolean ge_ub(int a, int b);

    @NoThrow
    @WASM(code = "i32.lt_u")
    private static native int lt(int a, int b);

    @NoThrow
    public static int getTypeShiftUnsafe(int clazz) {
        // 0 1 2 3 4 5 6 7 8 9
        // 2 2 2 2 0 0 1 1 3 3
        int flag0 = ge_ui(clazz, 4) & lt(clazz, 6);
        int flag1 = ge_ui(clazz, 8);
        return 2 - flag0 - flag0 - ge_ui(clazz, 6) + flag1 + flag1;
    }

    public static int getTypeShift(int clazz) {
        if (clazz < 0 || clazz > 9) throw new IllegalArgumentException();
        return getTypeShiftUnsafe(clazz);
    }

    @NoThrow
    @WASM(code = "") // automatically done
    public static native int b2i(boolean flag);

    @NoThrow
    public static int getSuperClassId(int classId) {
        int tableAddress = getInheritanceTableEntry(classId);
        return getSuperClassIdFromInheritanceTableEntry(tableAddress);
    }

    @NoThrow
    public static int getInstanceSize(int classId) {
        // look up class table for size
        int tableAddress = getInheritanceTableEntry(classId);
        return read32(tableAddress + 4);
    }

    @NoThrow
    static int getInheritanceTableEntry(int classId) {
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
            validateClassIdx(childClassIdx);
            int tableAddress = getInheritanceTableEntry(childClassIdx);
            // we can return here if childClassIdx = 0, because java/lang/Object has no interfaces
            if (childClassIdx == 0) return false;
            childClassIdx = read32(tableAddress); // switch to parent class
        }
    }

    @NoThrow
    public static boolean isInInterfaceTable(int tableAddress, int interfaceClassIdx) {
        // handle interfaces
        tableAddress += 8; // skip over super class and instance size
        int length = read32(tableAddress);
        for (int i = 0; i < length; i++) {
            tableAddress += 4; // skip to next entry
            if (read32(tableAddress) == interfaceClassIdx) return true;
        }
        return false;
    }

    @NoThrow
    static void validateClassIdx(int childClassIdx) {
        if (ge_ub(childClassIdx, numClasses())) {
            log("class index out of bounds", childClassIdx, numClasses());
            throwJs();
        }
    }

    public static int resolveInterfaceByClass(int classId, int methodId) {
        // log("resolveInterfaceByClass", classId, methodId);
        validateClassIdx(classId);
        int tablePtr = getInheritanceTableEntry(classId);
        // log("tablePtr", tablePtr);
        if (tablePtr == 0) {
            log("No class table entry was found!", classId);
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

    static void reportNotFound(int clazz, int methodId, int tableLength, int tablePtr) {
        // return -1 - min
        log("Method could not be found", clazz);
        log("  id, length:", methodId, tableLength);
        for (int i = 0; i < tableLength; i++) {
            int addr = tablePtr + (i << 3);
            log("  ", read32(addr), read32(addr + 4));
        }
        throwJs("Method could not be found");
    }

    private static int searchInterfaceBinary(int min, int max, int methodId, int tablePtr) {
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

    private static int searchInterfaceLinear(int min, int max, int methodId, int tablePtr) {
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


    @Alias(names = "resolveInterface")
    public static int resolveInterface(Object instance, int methodId) {
        if (instance == null) {
            throw new NullPointerException("Instance for resolveInterface is null");
        } else {
            return resolveInterfaceByClass(readClassI(instance), methodId);
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
        int testedClass = readClassI(instance);
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
        int testedClass = readClassI(instance);
        return isChildOrSameClassNonInterface(testedClass, clazz);
    }

    @NoThrow
    @Alias(names = "instanceOfExact")
    public static boolean instanceOfExact(Object instance, int clazz) {
        return (instance != null) & (readClassI(instance) == clazz);
    }

    @NoThrow
    @WASM(code = "" +
            "  local.get 0 i32.const 2 i32.shl\n" + // clazz << 2
            "  global.get $resolveIndirectTable i32.add i32.load\n" + // memory[resolveIndirectTable + clazz << 2]
            "  local.get 1 i32.add i32.load") // memory[methodPtr + memory[resolveIndirectTable + clazz << 2]]
    private static native int resolveIndirectByClassUnsafe(int classId, int methodPtr);

    @NoThrow
    public static int resolveIndirectByClass(int classId, int methodPtr) {
        // unsafe memory -> from ~8ms/frame to ~6.2ms/frame
        // log("resolveIndirectByClass", classId, methodPtr);
        int x = resolveIndirectByClassUnsafe(classId, methodPtr);
        if (x < 0) {
            Class<Object> class1 = findClass(classId);
            log("resolveIndirectByClass", class1.getName());
            throwJs("classIndex, methodPtr, resolved:", classId, methodPtr, x);
        }
        return x;
    }

    @NoThrow
    public static int getDynamicTableSize(int classIdx) {
        return resolveIndirectByClass(classIdx, 0);
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
    @WASM(code = "global.get $resolveIndirectTable")
    public static native int resolveIndirectTable();

    @NoThrow
    @WASM(code = "global.get $numClasses")
    public static native int numClasses();

    @Alias(names = "resolveIndirect")
    public static int resolveIndirect(Object instance, int signatureId) {
        if (instance == null) {
            throw new NullPointerException("Instance for resolveIndirect is null");
        }
        return resolveIndirectByClass(readClassI(instance), signatureId);
    }

    @NoThrow
    @WASM(code = "") // auto
    public static native <V> V unsafeCast(Object obj);

}
