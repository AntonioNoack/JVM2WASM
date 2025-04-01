package jvm;

import annotations.Alias;
import annotations.NoThrow;

import static jvm.JVMShared.unsignedLessThan;

public class Chars {

    @NoThrow
    @Alias(names = "java_lang_Character_toUpperCase_II")
    public static int java_lang_Character_toUpperCase_II(int c) {
        if (unsignedLessThan(c - 'a', 26)) {
            c += 'A' - 'a';
        }
        return c;
    }

    @NoThrow
    @Alias(names = "java_lang_Character_toUpperCase_CC")
    public static char java_lang_Character_toUpperCase_CC(char c) {
        if (unsignedLessThan(c - 'a', 26)) {
            c += 'A' - 'a';
        }
        return c;
    }

    @NoThrow
    @Alias(names = "java_lang_Character_toLowerCase_CC")
    public static char java_lang_Character_toLowerCase_CC(char c) {
        if (unsignedLessThan(c - 'A', 26)) {
            c += 'a' - 'A';
        }
        return c;
    }

    @NoThrow
    @Alias(names = "java_lang_Character_toLowerCase_II")
    public static int java_lang_Character_toLowerCase_II(int c) {
        if (unsignedLessThan(c - 'A', 26)) {
            c += 'a' - 'A';
        }
        return c;
    }

    @NoThrow
    @Alias(names = "java_lang_Character_isWhitespace_IZ")
    public static boolean java_lang_Character_isWhitespace_IZ(int code) {
        return code == ' ' | code == '\t' | code == '\n' | code == '\r';
    }

    @NoThrow
    @Alias(names = "java_lang_Character_isLowerCase_IZ")
    public static boolean java_lang_Character_isLowerCase_IZ(int code) {
        return unsignedLessThan(code - 'a', 26);
    }

    @NoThrow
    @Alias(names = "java_lang_Character_isUpperCase_IZ")
    public static boolean java_lang_Character_isUpperCase_IZ(int code) {
        return unsignedLessThan(code - 'A', 26);
    }

    @NoThrow
    @Alias(names = "java_lang_Character_isSpaceChar_IZ")
    public static boolean java_lang_Character_isSpaceChar_IZ(int code) {
        return code == ' ';
    }

    @NoThrow
    @Alias(names = "java_lang_Character_isLetter_IZ")
    public static boolean java_lang_Character_isLetter_IZ(int code) {
        return unsignedLessThan(code - 'A', 26) | unsignedLessThan(code - 'a', 26);
    }

    @NoThrow
    @Alias(names = "java_lang_Character_isDigit_IZ")
    public static boolean java_lang_Character_isDigit_IZ(int code) {
        return unsignedLessThan(code - '0', 10);
    }

    @NoThrow
    @Alias(names = "java_lang_Character_isLetterOrDigit_IZ")
    public static boolean java_lang_Character_isLetterOrDigit_IZ(int code) {
        return java_lang_Character_isLetter_IZ(code) | java_lang_Character_isDigit_IZ(code);
    }

}
