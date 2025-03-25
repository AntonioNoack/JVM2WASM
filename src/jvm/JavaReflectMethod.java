package jvm;

import annotations.Alias;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static jvm.JVM32.*;
import static jvm.JavaLang.Object_toString;
import static jvm.JavaReflect.getClassId;
import static jvm.NativeLog.log;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static utils.StaticClassIndices.*;
import static utils.StaticFieldOffsets.*;

/**
 * Implements java.lang.reflect.Method.invoke, which is quite a complicated process
 */
public class JavaReflectMethod {

    // if we want to support exceptions going forward, we need to double all branches 🤯
    //  -> no, we don't, or at least not with extra return arguments

    // must be long enough for the longest supported combination, including 'self'
    private static final Object[] joinedCallArguments = new Object[4];

    @Alias(names = "java_lang_reflect_Method_invoke_Ljava_lang_ObjectAWLjava_lang_Object")
    public static Object Method_invoke(Method self, Object calledOrNull, Object[] args) {
        if (self == null) throw new IllegalArgumentException("Method must not be null");

        int methodId = getMethodId(self);
        // log("Invoking", self.getDeclaringClass().getName(), self.getName(), methodId);

        verifyCallerType(self, calledOrNull);
        verifyArgumentTypes(self.getParameterTypes(), args, methodId);

        // used to simplify branching
        joinCallArguments(calledOrNull, args);
        Object result = execute(methodId, self.getReturnType(), getCallSignature(self));
        verifyReturnType(self, result);
        return result;
    }

    public static void Constructor_invoke(Constructor<?> self, Object called, Object[] args) {
        if (called == null) throw new IllegalArgumentException("Called must not be null");
        if (self == null) throw new IllegalArgumentException("Method must not be null");

        int methodId = getMethodId(self);
        // log("Creating", self.getDeclaringClass().getName(), methodId);

        verifyArgumentTypes(self.getParameterTypes(), args, methodId);

        // used to simplify branching
        joinCallArguments(called, args);
        execute(methodId, void.class, getCallSignature(self));
    }

    private static void verifyCallerType(Method self, Object caller) {
        boolean isStatic = (self.getModifiers() & ACC_STATIC) != 0;
        boolean expectStatic = caller == null;
        if (isStatic != expectStatic) {
            throw new IllegalArgumentException("Static-ness mismatch");
        }
    }

    private static void verifyArgumentTypes(Class<?>[] expectedTypes, Object[] providedArguments, int methodId) {
        int expectedLength = expectedTypes.length;
        int actualLength = providedArguments.length;
        if (expectedLength != actualLength) {
            log("Incorrect number of arguments for call", expectedLength, actualLength, methodId);
            throw new IllegalArgumentException("Incorrect number of arguments for call");
        }
        for (int i = 0; i < expectedLength; i++) {
            Class<?> type = expectedTypes[i];
            Object value = providedArguments[i];
            if (value != null &&
                    !type.isPrimitive() && // primitives return false for isInstance, so they would need extra checks
                    !type.isInstance(value)) {
                log("Incorrect argument", i);
                throw new ClassCastException("Incorrect argument" + Object_toString(value) + " is no " + type.getName());
            }
        }
    }

    private static Class<?> getNonPrimitiveClass(Class<?> clazz) {
        // primitive classes always return false for isInstance (by spec), so convert them to the wrapper classes
        int idx = getClassId(clazz);
        if (idx >= FIRST_NATIVE && idx <= LAST_NATIVE) {
            idx += FIRST_INSTANCE - FIRST_NATIVE;
            return classIdToInstance(idx);
        }
        return clazz;
    }

    private static void verifyReturnType(Method self, Object returnValue) {
        Class<?> returnType = self.getReturnType();
        if (returnType == void.class) return;// no return type

        if (returnValue == null) {
            if (returnType.isPrimitive()) {
                log("Expected {}, got null", returnType.getName());
                throw new ClassCastException("Call returned wrong object");
            } else {
                // everything is fine
                // we don't know about nullability (yet?)
                return;
            }
        }

        Class<?> returnType1 = getNonPrimitiveClass(returnType);
        if (!returnType1.isInstance(returnValue)) {
            log("Expected", returnType.getName());
            log("Actual", returnValue.getClass().getName());
            throw new ClassCastException("Call returned wrong object");
        }
    }

    private static Object execute(int methodId, Class<?> returnType, String callSignature) {
        Object[] args = joinedCallArguments;
        Object arg0 = args[0];
        Object arg1 = args[1];
        switch (callSignature) {
            // static runnable
            case "V":
                invokeV(methodId);
                return null;
            // static getters
            case "T":
                return invokeT(methodId);
            case "I":
                return castToIntObject(invokeI(methodId), returnType);
            case "J":
                return Long.valueOf(invokeJ(methodId));
            case "F":
                return Float.valueOf(invokeF(methodId));
            case "D":
                return Double.valueOf(invokeD(methodId));
            // getters
            case "TT":
                return invokeTT(arg0, methodId);
            case "TI":
                return castToIntObject(invokeTI(arg0, methodId), returnType);
            case "TJ":
                return Long.valueOf(invokeTJ(arg0, methodId));
            case "TF":
                return Float.valueOf(invokeTF(arg0, methodId));
            case "TD":
                return Double.valueOf(invokeTD(arg0, methodId));
            // static setters
            case "TV":
                invokeTV(arg0, methodId);
                return null;
            case "IV":
                invokeIV(castToIntLike(arg0), methodId);
                return null;
            case "JV":
                invokeJV((long) arg0, methodId);
                return null;
            case "FV":
                invokeFV((float) arg0, methodId);
                return null;
            case "DV":
                invokeDV((double) arg0, methodId);
                return null;
            // setters
            case "TTV":
                invokeTTV(arg0, arg1, methodId);
                return null;
            case "TIV":
                invokeTIV(arg0, castToIntLike(arg1), methodId);
                return null;
            case "TJV":
                invokeTJV(arg0, (long) arg1, methodId);
                return null;
            case "TFV":
                invokeTFV(arg0, (float) arg1, methodId);
                return null;
            case "TDV":
                invokeTDV(arg0, (double) arg1, methodId);
                return null;
            default:
                throw new IllegalArgumentException("Unsupported method call");
        }
    }

    private static int castToIntLike(Object instance) {
        if (instance instanceof Integer) return (int) instance;
        if (instance instanceof Short) return (short) instance;
        if (instance instanceof Byte) return (byte) instance;
        if (instance instanceof Character) return (char) instance;
        if (instance instanceof Boolean) return (boolean) instance ? 1 : 0;
        log("Class (int-like):", instance != null ? instance.getClass().getName() : null);
        throw new ClassCastException("Object cannot be cast to int-like");
    }

    private static Object castToIntObject(int value, Class<?> returnType) {
        if (returnType == boolean.class) return Boolean.valueOf(value != 0);
        if (returnType == int.class) return Integer.valueOf(value);
        if (returnType == short.class) return Short.valueOf((short) value);
        if (returnType == byte.class) return Byte.valueOf((byte) value);
        if (returnType == char.class) return Character.valueOf((char) value);
        log("Class (int):", returnType.getName());
        throw new ClassCastException("Object cannot be cast to int");
    }

    private static int getMethodId(Method self) {
        return readI32AtOffset(self, OFFSET_METHOD_SLOT);
    }

    private static int getMethodId(Constructor<?> self) {
        return readI32AtOffset(self, OFFSET_CONSTRUCTOR_SLOT);
    }

    private static void joinCallArguments(Object prepended, Object[] src) {
        Object[] joined = joinedCallArguments;
        int dstI = 0;
        if (prepended != null) {
            joined[0] = prepended;
            dstI = 1;
        }
        System.arraycopy(src, 0, joined, dstI, src.length);
    }

    private static String getCallSignature(Method self) {
        return readPtrAtOffset(self, OFFSET_METHOD_CALL_SIGNATURE);
    }

    private static String getCallSignature(Constructor<?> self) {
        return readPtrAtOffset(self, OFFSET_CONSTRUCTOR_CALL_SIGNATURE);
    }

    // statix runnable
    private static native void invokeV(int methodId);

    // static getters
    private static native Object invokeT(int methodId);

    private static native int invokeI(int methodId);

    private static native long invokeJ(int methodId);

    private static native float invokeF(int methodId);

    private static native double invokeD(int methodId);

    // getters
    private static native Object invokeTT(Object arg0, int methodId);

    private static native int invokeTI(Object arg0, int methodId);

    private static native long invokeTJ(Object arg0, int methodId);

    private static native float invokeTF(Object arg0, int methodId);

    private static native double invokeTD(Object arg0, int methodId);

    // static setters
    private static native void invokeTV(Object arg0, int methodId);

    private static native void invokeIV(int arg0, int methodId);

    private static native void invokeJV(long arg0, int methodId);

    private static native void invokeFV(float arg0, int methodId);

    private static native void invokeDV(double arg0, int methodId);

    // setters
    private static native void invokeTTV(Object arg0, Object arg1, int methodId);

    private static native void invokeTIV(Object arg0, int arg1, int methodId);

    private static native void invokeTJV(Object arg0, long arg1, int methodId);

    private static native void invokeTFV(Object arg0, float arg1, int methodId);

    private static native void invokeTDV(Object arg0, double arg1, int methodId);

}
