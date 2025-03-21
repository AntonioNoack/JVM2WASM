package jvm;

import annotations.Alias;
import annotations.NoThrow;
import annotations.UsedIfIndexed;
import annotations.WASM;
import kotlin.jvm.internal.ClassBasedDeclarationContainer;
import kotlin.jvm.internal.ClassReference;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

import static jvm.JVM32.*;
import static jvm.JVMShared.*;
import static jvm.NativeLog.log;
import static jvm.ThrowJS.throwJs;
import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static utils.StaticClassIndices.*;
import static utils.StaticFieldOffsets.*;

public class JavaReflect {

    private static final ClassLoader classLoaderInstance = new ClassLoader() {
    };

    @NoThrow
    @Nullable
    @Alias(names = "java_lang_Class_getFields_AW")
    public static Field[] getFields(Class<?> clazz) {
        return readPtrAtOffset(clazz, OFFSET_CLASS_FIELDS);
    }

    @Alias(names = "java_lang_Class_getDeclaredFields_AW")
    public static Field[] Class_getDeclaredFields_AW(Class<?> clazz) {
        return getFields(clazz);
    }

    @SuppressWarnings("rawtypes")
    @Alias(names = "java_lang_Class_getField_Ljava_lang_StringLjava_lang_reflect_Field")
    public static Field Class_getField(Class clazz, String name) {
        // using class instance, find fields
        if (clazz == null || name == null) throw new NullPointerException("Class.getField()");
        Class originalClass = clazz;
        while (true) {
            Field[] fields = getFields(clazz);
            // log("class for fields", getAddr(clazz));
            // log("fields*, length", fields, length);
            // log("looking for field", searchedField);
            if (fields != null) for (Field field : fields) {
                String fieldName = field.getName();
                // log("field[i]:", fieldName);
                // log("field[i].offset:", getFieldOffset(field));
                if (name.equals(fieldName)) {
                    log("Found field", clazz.getName(), name);
                    return field;
                }
            }
            // use parent class as well, so we need fewer entries in each array
            clazz = clazz.getSuperclass();
            if (clazz == null) {
                log("Missing field", originalClass.getName(), name);
                return null; // not really JVM-conform, but we don't want exception-throwing
            }
        }
    }

    @Alias(names = "java_lang_Class_getDeclaredField_Ljava_lang_StringLjava_lang_reflect_Field")
    public static <V> Field Class_getDeclaredField(Class<V> clazz, String name) {
        return Class_getField(clazz, name);
    }

    @Alias(names = "java_lang_Class_getDeclaredMethods_AW")
    public static <V> Method[] Class_getDeclaredMethods(Class<V> clazz) {
        return readPtrAtOffset(clazz, OFFSET_CLASS_METHODS);
    }

    @Alias(names = "java_lang_Class_getMethods_AW")
    public static <V> Method[] Class_getMethods(Class<V> clazz) {
        // todo we probably need to join this with our parent's methods
        return readPtrAtOffset(clazz, OFFSET_CLASS_METHODS);
    }

    @Alias(names = "java_lang_Class_getName0_Ljava_lang_String")
    public static String Class_getName0_Ljava_lang_String() {
        // should never be called, because classes get set their name at compile-time
        return "?";
    }

    @Alias(names = "java_lang_Class_isInterface_Z")
    public static boolean Class_isInterface_Z(Class<Object> self) {
        // todo confirm this flag is saved like that
        return (self.getModifiers() & ACC_INTERFACE) != 0;
    }

    @Alias(names = "java_lang_Class_getMethod_Ljava_lang_StringAWLjava_lang_reflect_Method")
    public static <V> Method Class_getMethod(Class<V> self, String name, Class<?>[] parameters) {
        if (name == null) return null;
        Method[] methods = Class_getMethods(self);
        for (Method method : methods) {
            if (matches(method, name, parameters)) {
                return method;
            }
        }
        return null; // not JVM conform, but we need to be able to run a JVM without exceptions
        // throw noSuchMethodEx;
    }

    @Alias(names = "java_lang_Class_getDeclaredMethod_Ljava_lang_StringAWLjava_lang_reflect_Method")
    public static <V> Method Class_getDeclaredMethod(Class<V> self, String name, Class<?>[] parameters) {
        return Class_getMethod(self, name, parameters);
    }

    @Alias(names = "java_lang_Class_getCanonicalName_Ljava_lang_String")
    public static <V> String java_lang_Class_getCanonicalName_Ljava_lang_String(Class<V> self) {
        return self.getName();
    }

    private static boolean matches(Method method, String name, Class<?>[] parameters) {
        if (method.getName().equals(name) && parameters.length == method.getParameterCount()) {
            Class<?>[] params2 = method.getParameterTypes();
            for (int j = 0, l2 = parameters.length; j < l2; j++) {
                if (parameters[j] != params2[j]) {
                    return false;
                }
            }
            return true;
        } else return false;
    }

    public static int getFieldOffset(Field field) {
        int offset = readI32AtOffset(field, OFFSET_FIELD_SLOT);
        if (offset < objectOverhead && !Modifier.isStatic(field.getModifiers())) {
            log("Field/0", getAddr(field));
            log("Field/1", field.getDeclaringClass().getName(), field.getName(), field.getModifiers());
            throwJs("Field offset must not be zero");
        }
        return offset;
    }

    @Alias(names = "java_lang_reflect_Field_get_Ljava_lang_ObjectLjava_lang_Object")
    public static Object Field_get(Field field, Object instance) {
        log("Field_get", getAddr(field), getAddr(instance));
        int addr = getFieldAddr(field, instance);
        // if the field is native, we have to wrap it
        Class<?> clazz = field.getType();
        String clazzName = clazz.getName();
        switch (clazzName) {
            case "boolean":
                return read8(addr) > 0;
            case "byte":
                return read8(addr);
            case "short":
                return read16s(addr);
            case "char":
                return read16u(addr);
            case "int":
                return read32(addr);
            case "long":
                return read64(addr);
            case "float":
                return read32f(addr);
            case "double":
                return read64f(addr);
            default:
                return ptrTo(read32(addr));
        }
    }

    private static int getFieldAddr(Field field, Object instance) {
        int offset = getFieldOffset(field);
        if (Modifier.isStatic(field.getModifiers())) {
            return findStatic(getClassIndex(field.getDeclaringClass()), offset);
        } else {
            if (instance == null) throw new NullPointerException("getFieldAddr");
            return getAddr(instance) + offset;
        }
    }

    @Alias(names = "java_lang_reflect_Field_set_Ljava_lang_ObjectLjava_lang_ObjectV")
    public static void Field_set(Field field, Object instance, Object value) {
        int addr = getFieldAddr(field, instance);
        // log("Field.set()", addr, getAddr(value));
        // log("Field.newValue", String.valueOf(value));
        boolean isInstanceOfType = field.getType().isInstance(value);
        if (!isInstanceOfType) {
            throw new ClassCastException("Cannot set " + field + " to " + instance);
        }
        // log("Field.type vs newType", field.getType().getName(), value == null ? null : value.getClass().getName());
        // log("Field.instanceOf?", isInstanceOfType ? "true" : "false");
        switch (field.getType().getName()) {
            case "boolean":
                write8(addr, (byte) (((Boolean) value) ? 1 : 0));
                break;
            case "byte":
                write8(addr, (Byte) value);
                break;
            case "short":
                write16(addr, (Short) value);
                break;
            case "char":
                write16(addr, (Character) value);
                break;
            case "int":
                write32(addr, (Integer) value);
                break;
            case "long":
                write64(addr, (Long) value);
                break;
            case "float":
                write32(addr, (Float) value);
                break;
            case "double":
                write64(addr, (Double) value);
                break;
            default:
                write32(addr, getAddr(value));
                break;
        }
    }

    @Alias(names = "java_lang_reflect_Field_setInt_Ljava_lang_ObjectIV")
    public static void Field_setInt(Field field, Object instance, int value) {
        Class<?> clazz = field.getType();
        if (!"int".equals(clazz.getName())) throw new IllegalArgumentException("Type is not an integer");
        write32(getFieldAddr(field, instance), value);
    }

    @Alias(names = "java_lang_reflect_Field_getInt_Ljava_lang_ObjectI")
    public static int Field_getInt(Field field, Object instance) {
        Class<?> clazz = field.getType();
        if (!"int".equals(clazz.getName())) throw new IllegalArgumentException("Type is not an integer");
        return read32(getFieldAddr(field, instance));
    }

    @Alias(names = "java_lang_reflect_Field_setFloat_Ljava_lang_ObjectFV")
    public static void Field_setFloat(Field field, Object instance, float value) {
        Class<?> clazz = field.getType();
        if (!"float".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a float");
        write32(getFieldAddr(field, instance), value);
    }

    @Alias(names = "java_lang_reflect_Field_getFloat_Ljava_lang_ObjectF")
    public static float Field_getFloat(Field field, Object instance) {
        Class<?> clazz = field.getType();
        if (!"float".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a float");
        return read32f(getFieldAddr(field, instance));
    }

    @Alias(names = "java_lang_reflect_Field_setLong_Ljava_lang_ObjectJV")
    public static void Field_setLong(Field field, Object instance, long value) {
        Class<?> clazz = field.getType();
        if (!"int".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a long");
        write32(getFieldAddr(field, instance), value);
    }

    @Alias(names = "java_lang_reflect_Field_getLong_Ljava_lang_ObjectJ")
    public static long Field_getLong(Field field, Object instance) {
        Class<?> clazz = field.getType();
        if (!"long".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a long");
        return read64(getFieldAddr(field, instance));
    }

    @Alias(names = "java_lang_reflect_Field_setDouble_Ljava_lang_ObjectDV")
    public static void Field_setDouble(Field field, Object instance, double value) {
        Class<?> clazz = field.getType();
        if (!"double".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a double");
        write64(getFieldAddr(field, instance), value);
    }

    @Alias(names = "java_lang_reflect_Field_getDouble_Ljava_lang_ObjectD")
    public static double Field_getDouble(Field field, Object instance) {
        Class<?> clazz = field.getType();
        if (!"double".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a double");
        return read64f(getFieldAddr(field, instance));
    }

    @Alias(names = "java_lang_reflect_Field_setByte_Ljava_lang_ObjectBV")
    public static void Field_setByte(Field field, Object instance, byte value) {
        Class<?> clazz = field.getType();
        if (!"byte".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a byte");
        write8(getFieldAddr(field, instance), value);
    }

    @Alias(names = "java_lang_reflect_Field_getByte_Ljava_lang_ObjectB")
    public static byte Field_getByte(Field field, Object instance) {
        Class<?> clazz = field.getType();
        if (!"byte".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a byte");
        return read8(getFieldAddr(field, instance));
    }

    @Alias(names = "java_lang_reflect_Field_setShort_Ljava_lang_ObjectSV")
    public static void Field_setShort(Field field, Object instance, short value) {
        Class<?> clazz = field.getType();
        if (!"short".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a short");
        write16(getFieldAddr(field, instance), value);
    }

    @Alias(names = "java_lang_reflect_Field_getShort_Ljava_lang_ObjectS")
    public static short Field_getShort(Field field, Object instance) {
        Class<?> clazz = field.getType();
        if (!"short".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a short");
        return read16s(getFieldAddr(field, instance));
    }

    @Alias(names = "java_lang_reflect_Field_setChar_Ljava_lang_ObjectCV")
    public static void Field_setChar(Field field, Object instance, char value) {
        Class<?> clazz = field.getType();
        if (!"char".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a char");
        write16(getFieldAddr(field, instance), value);
    }

    @Alias(names = "java_lang_reflect_Field_getChar_Ljava_lang_ObjectC")
    public static char Field_getChar(Field field, Object instance) {
        Class<?> clazz = field.getType();
        if (!"char".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a char");
        return read16u(getFieldAddr(field, instance));
    }

    @Alias(names = "java_lang_reflect_Field_setBoolean_Ljava_lang_ObjectZV")
    public static void Field_setBoolean(Field field, Object instance, boolean value) {
        Class<?> clazz = field.getType();
        if (!"boolean".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a boolean");
        write8(getFieldAddr(field, instance), (byte) (value ? 1 : 0));
    }

    @Alias(names = "java_lang_reflect_Field_getBoolean_Ljava_lang_ObjectZ")
    public static boolean Field_getBoolean(Field field, Object instance) {
        Class<?> clazz = field.getType();
        if (!"boolean".equals(clazz.getName())) throw new IllegalArgumentException("Type is not a boolean");
        return read8(getFieldAddr(field, instance)) != 0;
    }

    @SuppressWarnings("rawtypes")
    private static HashMap<String, Class> classesForName;

    @SuppressWarnings("rawtypes")
    private static HashMap<String, Class> getClassesForName() {
        HashMap<String, Class> values = classesForName;
        if (values == null) {
            JavaReflect.classesForName = values = new HashMap<>(numClasses());
            for (int i = 0, l = numClasses(); i < l; i++) {
                Class<Object> clazz = findClass(i);
                values.put(clazz.getName(), clazz);
            }
        }
        return values;
    }

    @Alias(names = "java_lang_Class_forName_Ljava_lang_StringLjava_lang_Class")
    public static <V> Class<V> Class_forName(String name) throws ClassNotFoundException {
        @SuppressWarnings("unchecked")
        Class<V> clazz = (Class<V>) getClassesForName().get(name);
        if (clazz == null) log("Missing class, returning null", name);
        return clazz;
        // avoid throwing exceptions, so we can compile without them
        // if (clazz != null) return clazz;
        // throw new ClassNotFoundException();
    }

    @Alias(names = "java_lang_Class_forName_Ljava_lang_StringZLjava_lang_ClassLoaderLjava_lang_Class")
    public static <V> Class<V> Class_forName(String name, ClassLoader loader) throws ClassNotFoundException {
        return Class_forName(name);
    }

    @Alias(names = "java_lang_Class_isArray_Z")
    public static <V> boolean Class_isArray(Class<V> clazz) {
        return clazz.getName().charAt(0) == '[';
    }

    @Alias(names = "java_lang_Class_getEnclosingClass_Ljava_lang_Class")
    public static <V> Class<V> Class_getEnclosingClass(Class<V> clazz) {
        // todo could be found at compile time (massive speedup from O(|classes|) to O(1))
        String name = clazz.getName();
        int lio1 = name.lastIndexOf('$');
        int lio2 = name.lastIndexOf('.');
        lio1 = Math.max(lio1, lio2);// find the better index
        if (lio1 < 0) return null;
        String subName = name.substring(0, lio1);
        try {
            return Class_forName(subName);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Alias(names = "java_lang_Class_isLocalClass_Z")
    public static <V> boolean java_lang_Class_isLocalClass_Z(Class<V> clazz) {
        return Class_getEnclosingClass(clazz) != null;
    }

    @NoThrow
    @Alias(names = "java_lang_Class_checkMemberAccess_ILjava_lang_ClassZV")
    public static <V> void java_lang_Class_checkMemberAccess_ILjava_lang_ClassZV(Class<V> self, int a, Class<V> b, boolean c) {
        // security stuff... whatever...
    }

    @NoThrow
    @Alias(names = "java_lang_Class_registerNatives_V")
    public static void java_lang_Class_registerNatives_V() {

    }

    @Alias(names = "java_lang_Class_isPrimitive_Z")
    public static <V> boolean java_lang_Class_isPrimitive_Z(Class<V> clazz) {
        switch (clazz.getName()) {
            case "boolean":
            case "byte":
            case "char":
            case "short":
            case "float":
            case "integer":
            case "double":
            case "long":
                return true;
            default:
                return false;
        }
    }

    @Alias(names = "java_lang_ClassLoader_loadClass_Ljava_lang_StringZLjava_lang_Class")
    public static <V> Class<V> ClassLoader_loadClass(ClassLoader self, String name, boolean resolve) throws ClassNotFoundException {
        return Class_forName(name);
    }

    @NoThrow
    @Alias(names = "java_lang_ClassLoader_getSystemClassLoader_Ljava_lang_ClassLoader")
    public static ClassLoader ClassLoader_getSystemClassLoader() {
        return classLoaderInstance;
    }

    @NoThrow
    @Alias(names = "java_lang_Class_getClassLoader_Ljava_lang_ClassLoader")
    public static ClassLoader Class_getClassLoader(Class<Object> clazz) {
        return classLoaderInstance;
    }

    @NoThrow
    @Alias(names = "java_lang_Object_getClass_Ljava_lang_Class")
    public static int Object_getClass(int instance) {
        return findClassPtr(readClassId(instance));
    }

    @Alias(names = "java_lang_Object_hashCode_I")
    public static int Object_hashCode(Object instance) {
        return getAddr(instance);
    }

    @Alias(names = "java_lang_Class_getComponentType_Ljava_lang_Class")
    public static Class<?> Class_getComponentType_Ljava_lang_Class(Class<?> clazz) {
        switch (clazz.getName()) {
            case "[Z":
                return Boolean.class;
            case "[B":
                return Byte.class;
            case "[C":
                return Character.class;
            case "[S":
                return Short.class;
            case "[F":
                return Float.class;
            case "[I":
                return Integer.class;
            case "[D":
                return Double.class;
            case "[J":
                return Long.class;
            case "[V":
                return Void.class;
            case "[]":
                return Object.class;
            default:
                return null;
        }
    }

    @Alias(names = "java_lang_Class_getPrimitiveClass_Ljava_lang_StringLjava_lang_Class")
    public static Class<?> Class_getPrimitiveClass(String name) {
        // we need to get them by index, because the Java compiler
        //  returns nonsense like Short.TYPE, which calls this very method to be initialized
        int classIdx;
        switch (name) {
            case "boolean":
                classIdx = NATIVE_BOOLEAN;
                break;
            case "byte":
                classIdx = NATIVE_BYTE;
                break;
            case "char":
                classIdx = NATIVE_CHAR;
                break;
            case "short":
                classIdx = NATIVE_SHORT;
                break;
            case "int":
                classIdx = NATIVE_INT;
                break;
            case "long":
                classIdx = NATIVE_LONG;
                break;
            case "float":
                classIdx = NATIVE_FLOAT;
                break;
            case "double":
                classIdx = NATIVE_DOUBLE;
                break;
            case "void":
                classIdx = NATIVE_VOID;
                break;
            default:
                // avoid exceptions
                throwJs("getPrimitiveClass", name);
                return null;
        }
        return findClass(classIdx);
    }

    @NoThrow
    public static <V> int getClassIndex(Class<V> clazz) {
        if (clazz == null) return -1;
        return readI32AtOffset(clazz, OFFSET_CLASS_INDEX);
    }

    @NoThrow
    @Alias(names = "java_lang_Class_getModifiers_I")
    public static <V> int getClassModifiers(Class<V> clazz) {
        if (clazz == null) return 0;
        return readI32AtOffset(clazz, OFFSET_CLASS_MODIFIERS);
    }

    @Alias(names = "java_lang_Class_getSuperclass_Ljava_lang_Class")
    public static Class<Object> Class_getSuperclass_Ljava_lang_Class(Class<?> clazz) {
        int idx = getClassIndex(clazz);
        if (idx <= 0 || idx >= numClasses()) return null;
        int superClassIdx = getSuperClassId(idx);
        return findClass(superClassIdx);
    }

    @NoThrow
    @WASM(code = "global.get $resourceTable")
    static native int getResourcePtr();

    @Alias(names = "java_lang_ClassLoader_getResourceAsStream_Ljava_lang_StringLjava_io_InputStream")
    public static InputStream ClassLoader_getResourceAsStream_Ljava_lang_StringLjava_io_InputStream(ClassLoader cl, String name) {
        if (name == null) return null;
        int ptr = getResourcePtr();
        int length = read32(ptr);
        // todo if we have more than 16 resources, use a HashMap
        ptr += 4;
        int endPtr = ptr + (length << 3);
        while (ptr < endPtr) {
            String key = readPtrAtOffset(null, ptr);
            if (name.equals(key)) {
                byte[] bytes = readPtrAtOffset(null, ptr + 4);
                return new ByteArrayInputStream(bytes);
            }
            ptr += 8;
        }
        return null;
    }

    private static final Object[] empty = new Object[0];

    @Alias(names = "java_lang_reflect_Field_getDeclaredAnnotations_AW")
    public static Object[] Field_getDeclaredAnnotations(Field field) {
        // todo implement annotation instances
        if (field == null) throw new NullPointerException("getDeclaredAnnotations");
        return empty;
    }

    @Alias(names = "java_lang_reflect_AccessibleObject_getDeclaredAnnotations_AW")
    public static Object[] AccessibleObject_getDeclaredAnnotations(AccessibleObject object) {
        return empty;
    }

    @Alias(names = "java_lang_Class_isAnonymousClass_Z")
    public static <V> boolean isAnonymousClass(Class<V> clazz) {
        return false;
    }

    @Alias(names = "kotlin_jvm_internal_ClassReference_getSimpleName_Ljava_lang_String")
    public static String ClassReference_getSimpleName(ClassReference c) {
        return c.getJClass().getSimpleName();
    }

    @Alias(names = "kotlin_reflect_jvm_internal_KClassImpl_getSimpleName_Ljava_lang_String")
    public static String KClassImpl_getSimpleName(ClassBasedDeclarationContainer c) {
        return Class_getSimpleName(c.getJClass());
    }

    @Alias(names = "kotlin_reflect_full_KClasses_getMemberFunctions_Lkotlin_reflect_KClassLjava_util_Collection")
    public static Collection<Object> KClasses_getMemberFunctions_Lkotlin_reflect_KClassLjava_util_Collection(ClassBasedDeclarationContainer clazz) {
        return Collections.emptyList();
    }

    @NoThrow
    @Alias(names = "java_lang_Class_getSimpleName_Ljava_lang_String")
    public static <V> String Class_getSimpleName(Class<V> c) {
        return readPtrAtOffset(c, OFFSET_CLASS_SIMPLE_NAME);
    }

    @Alias(names = "java_lang_reflect_Field_equals_Ljava_lang_ObjectZ")
    public static boolean Field_equals(Field f, Object o) {
        return f == o;
    }

    @Alias(names = "java_lang_Class_newInstance_Ljava_lang_Object")
    public static <V> Object java_lang_Class_newInstance_Ljava_lang_Object(Class<V> clazz) {
        if (clazz == null) throwJs();
        int classIndex = getClassIndex(clazz);
        if (classIndex < 0) throwJs();
        if (getDynamicTableSize(classIndex) == 0) {
            log("Cannot instantiate clazz", classIndex, clazz == null ? null : clazz.getName());
            return null;
        }
        int constructorPtr = resolveIndirectByClass(classIndex, 4); // (id+1)<<2, id = 0
        int instance = createInstance(classIndex);
        // log("Creating class instance", classIndex, constructorPtr);
        // todo it would be good, if we have a value for stackPush/stackPop here
        callConstructor(instance, constructorPtr);
        return ptrTo(instance);
    }

    // @WASM(code = anyMethodThrows ? "call_indirect (type $iXi)" : "call_indirect (type $iX)")
    private static native void callConstructor(int obj, int methodPtr);

    @Alias(names = "java_lang_Class_toString_Ljava_lang_String")
    public static String java_lang_Class_toString_Ljava_lang_String(Class<?> clazz) {
        // we could have to implement isInterface() otherwise
        return clazz.getName();
    }

    @Alias(names = "java_lang_Class_getAnnotations_AW")
    public static Annotation[] java_lang_Class_getAnnotations_AW(Class<?> clazz) {
        // todo implement this properly
        return new Annotation[0];
    }

    @Alias(names = "java_lang_Class_isInstance_Ljava_lang_ObjectZ")
    public static boolean java_lang_Class_isInstance_Ljava_lang_ObjectZ(Class<?> clazz, Object instance) {
        if (instance == null) return false;
        if (instance.getClass() == clazz) return true;
        int classIndex = getClassIndex(clazz);
        if (classIndex >= 0) {
            return instanceOf(instance, classIndex);
        }
        return false;
    }

    @Alias(names = "java_lang_Class_getGenericInterfaces_AW")
    public static Object[] java_lang_Class_getGenericInterfaces_AW() {
        return empty; // not yet implemented / supported
    }

    @Alias(names = "java_lang_reflect_Array_newArray_Ljava_lang_ClassILjava_lang_Object")
    public static Object Array_newArray_Ljava_lang_ClassILjava_lang_Object(Class<?> clazz, int length) {
        String name = clazz.getName();
        switch (name) {
            case "int":
                return new int[length];
            case "long":
                return new long[length];
            case "byte":
                return new byte[length];
            case "short":
                return new short[length];
            case "char":
                return new char[length];
            case "float":
                return new float[length];
            case "double":
                return new double[length];
            default:
                return new Object[length];
        }
    }

    @Alias(names = "java_lang_reflect_AccessibleObject_checkAccess_Ljava_lang_ClassLjava_lang_ClassLjava_lang_ObjectIV")
    public static <A, B> void AccessibleObject_checkAccess_Ljava_lang_ClassLjava_lang_ClassLjava_lang_ObjectIV(Object self, Class<A> clazz, Class<B> clazz2, Object obj, int x) {
    }

    @Alias(names = "java_lang_Class_reflectionData_Ljava_lang_ClassXReflectionData")
    public static Object Class_reflectionData_Ljava_lang_ClassXReflectionData(Object self) {
        throw new RuntimeException("Cannot ask for reflection data, won't work");
    }

    @Alias(names = "java_lang_Class_privateGetPublicMethods_AW")
    public static <V> Method[] Class_privateGetPublicMethods_AW(Class<V> self) {
        return Class_getDeclaredMethods(self);
    }

    @Alias(names = "java_lang_Class_getConstructors_AW")
    public static <V> Object[] Class_getConstructors_AW(Class<V> self)
            throws NoSuchFieldException, IllegalAccessException {
        if (getDynamicTableSize(getClassIndex(self)) == 0) return empty; // not constructable class
        return new Object[]{getConstructorWithoutArgs(self)}; // to do better implementation?
    }

    @Alias(names = "java_lang_Class_getDeclaredConstructors_AW")
    public static <V> Object[] Class_getDeclaredConstructors_AW(Class<V> self)
            throws NoSuchFieldException, IllegalAccessException {
        return Class_getConstructors_AW(self);
    }

    @Alias(names = "java_lang_Class_privateGetDeclaredMethods_ZAW")
    public static <V> Method[] Class_privateGetDeclaredMethods_ZAW(Class<V> self, boolean sth) {
        return Class_getDeclaredMethods(self);
    }

    private static Constructor<Object>[] constructors;

    @Alias(names = "java_lang_Class_getConstructor_AWLjava_lang_reflect_Constructor")
    public static <V> Constructor<V> getConstructor(Class<V> self, Object[] args)
            throws NoSuchFieldException, IllegalAccessException {
        if (args == null) {
            throwJs("Arguments was null?");
            return null;
        }
        if (args.length > 0) {
            throwJs("Cannot access constructors with arguments");
            return null;
        }
        return getConstructorWithoutArgs(self);
    }

    private static <V> Constructor<V> getConstructorWithoutArgs(Class<V> clazz) throws NoSuchFieldException, IllegalAccessException {
        if (constructors == null) {
            //noinspection unchecked
            constructors = new Constructor[numClasses()];
        }
        int idx = getClassIndex(clazz);
        Constructor<Object> cs = constructors[idx];
        if (cs == null) {
            cs = ptrTo(createInstance(getClassIndex(Constructor.class)));
            Constructor.class.getDeclaredField("clazz").set(cs, clazz);
            constructors[idx] = cs;
        }
        //noinspection unchecked
        return (Constructor<V>) cs;
    }

    @Alias(names = "java_lang_reflect_Constructor_getParameterCount_I")
    private static int java_lang_reflect_Constructor_getParameterCount_I(Object self) {
        return 0; // anything else isn't supported at the moment
    }

    @Alias(names = "java_lang_reflect_Constructor_equals_Ljava_lang_ObjectZ")
    public static boolean java_lang_reflect_Constructor_equals_Ljava_lang_ObjectZ(Object self, Object other) {
        return self == other;
    }

    @Alias(names = "java_lang_reflect_Constructor_toString_Ljava_lang_String")
    public static String java_lang_reflect_Constructor_toString_Ljava_lang_String(Object self) {
        return self.getClass().getName();
    }

    @Alias(names = "java_lang_reflect_Constructor_getDeclaredAnnotations_AW")
    public static Object[] Constructor_getDeclaredAnnotations_AW(Object self) {
        return empty;
    }

    @UsedIfIndexed
    @Alias(names = "java_lang_reflect_Executable_getDeclaredAnnotations_AW")
    public static Object[] Executable_getDeclaredAnnotations_AW(Object self) {
        return empty;
    }

    @Alias(names = "java_lang_reflect_Constructor_newInstance_AWLjava_lang_Object")
    public static <V> V Constructor_newInstance_AWLjava_lang_Object(Constructor<V> self, Object[] args)
            throws InstantiationException, IllegalAccessException {
        if (args != null && args.length != 0)
            throw new IllegalArgumentException("Constructors with arguments aren't yet supported in WASM");
        return self.getDeclaringClass().newInstance();
    }

    @Alias(names = "java_lang_Class_getInterfaces_AW")
    public static Object[] java_lang_Class_getInterfaces_AW(Object self) {
        return empty;// to do -> not really used anyway
    }

    @Alias(names = "static_java_lang_ClassLoaderXParallelLoaders_V")
    public static void static_java_lang_ClassLoaderXParallelLoaders_V() {
    }

    @Alias(names = "java_lang_ClassLoaderXParallelLoaders_register_Ljava_lang_ClassZ")
    public static boolean java_lang_ClassLoaderXParallelLoaders_register_Ljava_lang_ClassZ(Object clazz) {
        // idc
        return false;
    }

    @Alias(names = "java_lang_Class_getAnnotation_Ljava_lang_ClassLjava_lang_annotation_Annotation")
    public static Annotation java_lang_Class_getAnnotation_Ljava_lang_ClassLjava_lang_annotation_Annotation(Object self, Object annotClass) {
        // todo implement properly
        return null;
    }

    @Alias(names = "java_lang_ClassLoader_loadLibrary0_Ljava_lang_ClassLjava_io_FileZ")
    public static boolean java_lang_ClassLoader_loadLibrary0_Ljava_lang_ClassLjava_io_FileZ(Object clazz, File file) {
        return false;
    }

    @Alias(names = "java_lang_ClassLoaderXNativeLibrary_finalize_V")
    public static void java_lang_ClassLoaderXNativeLibrary_finalize_V(Object self) {
    }

    @Alias(names = "java_lang_ClassLoader_loadLibrary_Ljava_lang_ClassLjava_lang_StringZV")
    public static void java_lang_ClassLoader_loadLibrary_Ljava_lang_ClassLjava_lang_StringZV(Object clazz, String file, boolean sth) {
    }

    @Alias(names = "java_lang_reflect_Executable_getParameters_AW")
    public static Object[] Executable_getParameters(Object self) {
        return empty;// todo implement for panel-constructors...
    }

    @Alias(names = "java_lang_reflect_Executable_getAnnotation_Ljava_lang_ClassLjava_lang_annotation_Annotation")
    public static Object java_lang_reflect_Executable_getAnnotation_Ljava_lang_ClassLjava_lang_annotation_Annotation(Object self, Object clazz) {
        return null;
    }

    @Alias(names = "java_lang_reflect_Executable_sharedGetParameterAnnotations_AWABAAWn")
    public static Object[] Executable_sharedGetParameterAnnotations_AWABAAW(Object[] classes, Object bytes) {
        return empty;
    }

    @Alias(names = "java_lang_reflect_Executable_isSynthetic_Z")
    public static boolean Executable_isSynthetic_Z(Object self) {
        // we could return here true for lambdas...
        return false;
    }

    @Alias(names = "java_lang_reflect_Executable_isVarArgs_Z")
    public static boolean Executable_isVarArgs_Z(Object self) {
        return false; // not really needed at runtime, only for the compiler, is it?
    }

    @Alias(names = "java_lang_reflect_Executable_getGenericParameterTypes_AW")
    public static Object[] Executable_getGenericParameterTypes_AW(Object self) {
        // shall return generics... I don't think we need that at runtime
        return empty;
    }

    @Alias(names = "java_lang_reflect_AccessibleObject_setAccessible0_Ljava_lang_reflect_AccessibleObjectZV")
    public static void AccessibleObject_setAccessible0_Ljava_lang_reflect_AccessibleObjectZV(Object self, boolean accessible) {
        // nothing to do
    }

    @Alias(names = "java_lang_reflect_AccessibleObject_isAccessible_Z")
    public static boolean AccessibleObject_isAccessibleZ(Object self) {
        return true;
    }

    @Alias(names = "java_lang_reflect_AccessibleObject_setAccessible_ZV")
    public static void java_lang_reflect_AccessibleObject_setAccessible_ZV(Object self, boolean accessible) {
        // nothing to do
    }
}
