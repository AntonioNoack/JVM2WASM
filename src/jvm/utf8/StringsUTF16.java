package jvm.utf8;

import annotations.Alias;

import java.util.Locale;

import static jvm.JVM32.unsignedLessThan;

public class StringsUTF16 {

    @Alias(names = "java_lang_String_toLowerCase_Ljava_util_LocaleLjava_lang_String")
    public static String String_toLowerCase(String s, Locale lx) {
        if (s == null) return null;
        char[] lc = null;
        for (int i = 0, l = s.length(); i < l; i++) {
            char c = s.charAt(i);
            int d = c - 'A';
            if (unsignedLessThan(d, 26)) {
                if (lc == null) {
                    lc = new char[s.length()];
                    for (int j = 0; j < i; j++) {
                        lc[j] = s.charAt(j);
                    }
                }
                c = (char) (d + 'a');
            }
            if (lc != null) lc[i] = c;
        }
        if (lc == null) return s;
        return new String(lc);
    }

    @Alias(names = "java_lang_String_toUpperCase_Ljava_util_LocaleLjava_lang_String")
    public static String String_toUpperCase(String s, Locale lx) {
        if (s == null) return null;
        char[] lc = null;
        for (int i = 0, l = s.length(); i < l; i++) {
            char c = s.charAt(i);
            int d = c - 'a';
            if (unsignedLessThan(d, 26)) {
                if (lc == null) {
                    lc = new char[s.length()];
                    for (int j = 0; j < i; j++) {
                        lc[j] = s.charAt(j);
                    }
                }
                c = (char) (d + 'A');
            }
            if (lc != null) lc[i] = c;
        }
        if (lc == null) return s;
        return new String(lc);
    }

}
