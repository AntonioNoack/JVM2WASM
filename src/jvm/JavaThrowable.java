package jvm;

import annotations.Alias;
import annotations.JavaScript;
import annotations.NoThrow;

import static jvm.JVM32.*;
import static jvm.JVMShared.*;
import static jvm.JavaLang.*;
import static jvm.NativeLog.log;
import static utils.StaticFieldOffsets.*;

public class JavaThrowable {

    private static final int stackReportLimit = 256;

    @Alias(names = "java_lang_Throwable_getStackTraceDepth_I")
    public static int Throwable_getStackTraceDepth_I(Throwable th) {
        // where is this used?
        return getStackDepth();
    }

    @Alias(names = "java_lang_Throwable_printStackTrace_V")
    public static void Throwable_printStackTrace_V(Throwable th) {
        printStackTraceHead(th.getClass().getName(), th.getMessage());
        StackTraceElement[] trace = ptrTo(read32(getAddr(th) + objectOverhead + 4));
        for (StackTraceElement element : trace) {
            String clazz = element.getClassName();
            String name = element.getMethodName();
            int line = element.getLineNumber();
            printStackTraceLine(clazz, name, line);
        }
        printStackTraceEnd();
    }

    @NoThrow
    @JavaScript(code = "console.log(str(arg0)+': '+str(arg1))")
    public static native void printStackTraceHead(String name, String message);

    @NoThrow
    @JavaScript(code = "console.log('  '+str(arg0)+'.'+str(arg1)+':'+arg2)")
    public static native void printStackTraceLine(String clazzName, String methodName, int lineNumber);

    @NoThrow
    @JavaScript(code = "")
    public static native void printStackTraceEnd();

    @NoThrow
    @Alias(names = "java_lang_Throwable_fillInStackTrace_Ljava_lang_Throwable")
    public static Throwable Throwable_fillInStackTraceI0(Throwable th) throws NoSuchFieldException, IllegalAccessException {
        return Throwable_fillInStackTraceI(th);
    }

    private static boolean insideFIST = false;

    private static StackTraceElement[] getStackTrace(Throwable th) {
        return readPtrAtOffset(th, OFFSET_THROWABLE_STACKTRACE);
    }

    private static void setStackTrace(Throwable th, StackTraceElement[] value) {
        writePtrAtOffset(th, OFFSET_THROWABLE_STACKTRACE, value);
    }

    @NoThrow
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
        int stackLength = getStackDepth(sp);// each element is 4 bytes in size currently
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

        int lookupBasePtr = getStackTraceTablePtr();
        if (lookupBasePtr <= 0) {
            insideFIST = false;
            return th;
        }

        criticalAlloc = true;

        StackTraceElement[] array0 = th != null ? getStackTrace(th) : null;
        StackTraceElement[] array1 = array0 != null && array0.length == stackLength ?
                array0 : new StackTraceElement[stackLength];
        /*if (array1 == null) {
            // how? branch generation was broken
            throwJs("Array1 is null somehow...", getAddr(array0), getAddr(array1), stackLength);
            insideFIST = false;
            return th;
        }*/
        // assign stackTrace; detailMessage, then stackTrace -> 4 offset
        if (th != null) setStackTrace(th, array1);

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
    private static void fillInElement(String className, String methodName, int line, StackTraceElement[] array1, int i) throws NoSuchFieldException, IllegalAccessException {
        // log("Fill In Element", className, methodName);
        // log("Fill In Element", line, getAddr(array1), i);
        // if (i >= array1.length) throwJs("Index out of bounds", getAddr(array1), i, array1.length);
        StackTraceElement element = array1[i];
        if (element == null) {
            array1[i] = new StackTraceElement(className, methodName, className, line);
        } else {
            // if element was already defined, reuse it :)
            writePtrAtOffset(element, OFFSET_STE_CLASS, className);
            writePtrAtOffset(element, OFFSET_STE_METHOD, methodName);
            writePtrAtOffset(element, OFFSET_STE_FILE, className);
            writeI32AtOffset(element, OFFSET_STE_LINE, line);
        }
    }

    @Alias(names = "java_lang_Thread_getStackTrace_AW")
    public static StackTraceElement[] Thread_getStackTrace(Thread thread) {

        int sp = getStackPtr();
        int stackLength = getStackDepth(sp);// each element is 4 bytes in size currently
        if (stackLength < 1) return new StackTraceElement[0];
        if (stackLength >= stackReportLimit) stackLength = stackReportLimit;
        int lookupBasePtr = getStackTraceTablePtr();
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
