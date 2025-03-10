package jvm.custom;

import annotations.Alias;

public class Locale {
    private static final Locale INSTANCE = new Locale();

    // todo why is the name still mixed???
    @Alias(names = "jvm_custom_Locale_getDefault_Ljava_util_LocaleXCategoryLjvm_custom_Locale")
    public static Locale getDefault(Object category) {
        return INSTANCE;
    }
}
