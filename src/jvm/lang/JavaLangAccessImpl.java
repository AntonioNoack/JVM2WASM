package jvm.lang;

import annotations.WASM;
import sun.misc.JavaLangAccess;
import sun.nio.ch.Interruptible;
import sun.reflect.ConstantPool;
import sun.reflect.annotation.AnnotationType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.security.AccessControlContext;
import java.util.Map;

import static jvm.JVM32.resolveInterfaceByClass;
import static jvm.JavaReflect.getClassIndex;

public class JavaLangAccessImpl implements JavaLangAccess {

    @Override
    public ConstantPool getConstantPool(Class<?> aClass) {
        return null;
    }

    @Override
    public boolean casAnnotationType(Class<?> aClass, AnnotationType annotationType, AnnotationType annotationType1) {
        return false;
    }

    @Override
    public AnnotationType getAnnotationType(Class<?> aClass) {
        return null;
    }

    @Override
    public Map<Class<? extends Annotation>, Annotation> getDeclaredAnnotationMap(Class<?> aClass) {
        return null;
    }

    @Override
    public byte[] getRawClassAnnotations(Class<?> aClass) {
        return new byte[0];
    }

    @Override
    public byte[] getRawClassTypeAnnotations(Class<?> aClass) {
        return new byte[0];
    }

    @Override
    public byte[] getRawExecutableTypeAnnotations(Executable executable) {
        return new byte[0];
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends Enum<E>> E[] getEnumConstantsShared(Class<E> c) {
        try {
            // ensure that the class is loaded
            // log("getting constants of", c.getName());
            int classIdx = getClassIndex(c);
            if (classIdx >= 0) {
                // log("got class index", classIdx);
                int methodId = resolveInterfaceByClass(classIdx, 0);
                callStaticInit(methodId);
            }
            // log("result:", getAddr(result));
            return (E[]) Class.class.getDeclaredField("enumConstants").get(c);
            // to do why is this the incorrect type of interface?
            // (getEnumConstantsShared is irrelevant)
            // to do used in: java/util/Spliterators$IntIteratorSpliterator.forEachRemaining
            // to do wanted:  -> uses java.util.PrimitiveIterator.OfInt
            // to do given:  java_lang_CharSequence_lambdaXcodePointsX1_Ljava_util_SpliteratorXOfInt
            // java.util.Spliterator$OfInt extends OfPrimitive<Integer, IntConsumer, OfInt>
            // OfPrimitive<> extends Spliterator
            /*
              // access flags 0x1
              public default codePoints()Ljava/util/stream/IntStream;
               L0
                LINENUMBER 227 L0
                ALOAD 0
                INVOKEDYNAMIC get(Ljava/lang/CharSequence;)Ljava/util/function/Supplier; [
                  // handle kind 0x6 : INVOKESTATIC
                  java/lang/invoke/LambdaMetafactory.metafactory(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;
                  // arguments:
                  ()Ljava/lang/Object;,
                  // handle kind 0x7 : INVOKESPECIAL
                  java/lang/CharSequence.lambda$codePoints$1()Ljava/util/Spliterator$OfInt; itf,
                  ()Ljava/util/Spliterator$OfInt;
                ]
                BIPUSH 16
                ICONST_0
                INVOKESTATIC java/util/stream/StreamSupport.intStream (Ljava/util/function/Supplier;IZ)Ljava/util/stream/IntStream;
                ARETURN
                MAXSTACK = 3
                MAXLOCALS = 1

              // access flags 0x1002
              private synthetic default lambda$codePoints$1()Ljava/util/Spliterator$OfInt;
               L0
                LINENUMBER 228 L0
                NEW java/lang/CharSequence$1CodePointIterator
                DUP
                ALOAD 0
                INVOKESPECIAL java/lang/CharSequence$1CodePointIterator.<init> (Ljava/lang/CharSequence;)V
                BIPUSH 16
                INVOKESTATIC java/util/Spliterators.spliteratorUnknownSize (Ljava/util/PrimitiveIterator$OfInt;I)Ljava/util/Spliterator$OfInt;
                ARETURN
                MAXSTACK = 3
                MAXLOCALS = 1

            * */
        } catch (IllegalAccessException | NoSuchFieldException | ClassCastException e) {
            throw new RuntimeException(e);
        }
    }

    @WASM(code = "call_indirect (type $sRV0)")
    public static native void callStaticInit(int methodId);

    @Override
    public void blockedOn(Thread thread, Interruptible interruptible) {

    }

    @Override
    public void registerShutdownHook(int i, boolean b, Runnable runnable) {

    }

    @Override
    public int getStackTraceDepth(Throwable throwable) {
        return 0;
    }

    @Override
    public StackTraceElement getStackTraceElement(Throwable throwable, int i) {
        return null;
    }

    @Override
    public String newStringUnsafe(char[] chars) {
        return new String(chars);
    }

    @Override
    public Thread newThreadWithAcc(Runnable runnable, AccessControlContext accessControlContext) {
        return null;
    }

    @Override
    public void invokeFinalize(Object o) throws Throwable {

    }
}
