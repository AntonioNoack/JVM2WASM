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

        // verify static & static-flag
        boolean isStatic = (self.getModifiers() & ACC_STATIC) != 0;
        boolean expectStatic = calledOrNull == null;
        if (isStatic != expectStatic) {
            throw new IllegalArgumentException("Static-ness mismatch");
        }

        // used to simplify branching
        //  may be a little expensive, but we could cache the array
        args = join(calledOrNull, args);

        String callSignature = getCallSignature(self);
        if (callSignature.length() != args.length + 1) { // +1 for return type
            throw new IllegalArgumentException("Incorrect number of arguments");
        }

        // todo validate call types,
        //  so check parameter types against what is given

        int methodId = getMethodId(self);
        Object arg0 = args.length >= 1 ? args[0] : null;
        Object arg1 = args.length >= 2 ? args[1] : null;

        switch (callSignature) {
            // static runnable
            case "V":
                invokeV(methodId);
                return null;
            // static getters
            case "T":
                return invokeT(methodId);
            case "I":
                return invokeI(methodId);
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
                return invokeTI(arg0, methodId);
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
                //noinspection DataFlowIssue
                invokeIV((int) arg0, methodId);
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
                //noinspection DataFlowIssue
                invokeTIV(arg0, (int) arg1, methodId);
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

    private static int getMethodId(Method self)
            throws NoSuchFieldException, IllegalAccessException {
        return self.getClass().getDeclaredField("slot").getInt(self);
    }

    private static Object[] join(Object prepended, Object[] src) {
        if (prepended == null) return src;
        Object[] joined = new Object[src.length + 1];
        joined[0] = prepended;
        System.arraycopy(src, 0, joined, 1, src.length);
        return joined;
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
