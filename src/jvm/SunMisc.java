package jvm;

import annotations.Alias;
import annotations.NoThrow;
import sun.misc.Unsafe;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;

import static jvm.JVM32.*;
import static jvm.JavaLang.*;

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

    /*
        @NoThrow
        @Alias(name = "sun_misc_Unsafe_arrayBaseOffset_Ljava_lang_ClassI")
        public static <V> int Unsafe_arrayBaseOffset(Unsafe unsafe, Class<V> clazz) {
            return arrayOverhead;
        }

        @NoThrow
        @Alias(name = "sun_misc_Unsafe_arrayIndexScale_Ljava_lang_ClassI")
        public static <V> int Unsafe_arrayIndexScale(Unsafe unsafe, Class<V> clazz) {
            String name = clazz.getName();
            if (name.charAt(0) != '[') return 0;
            switch (name) {
                case "[B":
                case "[Z":
                    return 1;
                case "[S":
                case "[C":
                    return 2;
                case "[J":
                case "[D":
                    return 8;
                default:
                    return 4;
            }
        }

        @NoThrow
        @Alias(name = "sun_misc_Unsafe_addressSize_I")
        public static int Unsafe_addressSize(Unsafe unsafe) {
            return 4;
        }

        @NoThrow
        @Alias(name = "sun_misc_Unsafe_objectFieldOffset_Ljava_lang_reflect_FieldJ")
        public static long Unsafe_objectFieldOffset(Unsafe unsafe, Field field) {
            return getFieldOffset(field);
        }

        @NoThrow
        @Alias(name = "sun_misc_Unsafe_compareAndSwapObject_Ljava_lang_ObjectJLjava_lang_ObjectLjava_lang_ObjectZ")
        public static boolean Unsafe_compareAndSwapObject(Unsafe unsafe, Object base, long offset, Object oldValue, Object newValue) {
            int addr = getAddr(base) + (int) offset;
            if (read32(addr) == getAddr(oldValue)) {
                write32(addr, getAddr(newValue));
                return true;
            }
            return false;
        }

        @NoThrow
        @Alias(name = "sun_misc_Unsafe_compareAndSwapLong_Ljava_lang_ObjectJJJZ")
        public static boolean Unsafe_compareAndSwapLong(Unsafe unsafe, Object base, long offset, long oldValue, long newValue) {
            int addr = getAddr(base) + (int) offset;
            if (read64(addr) == oldValue) {
                write64(addr, newValue);
                return true;
            }
            return false;
        }

        @NoThrow
        @Alias(name = "sun_misc_Unsafe_compareAndSwapInt_Ljava_lang_ObjectJIIZ")
        public static boolean Unsafe_compareAndSwapInt(Unsafe unsafe, Object base, long offset, int oldValue, int newValue) {
            int addr = getAddr(base) + (int) offset;
            if (read64(addr) == oldValue) {
                write64(addr, newValue);
                return true;
            }
            return false;
        }

        @NoThrow
        @Alias(name = "sun_misc_Unsafe_getInt_Ljava_lang_ObjectJI")
        public static int Unsafe_getInt(Unsafe unsafe, Object base, long offset) {
            return read32(getAddr(base) + (int) offset);
        }

        @NoThrow
        @Alias(name = "sun_misc_Unsafe_getIntVolatile_Ljava_lang_ObjectJI")
        public static int Unsafe_getIntVolatile(Unsafe unsafe, Object base, long offset) {
            return read32(getAddr(base) + (int) offset);
        }

        @NoThrow
        @Alias(name = "sun_misc_Unsafe_getLong_Ljava_lang_ObjectJJ")
        public static long Unsafe_getLong(Unsafe unsafe, Object base, long offset) {
            return read64(getAddr(base) + (int) offset);
        }

        @NoThrow
        @Alias(name = "sun_misc_Unsafe_getLongVolatile_Ljava_lang_ObjectJJ")
        public static long Unsafe_getLongVolatile(Unsafe unsafe, Object base, long offset) {
            return read64(getAddr(base) + (int) offset);
        }

        @NoThrow
        @Alias(name = "sun_misc_Unsafe_getObject_Ljava_lang_ObjectJLjava_lang_Object")
        public static int Unsafe_getObject(Unsafe unsafe, Object base, long offset) {
            return read32(getAddr(base) + (int) offset);
        }

        @NoThrow
        @Alias(name = "sun_misc_Unsafe_getObjectVolatile_Ljava_lang_ObjectJLjava_lang_Object")
        public static int Unsafe_getObjectVolatile(Unsafe unsafe, Object base, long offset) {
            return read32(getAddr(base) + (int) offset);
        }

        @NoThrow
        @Alias(name = "sun_misc_Unsafe_putInt_Ljava_lang_ObjectJIV")
        public static void Unsafe_putInt(Unsafe unsafe, Object base, long offset, int value) {
            log("Putting int at", getAddr(base), (int) offset);
            write32(getAddr(base) + (int) offset, value);
        }

        @NoThrow
        @Alias(name = "sun_misc_Unsafe_putLong_Ljava_lang_ObjectJJV")
        public static void Unsafe_putLong(Unsafe unsafe, Object base, long offset, long value) {
            log("Putting long at", getAddr(base), (int) offset);
            write64(getAddr(base) + (int) offset, value);
        }

        @NoThrow
        @Alias(name = "sun_misc_Unsafe_putObject_Ljava_lang_ObjectJLjava_lang_ObjectV")
        public static void Unsafe_putObject(Object unsafe, Object base, long offset, Object value) {
            write32(getAddr(base) + (int) offset, getAddr(value));
        }

        private static final HashMap<Integer, byte[]> nativeMemory = new HashMap<>(64);

        @Alias(name = "sun_misc_Unsafe_allocateMemory_JJ")
        public static long Unsafe_allocateMemory(Unsafe unsafe, long size) {
            if (size < 0) throw new IllegalArgumentException();
            byte[] memory = new byte[(int) size];
            int pointer = getAddr(memory) + arrayOverhead;
            nativeMemory.put(pointer, memory);
            return pointer;
        }

        @Alias(name = "sun_misc_Unsafe_reallocateMemory_JJJ")
        public static long Unsafe_reallocateMemory(Unsafe unsafe, long ptr, long newSize) {
            if (newSize < 0 || ptr == 0) throw new IllegalArgumentException();
            byte[] oldMemory = nativeMemory.get((int) ptr);
            int oldSize = oldMemory.length;
            if (newSize < oldSize && newSize >= (oldSize >> 1) + 128) {// reuse
                return ptr;
            } else {
                long newMemory = Unsafe_allocateMemory(unsafe, newSize);
                Unsafe_copyMemory(
                        unsafe,
                        null, ptr - arrayOverhead,
                        null, newMemory - arrayOverhead,
                        Math.min(oldSize, newSize)
                );
                Unsafe_freeMemory(unsafe, ptr);
                return newMemory;
            }
        }*/
/*
    @Alias(name = "sun_misc_Unsafe_copyMemory_Ljava_lang_ObjectJLjava_lang_ObjectJJV")
    public static void Unsafe_copyMemory(Unsafe unsafe, Object src, long srcIndex, Object dst, long dstIndex, long length) {

        if (length < 0) throw new IllegalArgumentException();
        if ((src == dst && srcIndex == dstIndex) || length == 0) return; // done :)
        int lengthI = (int) length;
        int src1 = getAddr(src), dst1 = getAddr(dst);

        int clazz1 = readClass(src1), clazz2 = readClass(dst1);
        if (clazz1 != clazz2) throw new RuntimeException("Mismatched types");

        log("Copying memory", getAddr(src), getAddr(dst), (int) length);
        log("CM", (int) srcIndex, (int) dstIndex);

        int delta = arrayOverhead;
        int src2 = src1 + delta + (int) srcIndex;
        int dst2 = dst1 + delta + (int) dstIndex;

        if (src != dst || srcIndex > dstIndex) copyForwards(src2, dst2, lengthI);
        else copyBackwards(src2, dst2, lengthI);
    }

    @Alias(name = "sun_misc_Unsafe_freeMemory_JV")
    public static void Unsafe_freeMemory(Unsafe unsafe, long ptr) {
        nativeMemory.remove((int) ptr);
    }
*/
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

    @Alias(name = "java_lang_Class_getConstructor0_ALjava_lang_ClassILjava_lang_reflect_Constructor")
    public static <V> Constructor<V> getConstructor0(Class<V> self, Object[] args) throws NoSuchFieldException, IllegalAccessException {
        if (args.length > 0) return null;
        if (constructors == null) {
            constructors = new Constructor[numClasses()];
        }
        int idx = getClassIndex(self);
        Constructor<Object> cs = constructors[idx];
        if (cs == null) {
            cs = ptrTo(create(getClassIndex(Constructor.class)));
            Constructor.class.getField("clazz").set(cs, self);
        }
        constructors[idx] = cs;
        return (Constructor<V>) cs;
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
