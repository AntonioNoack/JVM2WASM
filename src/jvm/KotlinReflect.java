package jvm;

import annotations.Alias;
import annotations.NoThrow;
import kotlin.jvm.internal.ClassBasedDeclarationContainer;
import kotlin.jvm.internal.ClassReference;
import kotlin.reflect.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static jvm.JVMShared.classIdToInstance;
import static jvm.JVMShared.numClasses;
import static jvm.JavaReflect.Class_getSimpleName;
import static jvm.JavaReflect.getClassId;

@SuppressWarnings("rawtypes")
public class KotlinReflect {

    static class WASMKotlin<V> implements KClass<V>, KType, ClassBasedDeclarationContainer {

        private final Class<V> jClass;

        public WASMKotlin(Class<V> jClass) {
            this.jClass = jClass;
        }

        @NotNull
        @Override
        public List<Annotation> getAnnotations() {
            return Arrays.asList(jClass.getAnnotations());
        }

        @NotNull
        @Override
        public Collection<KFunction<V>> getConstructors() {
            return Collections.emptyList();
        }

        @Override
        public boolean isAbstract() {
            return Modifier.isAbstract(jClass.getModifiers());
        }

        @Override
        public boolean isCompanion() {
            return jClass.getName().endsWith("$Companion");
        }

        @Override
        public boolean isData() {
            return false;
        }

        @Override
        public boolean isFinal() {
            return Modifier.isFinal(jClass.getModifiers());
        }

        @Override
        public boolean isFun() {
            return false;
        }

        @Override
        public boolean isInner() {
            return false;
        }

        @Override
        public boolean isOpen() {
            return !isFinal();
        }

        @Override
        public boolean isSealed() {
            return false;
        }

        @Override
        public boolean isValue() {
            return false;
        }

        @NotNull
        @Override
        public Collection<KCallable<?>> getMembers() {
            return Collections.emptyList();
        }

        @NotNull
        @Override
        public Collection<KClass<?>> getNestedClasses() {
            return Collections.emptyList();
        }

        @Nullable
        @Override
        public V getObjectInstance() {
            return null;
        }

        @Nullable
        @Override
        public String getQualifiedName() {
            return null;
        }

        @NotNull
        @Override
        public List<KClass<? extends V>> getSealedSubclasses() {
            return Collections.emptyList();
        }

        @Nullable
        @Override
        public String getSimpleName() {
            return jClass.getSimpleName();
        }

        @NotNull
        @Override
        public List<KType> getSupertypes() {
            Class<?> superclass = jClass.getSuperclass();
            if (superclass == null) return Collections.emptyList();
            return Collections.singletonList((KType) Reflection_getOrCreateKotlinClass(superclass));
        }

        @NotNull
        @Override
        public List<KTypeParameter> getTypeParameters() {
            return Collections.emptyList();
        }

        @Nullable
        @Override
        public KVisibility getVisibility() {
            int mod = jClass.getModifiers();
            if (Modifier.isPublic(mod)) return KVisibility.PUBLIC;
            if (Modifier.isPrivate(mod)) return KVisibility.PRIVATE;
            if (Modifier.isProtected(mod)) return KVisibility.PROTECTED;
            return KVisibility.INTERNAL;
        }

        @Override
        public boolean isInstance(@Nullable Object o) {
            return jClass.isInstance(o);
        }

        @NotNull
        @Override
        public List<KTypeProjection> getArguments() {
            return Collections.emptyList();
        }

        @Nullable
        @Override
        public KClassifier getClassifier() {
            return null;
        }

        @Override
        public boolean isMarkedNullable() {
            return false;
        }

        @NotNull
        @Override
        public Class<?> getJClass() {
            return jClass;
        }
    }

    @NoThrow
    @Alias(names = "kotlin_jvm_internal_Reflection_getOrCreateKotlinClass_Ljava_lang_ClassLkotlin_reflect_KClass")
    public static <V> KClass<Object> Reflection_getOrCreateKotlinClass(Class<V> clazz) {
        if (clazz == null) return null;
        return classes[getClassId(clazz)];
    }

    @Alias(names = "kotlin_jvm_internal_ReflectionFactory_getOrCreateKotlinClass_Ljava_lang_ClassLkotlin_reflect_KClass")
    public static <V> KClass<Object> ReflectionFactory_getOrCreateKotlinClass(Class<V> clazz) {
        if (clazz == null) return null;
        return classes[getClassId(clazz)];
    }

    private static final KClass<Object>[] classes;

    static {
        int l = numClasses();
        KClass<Object>[] c = classes = new KClass[l];
        for (int i = 0; i < l; i++) { // a few reflection methods expect this class to be used
            Class<Object> z = classIdToInstance(i);
            c[i] = new WASMKotlin<>(z);
            // c[i] = new KClassImpl<>(z);
            // c[i] = new ClassReference(z);
        }
    }

    // only used if Kotlyn isn't
    @SuppressWarnings("unchecked")
    @Alias(names = "kotlin_reflect_full_KClasses_getSuperclasses_Lkotlin_reflect_KClassLjava_util_List")
    public static List<KClass<Object>> KClasses_getSuperclasses(KClass<Object> clazz) {
        Class clazz1 = ((ClassBasedDeclarationContainer) clazz).getJClass();
        Class superClass = clazz1.getSuperclass();
        if (superClass == null) return Collections.emptyList();
        else return Collections.singletonList(Reflection_getOrCreateKotlinClass(superClass));
    }

    // only used if Kotlyn isn't
    @Alias(names = "kotlin_reflect_full_KClasses_getDeclaredMemberProperties_Lkotlin_reflect_KClassLjava_util_Collection")
    public static <V> Collection<V> kotlin_reflect_full_KClasses_getDeclaredMemberProperties_Lkotlin_reflect_KClassLjava_util_Collection(KClass<V> clazz) {
        // Class c = ((KClassImpl) clazz).getJClass();
        return Collections.emptyList();
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

}
