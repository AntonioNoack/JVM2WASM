package jvm;

import annotations.Alias;
import annotations.JavaScript;
import annotations.NoThrow;

import static jvm.JVM32.*;
import static jvm.JVM32.read32;
import static jvm.JavaLang.*;

public class JavaThrowable {

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
        //  -> create an int[] instead to save a little memory :)
        //  -> for capturing the stack trace, we just copy memory,
        //     for printing, we do it directly
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
            // if element was already defined, reuse it :)
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
    @JavaScript(code = "console.log(arg0, trace(arg0))")
    public static native void printStackTrace(Throwable th);

    @NoThrow
    @JavaScript(code = "console.log(trace1(arg0))")
    public static native void printStackTrace(StackTraceElement[] th);

}