package jvm;

import annotations.Alias;
import annotations.NoThrow;
import annotations.WASM;
import kotlin.jvm.internal.ClassBasedDeclarationContainer;
import kotlin.reflect.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static jvm.JVM32.findClass;
import static jvm.JVM32.numClasses;
import static jvm.JavaLang.getClassIndex;
import static jvm.JavaLang.ptrTo;

public class Kotlin {

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
			Class supa = jClass.getSuperclass();
			if (supa == null) return Collections.emptyList();
			return Collections.singletonList((KType) Reflection_getOrCreateKotlinClass(supa));
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
		return classes[getClassIndex(clazz)];
	}

	@Alias(names = "kotlin_jvm_internal_ReflectionFactory_getOrCreateKotlinClass_Ljava_lang_ClassLkotlin_reflect_KClass")
	public static <V> KClass<Object> ReflectionFactory_getOrCreateKotlinClass(Class<V> clazz) {
		if (clazz == null) return null;
		return classes[getClassIndex(clazz)];
	}

	@NoThrow
	@Alias(names = "kotlin_UnsignedKt_uintToDouble_ID")
	@WASM(code = "f64.convert_i32_u")
	public static native double kotlin_UnsignedKt_uintToDouble_ID(int v);

	@NoThrow
	@Alias(names = "kotlin_UnsignedKt_ulongToDouble_JD")
	@WASM(code = "f64.convert_i64_u")
	public static native double kotlin_UnsignedKt_ulongToDouble_JD(long v);

	// there is more that could be optimized, e.g.,
	// kotlin_UnsignedKt_uintCompare_III

	private static final KClass<Object>[] classes;

	static {
		int l = numClasses();
		KClass<Object>[] c = classes = new KClass[l];
		for (int i = 0; i < l; i++) { // a few reflection methods expect this class to be used
			Class<Object> z = ptrTo(findClass(i));
			c[i] = new WASMKotlin<>(z);
			// c[i] = new KClassImpl<>(z);
			// c[i] = new ClassReference(z);
		}
	}

	@NoThrow
	@Alias(names = "kotlin_ExceptionsKt__ExceptionsKt_addSuppressed_Ljava_lang_ThrowableLjava_lang_ThrowableV")
	public static void kotlin_ExceptionsKt__ExceptionsKt_addSuppressed_Ljava_lang_ThrowableLjava_lang_ThrowableV(Throwable a, Throwable b) {
		// whatever this is...
	}

	@NoThrow
	@Alias(names = "kotlin_jvm_internal_Intrinsics_checkNotNullParameter_Ljava_lang_ObjectLjava_lang_StringV")
	@WASM(code = "drop drop")
	public static native void kotlin_jvm_internal_Intrinsics_checkNotNullParameter_Ljava_lang_ObjectLjava_lang_StringV(Object o, String s);

	@Alias(names = "kotlin_text_StringsKt__StringNumberConversionsJVMKt_toFloatOrNull_Ljava_lang_StringLjava_lang_Float")
	public static Float toFloatOrNull(String s) {
		try {
			return Float.parseFloat(s);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	@Alias(names = "kotlin_reflect_full_KClasses_getSuperclasses_Lkotlin_reflect_KClassLjava_util_List")
	public static List<KClass<Object>> KClasses_getSuperclasses(KClass<Object> clazz) {
		Class clazz1 = ((ClassBasedDeclarationContainer) clazz).getJClass();
		Class superClass = clazz1.getSuperclass();
		if (superClass == null) return Collections.emptyList();
		else return Collections.singletonList(Reflection_getOrCreateKotlinClass(superClass));
	}

	@Alias(names = "kotlin_reflect_full_KClasses_getDeclaredMemberProperties_Lkotlin_reflect_KClassLjava_util_Collection")
	public static <V> Collection<V> kotlin_reflect_full_KClasses_getDeclaredMemberProperties_Lkotlin_reflect_KClassLjava_util_Collection(KClass<V> clazz) {
		// Class c = ((KClassImpl) clazz).getJClass();
		return Collections.emptyList();
	}

	@Alias(names = "kotlin_text_StringsKt__StringBuilderJVMKt_clear_Ljava_lang_StringBuilderLjava_lang_StringBuilder")
	public static StringBuilder clear(StringBuilder self) {
		self.setLength(0);
		return self;
	}

	@Alias(names = "kotlin_enums_EnumEntriesKt_enumEntries_ALjava_lang_EnumLkotlin_enums_EnumEntries")
	public static List<Object> enumEntries(Enum[] values) {
		return new ArrayWrapper<>(values, 0, values.length);
	}

}
