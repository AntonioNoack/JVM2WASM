package jvm;

import annotations.Alias;
import annotations.NoThrow;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;

import static jvm.JVM32.*;
import static jvm.JavaLang.getClassIndex;
import static jvm.JavaLang.ptrTo;

public class SunMisc {

    @NoThrow
    @Alias(name = "sun_misc_VM_initialize_V")
    public static void sun_misc_VM_initialize_V() {

    }

    @NoThrow
    @Alias(name = "sun_misc_VM_getSavedProperty_Ljava_lang_StringLjava_lang_String")
    public static String sun_misc_VM_getSavedProperty(String key) {
        if ("java.lang.Integer.IntegerCache.high".equals(key)) {
            return "256";// default: 127; max pre-allocated integer
        }
        return null;
    }

    @NoThrow
    @Alias(name = "sun_reflect_Reflection_getCallerClass_Ljava_lang_Class")
    public static Class<SunMisc> sun_reflect_Reflection_getCallerClass_Ljava_lang_Class() {
        // could be fixed in the future;
        // is being used for security at the moment, so idc
        return SunMisc.class;
    }

    @NoThrow
    @Alias(name = "sun_misc_VM_isSystemDomainLoader_Ljava_lang_ClassLoaderZ")
    public static boolean VM_isSystemDomainLoader(ClassLoader cl) {
        // there is only a utils.single class loader
        return true;
    }

    @NoThrow
    @Alias(name = "sun_misc_Unsafe_registerNatives_V")
    public static void sun_misc_Unsafe_registerNatives_V() {

    }

    @Alias(name = "java_lang_reflect_AccessibleObject_checkAccess_Ljava_lang_ClassLjava_lang_ClassLjava_lang_ObjectIV")
    public static <A, B> void java_lang_reflect_AccessibleObject_checkAccess_Ljava_lang_ClassLjava_lang_ClassLjava_lang_ObjectIV(Object self, Class<A> clazz, Class<B> clazz2, Object obj, int x) {
    }

    @Alias(name = "java_lang_Class_reflectionData_Ljava_lang_ClassXReflectionData")
    public static Object java_lang_Class_reflectionData_Ljava_lang_ClassXReflectionData(Object self) {
        throw new RuntimeException("Cannot ask for reflection data, won't work");
    }

    @Alias(name = "java_lang_Class_privateGetPublicMethods_ALjava_lang_reflect_Method")
    public static Object[] java_lang_Class_privateGetPublicMethods_ALjava_lang_reflect_Method(Object self) {
        return empty;// todo
    }

    @Alias(name = "java_lang_Class_privateGetDeclaredMethods_ZALjava_lang_reflect_Method")
    public static Object[] java_lang_Class_privateGetDeclaredMethods_ZALjava_lang_reflect_Method(Object self, boolean sth) {
        return empty;// todo
    }

    private static Constructor<Object>[] constructors;

    @Alias(name = "java_lang_Class_getConstructor_ALjava_lang_ClassLjava_lang_reflect_Constructor")
    public static <V> Constructor<V> getConstructor(Class<V> self, Object[] args) throws NoSuchFieldException, IllegalAccessException {
        if (args == null) {
            throwJs("Arguments was null?");
            return null;
        }
        if (args.length > 0) {
            throwJs("Cannot access constructors with arguments");
            return null;
        }
        if (constructors == null) {
            constructors = new Constructor[numClasses()];
        }
        int idx = getClassIndex(self);
        Constructor<Object> cs = constructors[idx];
        if (cs == null) {
            cs = ptrTo(create(getClassIndex(Constructor.class)));
            Constructor.class.getDeclaredField("clazz").set(cs, self);
            constructors[idx] = cs;
        }
        return (Constructor<V>) cs;
    }

    @Alias(name = "java_lang_reflect_Constructor_equals_Ljava_lang_ObjectZ")
    public static boolean java_lang_reflect_Constructor_equals_Ljava_lang_ObjectZ(Object self, Object other) {
        return self == other;
    }

    @Alias(name = "java_lang_reflect_Constructor_toString_Ljava_lang_String")
    public static String java_lang_reflect_Constructor_toString_Ljava_lang_String(Object self) {
        return self.getClass().getName();
    }

    @Alias(name = "java_lang_reflect_Constructor_getDeclaredAnnotations_ALjava_lang_annotation_Annotation")
    public static Object java_lang_reflect_Constructor_getDeclaredAnnotations_ALjava_lang_annotation_Annotation(Object self) {
        return null;
    }

    @Alias(name = "java_lang_reflect_Constructor_newInstance_ALjava_lang_ObjectLjava_lang_Object")
    public static <V> V java_lang_reflect_Constructor_newInstance_ALjava_lang_ObjectLjava_lang_Object(Constructor<V> self, Object[] args) throws InstantiationException, IllegalAccessException {
        if (args != null && args.length != 0)
            throw new IllegalArgumentException("Constructors with arguments aren't yet supported in WASM");
        return self.getDeclaringClass().newInstance();
    }

    @Alias(name = "java_lang_Class_getInterfaces_ALjava_lang_Class")
    public static Object[] java_lang_Class_getInterfaces_ALjava_lang_Class(Object self) {
        return empty;// todo
    }

    @Alias(name = "static_sun_misc_Unsafe_V")
    public static void static_sun_misc_Unsafe_V() {
    }

    @Alias(name = "static_java_lang_ClassXAtomic_V")
    public static void static_java_lang_ClassXAtomic_V() {
    }

    @Alias(name = "static_java_io_File_V")
    public static void static_java_io_File_V() {
    }

    @Alias(name = "static_sun_misc_SharedSecrets_V")
    public static void static_sun_misc_SharedSecrets_V() {
    }

    @Alias(name = "sun_misc_Unsafe_getUnsafe_Lsun_misc_Unsafe")
    public static void sun_misc_Unsafe_getUnsafe_Lsun_misc_Unsafe() {
        throw new RuntimeException("Unsafe is not supported!");
    }

    @Alias(name = "static_java_lang_ClassLoaderXParallelLoaders_V")
    public static void static_java_lang_ClassLoaderXParallelLoaders_V() {
    }

    private static final Object[] empty = new Object[0];

    @Alias(name = "java_lang_ClassLoaderXParallelLoaders_register_Ljava_lang_ClassZ")
    public static boolean java_lang_ClassLoaderXParallelLoaders_register_Ljava_lang_ClassZ(Object clazz) {
        // idc
        return false;
    }

    @Alias(name = "java_lang_Class_getAnnotation_Ljava_lang_ClassLjava_lang_annotation_Annotation")
    public static Annotation java_lang_Class_getAnnotation_Ljava_lang_ClassLjava_lang_annotation_Annotation(Object self, Object annotClass) {
        // todo implement properly
        return null;
    }

    @Alias(name = "java_lang_ClassLoader_loadLibrary0_Ljava_lang_ClassLjava_io_FileZ")
    public static boolean java_lang_ClassLoader_loadLibrary0_Ljava_lang_ClassLjava_io_FileZ(Object clazz, File file) {
        return false;
    }

    @Alias(name = "java_lang_ClassLoaderXNativeLibrary_finalize_V")
    public static void java_lang_ClassLoaderXNativeLibrary_finalize_V(Object self) {
    }

    @Alias(name = "java_lang_ClassLoader_loadLibrary_Ljava_lang_ClassLjava_lang_StringZV")
    public static void java_lang_ClassLoader_loadLibrary_Ljava_lang_ClassLjava_lang_StringZV(Object clazz, String file, boolean sth) {
    }

}
