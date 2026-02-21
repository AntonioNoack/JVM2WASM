package jvm;

import annotations.*;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.HashMap;

import static jvm.JVMShared.*;
import static jvm.JavaReflectMethod.Constructor_invoke;
import static jvm.NativeLog.log;
import static jvm.Pointer.add;
import static jvm.ThrowJS.throwJs;
import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static utils.StaticClassIndices.*;
import static utils.StaticFieldOffsets.*;

public class JavaReflect {

    private static final ClassLoader classLoaderInstance = new ClassLoader() {
    };

    @NoThrow
    @NotNull
    @Alias(names = {"java_lang_Class_getFields_AW", "java_lang_Class_getDeclaredFields_AW"})
    @JavaScriptNative(code = "return arg0.fields;")
    public static Field[] getFields(Class<?> clazz) {
        return readPtrAtOffset(clazz, OFFSET_CLASS_FIELDS);
    }

    @NotNull
    @Alias(names = {"java_lang_Class_getEnumConstants_AW", "java_lang_Class_getEnumConstantsShared_AW"})
    public static <E extends Enum<E>> Enum<E>[] getEnumConstantsShared(Class<E> self) {
        // ensure that the class is loaded
        int classId = getClassId(self);
        int methodId = resolveInterfaceByClass(classId, 0);
        callStaticInit(methodId);
        //noinspection unchecked
        return (E[]) Field_get(Class_getField(self, "$VALUES"), null);
    }

    // needs to be assigned dynamically to differentiate between throwing/non-throwing functions
    // @WASM(code = "call_indirect (type $Xi)")
    public static native void callStaticInit(int methodId);

    @SuppressWarnings("rawtypes")
    @Alias(names = "java_lang_Class_getField_Ljava_lang_StringLjava_lang_reflect_Field")
    public static Field Class_getField(Class self, String name) {
        // using class instance, find fields
        if (self == null || name == null) throw new NullPointerException("Class.getField()");
        Class currClass = self;
        // log("looking for field", name);
        while (true) {
            Field[] fields = getFields(currClass);
            // log("class for fields", Class_getName_Ljava_lang_String(clazz));
            // log("fields*, length", getAddr(fields), fields.length);
            for (Field field : fields) {
                String fieldName = field.getName();
                // log("field[i].name,offset:", fieldName, getFieldOffset(field));
                if (name.equals(fieldName)) {
                    // log("Found field", clazz.getName(), name);
                    return field;
                }
            }
            // use parent class as well, so we need fewer entries in each array
            Class nextClass = currClass.getSuperclass();
            if (nextClass == null || currClass == nextClass) {
                log("Missing field", self.getName(), name);
                return null; // not really JVM-conform, but we don't want exception-throwing
            }
            currClass = nextClass;
        }
    }

    @Alias(names = "java_lang_Class_getDeclaredField_Ljava_lang_StringLjava_lang_reflect_Field")
    public static <V> Field Class_getDeclaredField(Class<V> clazz, String name) {
        return Class_getField(clazz, name);
    }

    @Alias(names = "java_lang_Class_getDeclaredMethods_AW")
    public static <V> Method[] Class_getDeclaredMethods(Class<V> clazz) {
        return Class_getMethods(clazz); // just return all of them :)
    }

    @Alias(names = "java_lang_Class_getMethods_AW")
    @JavaScriptNative(code = "return arg0.methods;")
    public static <V> Method[] Class_getMethods(Class<V> clazz) {
        return readPtrAtOffset(clazz, OFFSET_CLASS_METHODS);
    }

    @Alias(names = "java_lang_Class_isInterface_Z")
    public static boolean Class_isInterface_Z(Class<Object> self) {
        return (self.getModifiers() & ACC_INTERFACE) != 0;
    }

    @Alias(names = "java_lang_Class_getMethod_Ljava_lang_StringAWLjava_lang_reflect_Method")
    public static <V> Method Class_getMethod(Class<V> self, String name, Class<?>[] parameters) {
        for (Method method : Class_getMethods(self)) {
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
        if (!method.getName().equals(name)) return false;
        if (parameters.length != method.getParameterCount()) return false;
        return matchesParams(method.getParameterTypes(), parameters);
    }

    private static boolean matches(Constructor<?> method, Class<?>[] parameters) {
        int actualLength = parameters != null ? parameters.length : 0;
        if (actualLength != method.getParameterCount()) return false;
        return matchesParams(method.getParameterTypes(), parameters);
    }

    private static boolean matchesParams(Class<?>[] expected, Class<?>[] actual) {
        for (int i = 0, length = expected.length; i < length; i++) {
            if (expected[i] != actual[i]) {
                return false;
            }
        }
        return true;
    }

    public static int getFieldOffset(Field field) {
        int offset = readI32AtOffset(field, OFFSET_FIELD_SLOT);
        if (offset < objectOverhead && !Modifier.isStatic(field.getModifiers())) {
            log("Field/0", castToPtr(field));
            log("Field/1", field.getDeclaringClass().getName(), field.getName(), field.getModifiers());
            throwJs("Field offset must not be zero");
        }
        return offset;
    }

    @JavaScriptNative(code = "" +
            "let field = arg0, instance = arg1;\n" +
            "const jsClass = field.type.jsClass;\n" +
            "if(!instance) instance = jsClass;\n" +
            "let value = instance[field.jsName];\n" +
            "return applyBoxing(value, field.type);\n")
    @Alias(names = "java_lang_reflect_Field_get_Ljava_lang_ObjectLjava_lang_Object")
    public static Object Field_get(Field field, Object instance) {
        // log("Field_get", getAddr(field), getAddr(instance));
        int offset = getFieldOffsetSafely(field, instance);
        // if the field is native, we have to wrap it
        Class<?> clazz = field.getType();
        switch (getClassId(clazz)) {
            case NATIVE_BOOLEAN:
                return Boolean.valueOf(readI8AtOffset(instance, offset) > 0);
            case NATIVE_BYTE:
                return Byte.valueOf(readI8AtOffset(instance, offset));
            case NATIVE_SHORT:
                return Short.valueOf(readS16AtOffset(instance, offset));
            case NATIVE_CHAR:
                return Character.valueOf(readU16AtOffset(instance, offset));
            case NATIVE_INT:
                return Integer.valueOf(readI32AtOffset(instance, offset));
            case NATIVE_LONG:
                return Long.valueOf(readI64AtOffset(instance, offset));
            case NATIVE_FLOAT:
                return Float.valueOf(readF32AtOffset(instance, offset));
            case NATIVE_DOUBLE:
                return Double.valueOf(readF64AtOffset(instance, offset));
            default:
                return readPtrAtOffset(instance, offset);
        }
    }

    @JavaScriptNative(code = "return arg0.name;")
    private static int getFieldOffsetSafely(Field field, Object instance) {
        int offset = getFieldOffset(field);
        if (Modifier.isStatic(field.getModifiers())) {
            return findStatic(getClassId(field.getDeclaringClass()), offset);
        } else {
            if (instance == null) throw new NullPointerException("getFieldAddr");
            return offset;
        }
    }

    @Alias(names = "java_lang_reflect_Field_set_Ljava_lang_ObjectLjava_lang_ObjectV")
    public static void Field_set(Field field, Object instance, Object value) {
        // log("Field.set()", addr, getAddr(value));
        // log("Field.newValue", String.valueOf(value));
        boolean isInstanceOfType = field.getType().isInstance(value);
        if (!isInstanceOfType) {
            throw new ClassCastException("Cannot set " + field + " to " + instance);
        }
        // log("Field.type vs newType", field.getType().getName(), value == null ? null : value.getClass().getName());
        // log("Field.instanceOf?", isInstanceOfType ? "true" : "false");
        Field_setImpl(field, getClassId(field.getType()), instance, value);
    }

    @JavaScriptNative(code = "" +
            "let field = arg0;\n" +
            "let typeId = arg1;\n" +
            "let instance = arg2;\n" +
            "let value = arg3;\n" +
            "if (typeId >= 16 && typeId <= 23) {\n" + // convert wrapper object into native value
            "   value = value.value;\n" +
            "}\n" +
            "if (!instance) {\n" +
            "   instance = getJSClassByClass(field.clazz);\n" +
            "}\n" +
            "let fieldName = unwrapString(field.name);\n" +
            "instance[fieldName] = value;\n")
    private static void Field_setImpl(Field field, int typeClassId, Object instance, Object value) {
        int offset = getFieldOffsetSafely(field, instance);
        switch (typeClassId) {
            case NATIVE_BOOLEAN:
                boolean value1 = (Boolean) value;
                writeI8AtOffset(instance, offset, value1 ? (byte) 1 : (byte) 0);
                break;
            case NATIVE_BYTE:
                writeI8AtOffset(instance, offset, (Byte) value);
                break;
            case NATIVE_SHORT:
                writeI16AtOffset(instance, offset, (Short) value);
                break;
            case NATIVE_CHAR:
                writeI16AtOffset(instance, offset, (Character) value);
                break;
            case NATIVE_INT:
                writeF32AtOffset(instance, offset, (Integer) value);
                break;
            case NATIVE_LONG:
                writeF64AtOffset(instance, offset, (Long) value);
                break;
            case NATIVE_FLOAT:
                writeF32AtOffset(instance, offset, (Float) value);
                break;
            case NATIVE_DOUBLE:
                writeF64AtOffset(instance, offset, (Double) value);
                break;
            default:
                writePtrAtOffset(instance, offset, value);
                break;
        }
    }

    @Alias(names = "java_lang_reflect_Field_setInt_Ljava_lang_ObjectIV")
    public static void Field_setInt(Field field, Object instance, int value) {
        if (getClassId(field.getType()) != NATIVE_INT)
            throw new IllegalArgumentException("Type is not an integer");
        writeI32AtOffset(instance, getFieldOffsetSafely(field, instance), value);
    }

    @Alias(names = "java_lang_reflect_Field_getInt_Ljava_lang_ObjectI")
    public static int Field_getInt(Field field, Object instance) {
        if (getClassId(field.getType()) != NATIVE_INT)
            throw new IllegalArgumentException("Type is not an integer");
        return readI32AtOffset(instance, getFieldOffsetSafely(field, instance));
    }

    @Alias(names = "java_lang_reflect_Field_setFloat_Ljava_lang_ObjectFV")
    public static void Field_setFloat(Field field, Object instance, float value) {
        if (getClassId(field.getType()) != NATIVE_FLOAT)
            throw new IllegalArgumentException("Type is not a float");
        writeF32AtOffset(instance, getFieldOffsetSafely(field, instance), value);
    }

    @Alias(names = "java_lang_reflect_Field_getFloat_Ljava_lang_ObjectF")
    public static float Field_getFloat(Field field, Object instance) {
        if (getClassId(field.getType()) != NATIVE_FLOAT)
            throw new IllegalArgumentException("Type is not a float");
        return readF32AtOffset(instance, getFieldOffsetSafely(field, instance));
    }

    @Alias(names = "java_lang_reflect_Field_setLong_Ljava_lang_ObjectJV")
    public static void Field_setLong(Field field, Object instance, long value) {
        if (getClassId(field.getType()) != NATIVE_LONG)
            throw new IllegalArgumentException("Type is not a long");
        writeI64AtOffset(instance, getFieldOffsetSafely(field, instance), value);
    }

    @Alias(names = "java_lang_reflect_Field_getLong_Ljava_lang_ObjectJ")
    public static long Field_getLong(Field field, Object instance) {
        if (getClassId(field.getType()) != NATIVE_LONG)
            throw new IllegalArgumentException("Type is not a long");
        return readI64AtOffset(instance, getFieldOffsetSafely(field, instance));
    }

    @Alias(names = "java_lang_reflect_Field_setDouble_Ljava_lang_ObjectDV")
    public static void Field_setDouble(Field field, Object instance, double value) {
        if (getClassId(field.getType()) != NATIVE_DOUBLE)
            throw new IllegalArgumentException("Type is not a double");
        writeF64AtOffset(instance, getFieldOffsetSafely(field, instance), value);
    }

    @Alias(names = "java_lang_reflect_Field_getDouble_Ljava_lang_ObjectD")
    public static double Field_getDouble(Field field, Object instance) {
        if (getClassId(field.getType()) != NATIVE_DOUBLE)
            throw new IllegalArgumentException("Type is not a double");
        return readF64AtOffset(instance, getFieldOffsetSafely(field, instance));
    }

    @Alias(names = "java_lang_reflect_Field_setByte_Ljava_lang_ObjectBV")
    public static void Field_setByte(Field field, Object instance, byte value) {
        if (getClassId(field.getType()) != NATIVE_BYTE)
            throw new IllegalArgumentException("Type is not a byte");
        writeI8AtOffset(instance, getFieldOffsetSafely(field, instance), value);
    }

    @Alias(names = "java_lang_reflect_Field_getByte_Ljava_lang_ObjectB")
    public static byte Field_getByte(Field field, Object instance) {
        if (getClassId(field.getType()) != NATIVE_BYTE)
            throw new IllegalArgumentException("Type is not a byte");
        return readI8AtOffset(instance, getFieldOffsetSafely(field, instance));
    }

    @Alias(names = "java_lang_reflect_Field_setShort_Ljava_lang_ObjectSV")
    public static void Field_setShort(Field field, Object instance, short value) {
        if (getClassId(field.getType()) != NATIVE_SHORT)
            throw new IllegalArgumentException("Type is not a short");
        writeI16AtOffset(instance, getFieldOffsetSafely(field, instance), value);
    }

    @Alias(names = "java_lang_reflect_Field_getShort_Ljava_lang_ObjectS")
    public static short Field_getShort(Field field, Object instance) {
        if (getClassId(field.getType()) != NATIVE_SHORT)
            throw new IllegalArgumentException("Type is not a short");
        return readS16AtOffset(instance, getFieldOffsetSafely(field, instance));
    }

    @Alias(names = "java_lang_reflect_Field_setChar_Ljava_lang_ObjectCV")
    public static void Field_setChar(Field field, Object instance, char value) {
        if (getClassId(field.getType()) != NATIVE_CHAR)
            throw new IllegalArgumentException("Type is not a char");
        writeI16AtOffset(instance, getFieldOffsetSafely(field, instance), value);
    }

    @Alias(names = "java_lang_reflect_Field_getChar_Ljava_lang_ObjectC")
    public static char Field_getChar(Field field, Object instance) {
        if (getClassId(field.getType()) != NATIVE_CHAR)
            throw new IllegalArgumentException("Type is not a char");
        return readU16AtOffset(instance, getFieldOffsetSafely(field, instance));
    }

    @Alias(names = "java_lang_reflect_Field_setBoolean_Ljava_lang_ObjectZV")
    public static void Field_setBoolean(Field field, Object instance, boolean value) {
        if (getClassId(field.getType()) != NATIVE_BOOLEAN)
            throw new IllegalArgumentException("Type is not a boolean");
        writeI8AtOffset(instance, getFieldOffsetSafely(field, instance), (byte) (value ? 1 : 0));
    }

    @Alias(names = "java_lang_reflect_Field_getBoolean_Ljava_lang_ObjectZ")
    public static boolean Field_getBoolean(Field field, Object instance) {
        if (getClassId(field.getType()) != NATIVE_BOOLEAN)
            throw new IllegalArgumentException("Type is not a boolean");
        return readI8AtOffset(instance, getFieldOffsetSafely(field, instance)) != 0;
    }

    @SuppressWarnings("rawtypes")
    private static HashMap<String, Class> classesForName;

    @SuppressWarnings("rawtypes")
    private static HashMap<String, Class> getClassesForName() {
        HashMap<String, Class> values = classesForName;
        if (values == null) {
            JavaReflect.classesForName = values = new HashMap<>(numClasses());
            for (int i = 0, l = numClasses(); i < l; i++) {
                Class<Object> clazz = classIdToInstance(i);
                values.put(clazz.getName(), clazz);
            }
        }
        return values;
    }

    @Alias(names = "java_lang_Class_forName_Ljava_lang_StringLjava_lang_Class")
    public static <V> Class<V> Class_forName(String name) throws ClassNotFoundException {
        // usually doesn't support primitive classes, but I don't get why they wouldn't be supported
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
        int index = getClassId(clazz);
        return index >= FIRST_NATIVE && index <= LAST_NATIVE;
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
    @JavaScriptNative(code = "return arg0.constructor.CLASS_INSTANCE;")
    public static Object Object_getClass(Object instance) {
        return classIdToInstance(readClassId(instance));
    }

    @Alias(names = "java_lang_Object_hashCode_I")
    public static int Object_hashCode(Object instance) {
        return System.identityHashCode(instance);
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
    public static Class<?> Class_getPrimitiveClass(String name) throws ClassNotFoundException {
        // we need to get them by index, because the Java compiler
        //  returns nonsense like Short.TYPE, which calls this very method to be initialized
        return Class_forName(name);
    }

    @NoThrow
    @JavaScriptNative(code = "return arg0 ? arg0.index : -1;")
    public static <V> int getClassId(Class<V> self) {
        if (self == null) return -1;
        return readI32AtOffset(self, OFFSET_CLASS_INDEX);
    }

    @NoThrow
    @Alias(names = "java_lang_Class_getModifiers_I")
    @JavaScriptNative(code = "return arg0.modifiers")
    public static <V> int getClassModifiers(Class<V> self) {
        if (self == null) return 0;
        return readI32AtOffset(self, OFFSET_CLASS_MODIFIERS);
    }

    @Alias(names = "java_lang_Class_getSuperclass_Ljava_lang_Class")
    @JavaScriptNative(code = "return arg0.superClass;\n")
    public static Class<Object> Class_getSuperclass_Ljava_lang_Class(Class<?> clazz) {
        int classId = getClassId(clazz);
        if (classId <= 0 || classId >= numClasses()) return null;
        int superClassId = getSuperClassId(classId);
        return classIdToInstance(superClassId);
    }

    @NoThrow
    @WASM(code = "global.get $resourceTable")
    static native Pointer getResourcePtr();

    @Alias(names = "java_lang_ClassLoader_getResourceAsStream_Ljava_lang_StringLjava_io_InputStream")
    public static InputStream ClassLoader_getResourceAsStream_Ljava_lang_StringLjava_io_InputStream(ClassLoader cl, String name) {
        byte[] bytes = ClassLoader_getResourceAsByteArray(name);
        return bytes != null ? new ByteArrayInputStream(bytes) : null;
    }

    @JavaScriptNative(code = "return getResourceAsByteArray(arg0);")
    public static byte[] ClassLoader_getResourceAsByteArray(String name) {
        if (name == null) return null;
        Pointer ptr = getResourcePtr();
        int numResources = read32(ptr);
        // todo if we have more than 16 resources, use a HashMap
        ptr = add(ptr, 4);
        Pointer endPtr = add(ptr, numResources << 3);
        while (Pointer.unsignedLessThan(ptr, endPtr)) {
            String key = readPtrAtOffset(ptr, 0);
            if (name.equals(key)) {
                return readPtrAtOffset(ptr, 4);
            }
            ptr = add(ptr, 8);
        }
        return null;
    }

    private static final Object[] empty = new Object[0];

    @Alias(names = "java_lang_reflect_Field_getAnnotations_AW")
    @JavaScriptNative(code = "return arg0.annotations;")
    public static Object[] Field_getAnnotations(Field field) {
        return readPtrAtOffset(field, OFFSET_FIELD_ANNOTATIONS);
    }

    @Alias(names = "java_lang_reflect_Field_getDeclaredAnnotations_AW")
    public static Object[] Field_getDeclaredAnnotations(Field field) {
        return Field_getAnnotations(field);
    }

    @Alias(names = "java_lang_reflect_AccessibleObject_getDeclaredAnnotations_AW")
    public static Object[] AccessibleObject_getDeclaredAnnotations(AccessibleObject object) {
        return empty;
    }

    @Alias(names = "java_lang_Class_isAnonymousClass_Z")
    public static <V> boolean isAnonymousClass(Class<V> clazz) {
        return false;
    }

    @NoThrow
    @Alias(names = "java_lang_Class_getSimpleName_Ljava_lang_String")
    @JavaScriptNative(code = "return arg0.simpleName;")
    public static <V> String Class_getSimpleName(Class<V> c) {
        return readPtrAtOffset(c, OFFSET_CLASS_SIMPLE_NAME);
    }

    @Alias(names = "java_lang_reflect_Field_equals_Ljava_lang_ObjectZ")
    public static boolean Field_equals(Field f, Object o) {
        return f == o;
    }

    @Alias(names = "java_lang_Class_newInstance_Ljava_lang_Object")
    public static <V> Object Class_newInstance_Ljava_lang_Object(Class<V> clazz) throws NoSuchMethodException {
        Constructor<?> constructor = clazz.getConstructor((Class<?>[]) empty);
        //noinspection ConstantValue
        if (constructor == null) throw new IllegalStateException("Class doesn't have constructor without args");
        return Constructor_newInstance_AWLjava_lang_Object(constructor, empty);
    }

    @Alias(names = "java_lang_Class_toString_Ljava_lang_String")
    public static String Class_toString_Ljava_lang_String(Class<?> clazz) {
        // we could have to implement isInterface() otherwise
        return Class_getName_Ljava_lang_String(clazz);
    }

    @Alias(names = "java_lang_Class_getName_Ljava_lang_String")
    @JavaScriptNative(code = "return arg0.name;")
    public static String Class_getName_Ljava_lang_String(Class<?> self) {
        return readPtrAtOffset(self, OFFSET_CLASS_NAME);
    }

    @Alias(names = "java_lang_Class_getAnnotations_AW")
    public static Annotation[] Class_getAnnotations_AW(Class<?> clazz) {
        // todo implement this properly
        return new Annotation[0];
    }

    @Alias(names = "java_lang_Class_isInstance_Ljava_lang_ObjectZ")
    @JavaScriptNative(code = "return arg1 && arg1 instanceof getJSClassByClass(arg0);")
// todo we need to handle interfaces, too
    public static boolean Class_isInstance_Ljava_lang_ObjectZ(Class<?> clazz, Object instance) {
        if (instance == null) return false;
        int classIndex = getClassId(clazz);
        validateClassId(classIndex);
        return instanceOf(instance, classIndex);
    }

    @Alias(names = "java_lang_Class_getGenericInterfaces_AW")
    public static Object[] Class_getGenericInterfaces_AW() {
        return empty; // not yet implemented / supported
    }

    @Alias(names = "java_lang_reflect_Array_newArray_Ljava_lang_ClassILjava_lang_Object")
    public static Object Array_newArray_Ljava_lang_ClassILjava_lang_Object(Class<?> clazz, int length) {
        int classId = getClassId(clazz);
        if (classId < FIRST_NATIVE || classId > LAST_NATIVE) {
            // create object array
            classId = OBJECT_ARRAY;
        } else {
            // create native array
            classId = classId + INT_ARRAY - NATIVE_INT;
        }
        return createNativeArray1(length, classId);
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
    @JavaScriptNative(code = "return arg0.constructors;")
    public static <V> Constructor<V>[] Class_getConstructors_AW(Class<V> self) {
        return readPtrAtOffset(self, OFFSET_CLASS_CONSTRUCTORS);
    }

    @Alias(names = "java_lang_Class_getDeclaredConstructors_AW")
    public static <V> Constructor<V>[] Class_getDeclaredConstructors_AW(Class<V> self) {
        return Class_getConstructors_AW(self);
    }

    @Alias(names = "java_lang_Class_privateGetDeclaredMethods_ZAW")
    public static <V> Method[] Class_privateGetDeclaredMethods_ZAW(Class<V> self, boolean sth) {
        return Class_getDeclaredMethods(self);
    }

    @Alias(names = "java_lang_Class_getConstructor_AWLjava_lang_reflect_Constructor")
    public static <V> Constructor<V> Class_getConstructor(Class<V> self, Class<?>[] args) {
        if (self == null) return null; // really shouldn't happen
        Constructor<?>[] constructors = self.getConstructors();
        // log("Constructors:", self.getName(), constructors.length);
        for (Constructor<?> constructor : constructors) {
            if (matches(constructor, args)) {
                /*log("Found constructor", self.getName(),
                        constructor.getParameterTypes().length,
                        args != null ? args.length : 0);*/
                //noinspection unchecked
                return (Constructor<V>) constructor;
            }
        }
        return null;
    }

    @Alias(names = "java_lang_reflect_Method_getParameterTypes_AW")
    @JavaScriptNative(code = "return arg0.parameterTypes;")
    public static Class<?>[] Method_getParameterTypes_AW(Method self) {
        // avoid cloning, when we never modify these values anyway
        return readPtrAtOffset(self, OFFSET_METHOD_PARAMETER_TYPES);
    }

    @Alias(names = "java_lang_reflect_Constructor_getParameterTypes_AW")
    @JavaScriptNative(code = "return arg0.parameterTypes;")
    public static Class<?>[] Constructor_getParameterTypes_AW(Constructor<?> self) {
        // avoid cloning, when we never modify these values anyway
        return readPtrAtOffset(self, OFFSET_CONSTRUCTOR_PARAMETER_TYPES);
    }

    @Alias(names = "java_lang_reflect_Constructor_equals_Ljava_lang_ObjectZ")
    public static boolean Constructor_equals_Ljava_lang_ObjectZ(Object self, Object other) {
        return self == other;
    }

    @Alias(names = "java_lang_reflect_Constructor_toString_Ljava_lang_String")
    public static String Constructor_toString_Ljava_lang_String(Object self) {
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
    public static <V> V Constructor_newInstance_AWLjava_lang_Object(Constructor<V> self, Object[] args) {
        int classId = getClassId(self.getDeclaringClass());
        validateClassId(classId);
        Object instance = createInstance(classId);
        Constructor_invoke(self, instance, args);
        return unsafeCast(instance);
    }

    @Alias(names = "java_lang_Class_getInterfaces_AW")
    public static Object[] Class_getInterfaces_AW(Object self) {
        return empty;// to do -> not really used anyway
    }

    @Alias(names = "static_java_lang_ClassLoaderXParallelLoaders_V")
    public static void static_java_lang_ClassLoaderXParallelLoaders_V() {
    }

    @Alias(names = "java_lang_ClassLoaderXParallelLoaders_register_Ljava_lang_ClassZ")
    public static boolean ClassLoaderXParallelLoaders_register_Ljava_lang_ClassZ(Object clazz) {
        // idc
        return false;
    }

    @Alias(names = "java_lang_Class_getAnnotation_Ljava_lang_ClassLjava_lang_annotation_Annotation")
    public static Annotation Class_getAnnotation_Ljava_lang_ClassLjava_lang_annotation_Annotation(Class<?> self, Class<?> annotationClass) {
        for (Annotation instance : self.getAnnotations()) {
            if (annotationClass.isInstance(instance)) {
                return instance;
            }
        }
        return null;
    }

    @Alias(names = "java_lang_reflect_Field_getAnnotation_Ljava_lang_ClassLjava_lang_annotation_Annotation")
    public static Annotation Field_getAnnotation_Ljava_lang_ClassLjava_lang_annotation_Annotation(Field self, Class<?> annotationClass) {
        for (Annotation instance : self.getAnnotations()) {
            if (annotationClass.isInstance(instance)) {
                return instance;
            }
        }
        return null;
    }

    @Alias(names = "java_lang_ClassLoader_loadLibrary0_Ljava_lang_ClassLjava_io_FileZ")
    public static boolean ClassLoader_loadLibrary0_Ljava_lang_ClassLjava_io_FileZ(Object clazz, File file) {
        return false;
    }

    @Alias(names = "java_lang_ClassLoaderXNativeLibrary_finalize_V")
    public static void ClassLoaderXNativeLibrary_finalize_V(Object self) {
    }

    @Alias(names = "java_lang_ClassLoader_loadLibrary_Ljava_lang_ClassLjava_lang_StringZV")
    public static void ClassLoader_loadLibrary_Ljava_lang_ClassLjava_lang_StringZV(Object clazz, String file, boolean sth) {
    }

    @Alias(names = "java_lang_reflect_Executable_getParameters_AW")
    public static Object[] Executable_getParameters(Object self) {
        return empty;// todo implement for panel-constructors...
    }

    @Alias(names = "java_lang_reflect_Executable_getAnnotation_Ljava_lang_ClassLjava_lang_annotation_Annotation")
    public static Object Executable_getAnnotation_Ljava_lang_ClassLjava_lang_annotation_Annotation(Object self, Object clazz) {
        return null;
    }

    @Alias(names = "java_lang_reflect_Executable_sharedGetParameterAnnotations_AWABAAWn")
    public static Object[] Executable_sharedGetParameterAnnotations_AWABAAW(Object[] classes, Object bytes) {
        return empty;
    }

    @Alias(names = "java_lang_reflect_Executable_isSynthetic_Z")
    public static boolean Executable_isSynthetic_Z(Executable self) {
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
        // Safety? What's that??? 😆
        return true;
    }

    @Alias(names = "java_lang_reflect_AccessibleObject_setAccessible_ZV")
    public static void AccessibleObject_setAccessible_ZV(Object self, boolean accessible) {
        // nothing to do
    }
}
