package jvm;

import annotations.Alias;

import java.lang.reflect.Method;

import static org.objectweb.asm.Opcodes.ACC_STATIC;

/**
 * Implements java.lang.reflect.Method.invoke, which is quite a complicated process
 */
public class JavaReflectMethod {

    // if we want to support exceptions going forward, we need to double all branches 🤯
    //  -> no, we don't, or at least not with extra return arguments

    // must be long enough for the longest supported combination, including 'self'
    private static final Object[] joinedCallArguments = new Object[4];

    @Alias(names = "java_lang_reflect_Method_invoke_Ljava_lang_ObjectAWLjava_lang_Object")
    public static Object Method_invoke_Ljava_lang_ObjectAWLjava_lang_Object(
            Method self, Object calledOrNull, Object[] args
    ) throws NoSuchFieldException, IllegalAccessException {
        // todo store methods in class;
        // todo filter for callable methods, and add them all to the dynIndex
        // todo store the callSignature of each method within it

        if (self == null) {
            throw new IllegalArgumentException("Method must not be null");
        }

        verifyCallerType(self, calledOrNull);
        verifyArgumentTypes(self, args);

        // used to simplify branching
        joinCallArguments(calledOrNull, args);

        Object result = execute(self);
        verifyReturnType(self, result);
        return result;
    }

    private static void verifyCallerType(Method self, Object caller) {
        boolean isStatic = (self.getModifiers() & ACC_STATIC) != 0;
        boolean expectStatic = caller == null;
        if (isStatic != expectStatic) {
            throw new IllegalArgumentException("Static-ness mismatch");
        }
    }

    private static void verifyArgumentTypes(Method self, Object[] args) {
        Class<?>[] types = self.getParameterTypes();
        // todo this could prove difficult with our function overrides... we need to make sure to store the metadata of the originals
        if (types.length != args.length) throw new IllegalArgumentException("Incorrect number of arguments for call");

    }

    private static void verifyReturnType(Method self, Object returnValue) {
        Class<?> returnType = self.getReturnType();
        if (!returnType.isInstance(returnValue)) {
            throw new ClassCastException("Call returned wrong object");
        }
    }

    private static Object execute(Method self)
            throws NoSuchFieldException, IllegalAccessException {
        int methodId = getMethodId(self);
        Object[] args = joinedCallArguments;
        Object arg0 = args[0];
        Object arg1 = args[1];
        Class<?> returnType = self.getReturnType();
        String callSignature = getCallSignature(self);
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
                return invokeJ(methodId);
            case "F":
                return invokeF(methodId);
            case "D":
                return invokeD(methodId);
            // getters
            case "TT":
                return invokeTT(arg0, methodId);
            case "TI":
                return castToIntObject(invokeTI(arg0, methodId), returnType);
            case "TJ":
                return invokeTJ(arg0, methodId);
            case "TF":
                return invokeTF(arg0, methodId);
            case "TD":
                return invokeTD(arg0, methodId);
            // static setters
            case "TV":
                invokeTV(arg0, methodId);
                return null;
            case "IV":
                invokeIV(castToIntLike(arg0), methodId);
                return null;
            case "JV":
                //noinspection DataFlowIssue
                invokeJV((long) arg0, methodId);
                return null;
            case "FV":
                //noinspection DataFlowIssue
                invokeFV((float) arg0, methodId);
                return null;
            case "DV":
                //noinspection DataFlowIssue
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
                //noinspection DataFlowIssue
                invokeTJV(arg0, (long) arg1, methodId);
                return null;
            case "TFV":
                //noinspection DataFlowIssue
                invokeTFV(arg0, (float) arg1, methodId);
                return null;
            case "TDV":
                //noinspection DataFlowIssue
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
        throw new ClassCastException("Object cannot be cast to int");
    }

    private static Object castToIntObject(int value, Class<?> returnType) {
        if (returnType == int.class) return value;
        if (returnType == short.class) return (short) value;
        if (returnType == byte.class) return (byte) value;
        if (returnType == char.class) return (char) value;
        throw new ClassCastException("Object cannot be cast to int");
    }

    private static int getMethodId(Method self)
            throws NoSuchFieldException, IllegalAccessException {
        return self.getClass().getDeclaredField("slot").getInt(self);
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

    private static String getSignature(Method self) throws NoSuchFieldException, IllegalAccessException {
        return (String) self.getClass().getDeclaredField("signature").get(self);
    }

    private static String getCallSignature(Method self) throws NoSuchFieldException, IllegalAccessException {
        return (String) self.getClass().getDeclaredField("callSignature").get(self);
    }

}
