package jvm;

import annotations.Alias;
import annotations.NoThrow;

public class SunMisc {

    @NoThrow
    @Alias(names = "sun_misc_VM_initialize_V")
    public static void sun_misc_VM_initialize_V() {

    }

    @NoThrow
    @Alias(names = "sun_misc_VM_getSavedProperty_Ljava_lang_StringLjava_lang_String")
    public static String sun_misc_VM_getSavedProperty(String key) {
        if ("java.lang.Integer.IntegerCache.high".equals(key)) {
            return "256";// default: 127; max pre-allocated integer
        }
        return null;
    }

    @NoThrow
    @Alias(names = "sun_reflect_Reflection_getCallerClass_Ljava_lang_Class")
    public static Class<SunMisc> sun_reflect_Reflection_getCallerClass_Ljava_lang_Class() {
        // could be fixed in the future;
        // is being used for security at the moment, so idc
        return SunMisc.class;
    }

    @NoThrow
    @Alias(names = "sun_misc_VM_isSystemDomainLoader_Ljava_lang_ClassLoaderZ")
    public static boolean VM_isSystemDomainLoader(ClassLoader cl) {
        // there is only a utils.single class loader
        return true;
    }

    @NoThrow
    @Alias(names = "sun_misc_Unsafe_registerNatives_V")
    public static void sun_misc_Unsafe_registerNatives_V() {

    }

    @Alias(names = "static_sun_misc_Unsafe_V")
    public static void static_sun_misc_Unsafe_V() {
    }

    @Alias(names = "static_java_lang_ClassXAtomic_V")
    public static void static_java_lang_ClassXAtomic_V() {
    }

    @Alias(names = "static_java_io_File_V")
    public static void static_java_io_File_V() {
    }

    @Alias(names = "static_sun_misc_SharedSecrets_V")
    public static void static_sun_misc_SharedSecrets_V() {
    }

    @Alias(names = "sun_misc_Unsafe_getUnsafe_Lsun_misc_Unsafe")
    public static Object sun_misc_Unsafe_getUnsafe_Lsun_misc_Unsafe() {
        throw new RuntimeException("Unsafe is not supported!");
    }

}
