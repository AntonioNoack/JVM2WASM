package jvm.lang;

import sun.misc.JavaLangAccess;
import sun.nio.ch.Interruptible;
import sun.reflect.ConstantPool;
import sun.reflect.annotation.AnnotationType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.security.AccessControlContext;
import java.util.Map;

import static jvm.JVM32.readPtrAtOffset;
import static jvm.JVMShared.resolveInterfaceByClass;
import static jvm.JavaReflect.getClassId;
import static utils.StaticFieldOffsets.OFFSET_CLASS_ENUM_CONSTANTS;

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
    public <E extends Enum<E>> E[] getEnumConstantsShared(Class<E> c) {
        // ensure that the class is loaded
        int classId = getClassId(c);
        int methodId = resolveInterfaceByClass(classId, 0);
        callStaticInit(methodId);
        return readPtrAtOffset(c, OFFSET_CLASS_ENUM_CONSTANTS);
    }

    // needs to be assigned dynamically to differentiate between throwing/non-throwing functions
    // @WASM(code = "call_indirect (type $Xi)")
    public static native void callStaticInit(int methodId);

    @Override
    public void blockedOn(Thread thread, Interruptible interruptible) {

    }

    @Override
    public void registerShutdownHook(int i, boolean b, Runnable runnable) {

    }

    @Override
    public int getStackTraceDepth(Throwable throwable) {
        return throwable.getStackTrace().length;
    }

    @Override
    public StackTraceElement getStackTraceElement(Throwable throwable, int i) {
        return throwable.getStackTrace()[i];
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
