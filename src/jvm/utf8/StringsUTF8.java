package jvm.utf8;

import annotations.Alias;
import annotations.NoThrow;

import java.nio.charset.Charset;
import java.util.Locale;

import static jvm.JVM32.*;
import static jvm.JavaLang.*;

public class StringsUTF8 {

    // can be set to false for better performance
    // then, the values must not be changed
    static boolean copyToString = false;
    static boolean copyGetBytes = false;

    @Alias(name = "java_lang_String_getBytes_AB")
    public static byte[] String_getBytes(String self) {
        byte[] data = getValue(self);
        if (copyGetBytes) {
            int length = data.length;
            byte[] clone = new byte[length];
            System.arraycopy(data, 0, clone, 0, length);
            return clone;
        } else return data;
    }

    @Alias(name = "java_lang_String_getBytes_Ljava_nio_charset_CharsetAB")
    public static byte[] String_getBytes(String self, Charset cs) {
        return String_getBytes(self);
    }

    @Alias(name = "new_java_lang_String_ACIIV")
    public static void new_java_lang_String_ACIIV(String self, char[] chars, int start, int end) {
        // todo if any char is > 127, use a better method
        int l = end - start;
        byte[] v1 = new byte[l];
        for (int i = 0; i < l; i++) {
            v1[i] = (byte) chars[i + start];
        }
        setValue(self, v1);
    }

    @Alias(name = "new_java_lang_String_ABLjava_nio_charset_CharsetV")
    public static void new_java_lang_String_ABLjava_nio_charset_CharsetV(String self, byte[] bytes, Charset charset) {
        new_java_lang_String_ABV(self, bytes);
    }

    @Alias(name = "new_java_lang_String_ABIILjava_nio_charset_CharsetV")
    public static void new_java_lang_String_ABIILjava_nio_charset_CharsetV(String self, byte[] bytes, int start, int end, Charset charset) {
        new_java_lang_String_ABIIV(self, bytes, start, end);
    }

    @Alias(name = "new_java_lang_String_ABV")
    public static void new_java_lang_String_ABV(String self, byte[] bytes) {
        if (copyToString) {
            int length = bytes.length;
            byte[] clone = new byte[length];
            System.arraycopy(bytes, 0, clone, 0, length);
            setValue(self, clone);
        } else setValue(self, bytes);
    }

    @Alias(name = "new_java_lang_String_ABIIV")
    public static void new_java_lang_String_ABIIV(String self, byte[] bytes, int start, int end) {
        if (start == 0 && end == bytes.length) {
            new_java_lang_String_ABV(self, bytes);
        } else {
            int length = end - start;
            byte[] v1 = new byte[length];
            System.arraycopy(bytes, start, v1, 0, length);
            setValue(self, v1);
        }
    }

    @Alias(name = "new_java_lang_String_ACV")
    public static void new_java_lang_String_ACV(String self, char[] chars) {
        new_java_lang_String_ACIIV(self, chars, 0, chars.length);
    }

    @Alias(name = "new_java_lang_String_ACZV")
    public static void new_java_lang_String_ACZV(String self, char[] chars, boolean ignored) {
        new_java_lang_String_ACIIV(self, chars, 0, chars.length);
    }

    @Alias(name = "java_lang_String_charAt_IC")
    public static int String_charAt(String str, int index) {
        return getValue(str)[index] & 255;
    }

    @Alias(name = "java_lang_String_regionMatches_ILjava_lang_StringIIZ")
    public static boolean String_regionMatches(String self, int i0, String other, int j0, int length) {
        return String_regionMatches(self, false, i0, other, j0, length);
    }

    @Alias(name = "java_lang_String_regionMatches_ZILjava_lang_StringIIZ")
    public static boolean String_regionMatches(String self, boolean ignoreCase, int i0, String other, int j0, int length) {
        byte[] v0 = getValue(self);
        byte[] v1 = getValue(other);
        int a0 = getAddr(v0) + arrayOverhead;
        int a1 = getAddr(v1) + arrayOverhead + j0;
        int e0 = a0 + v0.length;
        int i = i0;
        if (j0 >= 0 && i0 >= 0 && (long) i0 <= (long) v0.length - (long) length && (long) j0 <= (long) v1.length - (long) length) {
            byte b0;
            byte b1;
            do {
                final int discrepancy = findDiscrepancy(a0 + i, e0, a1 + i);
                if (unsignedLessThanEqual(discrepancy, e0)) return true;// they are the same :)
                if (!ignoreCase) return false;
                i = discrepancy - a0;
                final byte b0i = read8(discrepancy);
                final byte b1i = read8(a1 + i);
                b0 = toUpperCase(b0i);
                b1 = toUpperCase(b1i);
            } while (b0 == b1 || toLowerCase(b0) == toLowerCase(b1));
        }
        return false;
    }

    @Alias(name = "java_lang_String_indexOf_III")
    public static int String_indexOf(String self, int code, int idx) {
        if (code < 128) {
            byte[] v0 = getValue(self);
            for (int i = idx, l = v0.length; i < l; i++) {
                if (v0[i] == code) {
                    return i;
                }
            }
        }
        // else todo implement for special characters
        return -1;
    }

    @Alias(name = "java_lang_String_lastIndexOf_III")
    public static int String_lastIndexOf(String self, int code, int idx) {
        if (code < 128) {
            byte[] v0 = getValue(self);
            for (int i = Math.min(idx, v0.length - 1); i >= 0; --i) {
                if (v0[i] == code) {
                    return i;
                }
            }
        }
        // else todo implement for special characters
        return -1;
    }

    @Alias(name = "java_lang_String_indexOf_Ljava_lang_StringII")
    public static int String_indexOf(String self, String other, int idx) {
        if (other.length() == 0) return 0;
        for (int i = idx, l = self.length() - other.length(); i <= l; i++) {
            if (self.startsWith(other, i)) return i;
        }
        return -1;
    }

    @Alias(name = "java_lang_String_startsWith_Ljava_lang_StringIZ")
    public static boolean String_startsWith(String self, String other, int offset) {
        // return String_regionMatches(self, false, offset, other, 0, other.length());// todo why is this other method not working?
        byte[] v0 = getValue(self);
        byte[] v1 = getValue(other);
        int l0 = v0.length;
        int l1 = v1.length;
        if (l0 - offset < l1) return false;
        int a0 = getAddr(v0) + arrayOverhead + offset;
        int b0 = getAddr(v1) + arrayOverhead;
        int a1 = a0 + l1;
        return findDiscrepancy(a0, a1, b0) == a1;
    }

    @Alias(name = "java_lang_String_substring_IILjava_lang_String")
    public static String String_substring(String self, int start, int end) {
        byte[] v0 = getValue(self);
        if (start == 0 && end == v0.length) return self;
        if (start == end) return ""; // special case :)
        int length = end - start;
        byte[] v1 = new byte[length];
        System.arraycopy(v0, start, v1, 0, length);
        return newString(v1);
    }

    @Alias(name = "java_lang_String_substring_ILjava_lang_String")
    public static String String_substring(String self, int start) {
        return String_substring(self, start, self.length());
    }

    public static byte toUpperCase(byte b) {
        int c = b - 'a';
        if (unsignedLessThan(c, 26)) {
            return (byte) (c + 'A');
        } else return b;
    }

    public static byte toLowerCase(byte b) {
        int c = b - 'A';
        if (unsignedLessThan(c, 26)) {
            return (byte) (c + 'a');
        } else return b;
    }

    @SuppressWarnings("StringEquality")
    @Alias(name = "java_lang_String_compareTo_Ljava_lang_StringI")
    public static int String_compareTo(String self, String other) {
        if (self == other) return 0;
        if (other == null) throw new NullPointerException("String.compareTo");
        int v0 = getAddr(getValue(self));
        int v1 = getAddr(getValue(other));
        int a0 = v0 + objectOverhead;
        int b0 = v1 + objectOverhead;
        int l0 = read32(a0);
        int l1 = read32(b0);
        // skip length
        a0 += 4;
        b0 += 4;
        int l = Math.min(l0, l1);
        int i = findDiscrepancy(a0, a0 + l, b0) - a0;
        if (i < l) return read8(a0 + i) - read8(b0 + i);
        return l0 - l1;
    }

    @Alias(name = "java_lang_String_equals_Ljava_lang_ObjectZ")
    public static boolean String_equals(String self, Object other) {
        if (self == other) return true;
        if (other instanceof String) {
            String otherS = (String) other;
            if (self.length() != otherS.length()) return false;
            if (self.hashCode() != otherS.hashCode()) return false; // slower at first, but faster afterwards :)
            return String_compareTo(self, otherS) == 0;
        } else return false;
    }

    @Alias(name = "java_lang_String_getChars_IIACIV")
    public static void String_getChars(String str, int start, int end, char[] dst, int dstStart) {
        byte[] data = str.getBytes();
        int length = end - start;
        for (int i = 0; i < length; i++) {
            dst[dstStart + i] = (char) data[start + i];
        }
    }

    @Alias(name = "java_lang_String_toCharArray_AC")
    public static char[] String_toCharArray(String str) {
        byte[] data = str.getBytes();
        int length = data.length;
        char[] result = new char[length];
        for (int i = 0; i < length; i++) {
            result[i] = (char) data[i];
        }
        return result;
    }

    @NoThrow
    private static byte[] getValue(String s) {
        return ptrTo(read32(getAddr(s) + objectOverhead));
    }

    private static void setValue(String s, byte[] value) {
        write32(getAddr(s) + objectOverhead, getAddr(value));
    }

    // most critical, will break switch-case statements, if not ascii
    @Alias(name = "java_lang_String_hashCode_I")
    public static int String_hashCode_I(String str) {
        int hashPtr = getAddr(str) + objectOverhead + 4;
        int hash = read32(hashPtr);
        byte[] data = getValue(str);
        int startPtr = getAddr(data) + arrayOverhead;
        int endPtr = startPtr + data.length;
        if (hash == 0) {
            while (unsignedLessThan(startPtr, endPtr)) {
                hash = hash * 31 + (read8(startPtr) & 255);
                startPtr++;
            }
            write32(hashPtr, hash);
        }
        return hash;
    }

    private static String newString(byte[] bytes) {
        String value = ptrTo(create(getClassIndex(String.class))); // string class
        setValue(value, bytes);
        return value;
    }

    @Alias(name = "java_lang_String_toLowerCase_Ljava_util_LocaleLjava_lang_String")
    public static String String_toLowerCase(String s, Locale lx) {
        if (s == null) return null;
        byte[] lc = null;
        byte[] uc = getValue(s);
        for (int i = 0, l = uc.length; i < l; i++) {
            byte c = uc[i];
            int d = c - 'A';
            if (unsignedLessThan(d, 26)) {
                if (lc == null) {
                    lc = new byte[s.length()];
                    System.arraycopy(uc, 0, lc, 0, i);
                }
                c = (byte) (d + 'a');
            }
            if (lc != null) lc[i] = c;
        }
        if (lc == null) return s;
        return newString(lc);
    }

    @Alias(name = "java_lang_String_toUpperCase_Ljava_util_LocaleLjava_lang_String")
    public static String String_toUpperCase(String s, Locale lx) {
        if (s == null) return null;
        byte[] lc = null;
        byte[] uc = getValue(s);
        for (int i = 0, l = uc.length; i < l; i++) {
            byte c = uc[i];
            int d = c - 'a';
            if (unsignedLessThan(d, 26)) {
                if (lc == null) {
                    lc = new byte[s.length()];
                    System.arraycopy(uc, 0, lc, 0, i);
                }
                c = (byte) (d + 'A');
            }
            if (lc != null) lc[i] = c;
        }
        if (lc == null) return s;
        return newString(lc);
    }

    @Alias(name = "java_lang_CharSequence_codePoints_Ljava_util_stream_IntStream")
    public static IntStreamV2 java_lang_CharSequence_codePoints_Ljava_util_stream_IntStream(String str) {
        return new IntStreamV2(str);
    }

    // java.lang.Integer would need to replaced;
    // potential gain:
    // - less allocations, because skipping char[]
    // - much smaller static {} function, because we can use String instead of char[]
    /*static final String digits = "0123456789abcdefghijklmnopqrstuvwxyz";
    static final String DigitTens = "0000000000111111111122222222223333333333444444444455555555556666666666777777777788888888889999999999";
    static final String DigitOnes = "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789";

    @NoThrow
    @Alias(name = "static_java_lang_Integer_V")
    public static void static_java_lang_Integer_V() {
    }

    @Alias(name = "java_lang_Integer_getChars_IIACV")
    public static void getChars(int value, int x0, char[] dst) {
        int i = x0;
        char minus = 0;
        if (value < 0) {
            minus = 45;
            value = -value;
        }

        int v100;
        int idx;
        while (value >= 65536) {
            v100 = value / 100;
            idx = value - ((v100 << 6) + (v100 << 5) + (v100 << 2)); // value - v/100*100
            value = v100;
            dst[--i] = DigitOnes.charAt(idx);
            dst[--i] = DigitTens.charAt(idx);
        }

        do {
            v100 = value * '쳍' >>> 19; // 52429?
            idx = value - ((v100 << 3) + (v100 << 1));
            dst[--i] = digits.charAt(idx);
            value = v100;
        } while (v100 != 0);

        if (minus != 0) {
            dst[--i] = minus;
        }

    }*/

}
