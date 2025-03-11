package jvm;

import annotations.Alias;
import annotations.NoThrow;
import annotations.WASM;

import java.util.List;

import static jvm.JavaLang.Object_toString;

@SuppressWarnings("rawtypes")
public class Kotlin {

    @NoThrow
    @Alias(names = "kotlin_UnsignedKt_uintToDouble_ID")
    @WASM(code = "f64.convert_i32_u")
    public static native double UnsignedKt_uintToDouble_ID(int v);

    @NoThrow
    @Alias(names = "kotlin_UnsignedKt_ulongToDouble_JD")
    @WASM(code = "f64.convert_i64_u")
    public static native double UnsignedKt_ulongToDouble_JD(long v);

    // there is more that could be optimized, e.g.,
    // kotlin_UnsignedKt_uintCompare_III

    @NoThrow
    @Alias(names = "kotlin_ExceptionsKt__ExceptionsKt_addSuppressed_Ljava_lang_ThrowableLjava_lang_ThrowableV")
    public static void ExceptionsKt__ExceptionsKt_addSuppressed_Ljava_lang_ThrowableLjava_lang_ThrowableV(Throwable a, Throwable b) {
        // whatever this is...
    }

    @NoThrow // safe by using Kotlin language
    @Alias(names = "kotlin_jvm_internal_Intrinsics_checkNotNullParameter_Ljava_lang_ObjectLjava_lang_StringV")
    @WASM(code = "drop drop")
    public static native void Intrinsics_checkNotNullParameter(Object o, String s);

	/*@NoThrow // unsafe
	@Alias(names = "kotlin_jvm_internal_Intrinsics_checkNotNullExpressionValue_Ljava_lang_ObjectLjava_lang_StringV")
	@WASM(code = "drop drop")
	public static native void Intrinsics_checkNotNullExpressionValue(Object o, String s);*/

    @Alias(names = "kotlin_text_StringsKt__StringNumberConversionsJVMKt_toFloatOrNull_Ljava_lang_StringLjava_lang_Float")
    public static Float Float_toFloatOrNull(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Alias(names = "kotlin_text_StringsKt__StringBuilderJVMKt_clear_Ljava_lang_StringBuilderLjava_lang_StringBuilder")
    public static StringBuilder StringBuilderJVMKt_clear(StringBuilder self) {
        self.setLength(0);
        return self;
    }

    @Alias(names = "kotlin_enums_EnumEntriesKt_enumEntries_AWLkotlin_enums_EnumEntries")
    public static List<Object> EnumEntriesKt_enumEntries(Enum[] values) {
        return new ArrayWrapper<>(values, 0, values.length);
    }

    @Alias(names = "kotlin_jvm_internal_Lambda_toString_Ljava_lang_String")
    public static String Lambda_toString_Ljava_lang_String(Object lambda) {
        return Object_toString(lambda);
    }

    @Alias(names = "kotlin_jvm_internal_AdaptedFunctionReference_toString_Ljava_lang_String")
    public static String AdaptedFunctionReference_toString_Ljava_lang_String(Object lambda) {
        return Object_toString(lambda);
    }

}
