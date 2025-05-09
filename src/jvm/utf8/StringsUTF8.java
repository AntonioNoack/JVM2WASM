package jvm.utf8;

import annotations.Alias;
import annotations.NoThrow;
import annotations.PureJavaScript;
import jvm.Pointer;

import java.nio.charset.Charset;
import java.util.Locale;

import static jvm.JVMShared.*;
import static jvm.JavaReflect.getClassId;
import static jvm.Pointer.add;
import static jvm.Pointer.diff;
import static utils.StaticFieldOffsets.OFFSET_STRING_HASH;
import static utils.StaticFieldOffsets.OFFSET_STRING_VALUE;

public class StringsUTF8 {

    // can be set to false for better performance
    // then, the values must not be changed
    static boolean copyToString = false;
    static boolean copyGetBytes = false;

    @Alias(names = "java_lang_String_getBytes_AB")
    public static byte[] String_getBytes(String self) {
        byte[] chars = getValue(self);
        if (copyGetBytes) {
            int length = chars.length;
            byte[] clone = new byte[length];
            System.arraycopy(chars, 0, clone, 0, length);
            return clone;
        } else return chars;
    }

    @Alias(names = "java_lang_String_getBytes_Ljava_nio_charset_CharsetAB")
    public static byte[] String_getBytes(String self, Charset ignored) {
        return String_getBytes(self);
    }

    @Alias(names = "new_java_lang_String_ACIIV")
    public static void new_java_lang_String_ACIIV(String self, char[] chars, int start, int length) {
        // todo if any char is > 127, use a better method
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) chars[i + start];
        }
        setValue(self, bytes);
    }

    @Alias(names = "new_java_lang_String_ABLjava_nio_charset_CharsetV")
    public static void new_java_lang_String_ABLjava_nio_charset_CharsetV(String self, byte[] bytes, Charset ignored) {
        new_java_lang_String_ABV(self, bytes);
    }

    @Alias(names = "new_java_lang_String_ABIILjava_nio_charset_CharsetV")
    public static void new_java_lang_String_ABIILjava_nio_charset_CharsetV(String self, byte[] bytes, int start, int end, Charset ignored) {
        new_java_lang_String_ABIIV(self, bytes, start, end);
    }

    @Alias(names = "new_java_lang_String_ABV")
    public static void new_java_lang_String_ABV(String self, byte[] src) {
        if (copyToString) {
            int length = src.length;
            byte[] clone = new byte[length];
            System.arraycopy(src, 0, clone, 0, length);
            setValue(self, clone);
        } else setValue(self, src);
    }

    @Alias(names = "new_java_lang_String_ABIIV")
    public static void new_java_lang_String_ABIIV(String self, byte[] src, int start, int end) {
        if (start == 0 && end == src.length) {
            new_java_lang_String_ABV(self, src);
        } else {
            int length = end - start;
            byte[] clone = new byte[length];
            System.arraycopy(src, start, clone, 0, length);
            setValue(self, clone);
        }
    }

    @Alias(names = "new_java_lang_String_ACV")
    public static void new_java_lang_String_ACV(String self, char[] chars) {
        new_java_lang_String_ACIIV(self, chars, 0, chars.length);
    }

    @Alias(names = "new_java_lang_String_ACZV")
    public static void new_java_lang_String_ACZV(String self, char[] chars, boolean ignored) {
        new_java_lang_String_ACIIV(self, chars, 0, chars.length);
    }

    @Alias(names = "java_lang_String_charAt_IC")
    public static char String_charAt(String str, int index) {
        return (char) (getValue(str)[index] & 255);
    }

    @Alias(names = "java_lang_String_regionMatches_ILjava_lang_StringIIZ")
    public static boolean String_regionMatches(String self, int i0, String other, int j0, int length) {
        return String_regionMatches(self, false, i0, other, j0, length);
    }

    @Alias(names = "java_lang_String_regionMatches_ZILjava_lang_StringIIZ")
    public static boolean String_regionMatches(String strA, boolean ignoreCase, int offsetA, String strB, int offsetB, int numCharsToCompare) {
        byte[] v0 = getValue(strA);
        byte[] v1 = getValue(strB);
        if (offsetA < 0 || offsetA + numCharsToCompare > v0.length) return false;
        if (offsetB < 0 || offsetB + numCharsToCompare > v1.length) return false;
        for (int i = 0; i < numCharsToCompare; i++) {
            byte b0 = v0[offsetA + i];
            byte b1 = v1[offsetB + i];
            if (b0 != b1) return false;
        }
        return true;
    }

    // todo mark this as a native, fast variant???
    public static boolean String_regionMatchesFast(String strA, boolean ignoreCase, int offsetA, String strB, int offsetB, int numCharsToCompare) {
        byte[] v0 = getValue(strA);
        byte[] v1 = getValue(strB);
        if (offsetA < 0 || offsetA + numCharsToCompare > v0.length) return false;
        if (offsetB < 0 || offsetB + numCharsToCompare > v1.length) return false;
        Pointer a0 = add(castToPtr(v0), (long) arrayOverhead + offsetA);
        Pointer a1 = add(castToPtr(v1), (long) arrayOverhead + offsetB);
        Pointer e0 = add(a0, numCharsToCompare);
        while (true) {
            final Pointer diff0 = findDiscrepancy(a0, e0, a1);
            if (Pointer.unsignedGreaterThanEqual(diff0, e0)) return true;// they are the same :) -> match
            if (!ignoreCase) return false; // they are different -> no match
            a1 = add(a1, Pointer.sub(diff0, a0)); // advance a1
            a0 = diff0;
            byte b0 = toUpperCase(read8(a0));
            byte b1 = toUpperCase(read8(a1));
            if (b0 != b1) return false; // even with ignored case, they are different -> no match
            a0 = add(a0, 1); // advance to next position
            a1 = add(a1, 1);
        }
    }

    @Alias(names = "java_lang_String_indexOf_III")
    public static int String_indexOf(String self, int code, int idx) {
        byte code1 = (byte) code;
        byte[] chars = getValue(self);
        for (int i = idx, l = chars.length; i < l; i++) {
            if (chars[i] == code1) {
                return i;
            }
        }
        return -1;
    }

    @Alias(names = "java_lang_String_lastIndexOf_III")
    public static int String_lastIndexOf(String self, int code, int idx) {
        byte code1 = (byte) code;
        byte[] chars = getValue(self);
        for (int i = Math.min(idx, chars.length - 1); i >= 0; --i) {
            if (chars[i] == code1) {
                return i;
            }
        }
        return -1;
    }

    @Alias(names = "java_lang_String_indexOf_Ljava_lang_StringII")
    public static int String_indexOf(String self, String other, int idx) {
        if (other.isEmpty()) return 0; // this case is needed for self=other=""
        for (int i = idx, l = self.length() - other.length(); i <= l; i++) {
            if (self.startsWith(other, i)) return i;
        }
        return -1;
    }

    @Alias(names = "java_lang_String_startsWith_Ljava_lang_StringIZ")
    public static boolean String_startsWith(String self, String other, int offset) {
        return String_regionMatches(self, false, offset, other, 0, other.length());
    }

    @Alias(names = "java_lang_String_substring_IILjava_lang_String")
    public static String String_substring(String self, int start, int end) {
        byte[] src = getValue(self);
        if (start == 0 && end == src.length) return self;
        if (start == end) return ""; // special case :)
        int length = end - start;
        byte[] dst = new byte[length];
        System.arraycopy(src, start, dst, 0, length);
        return newString(dst);
    }

    @Alias(names = "java_lang_String_substring_ILjava_lang_String")
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
    @Alias(names = "java_lang_String_compareTo_Ljava_lang_StringI")
    public static int String_compareTo(String strA, String strB) {
        if (strA == strB) return 0;
        if (strB == null) throw new NullPointerException("String.compareTo");
        byte[] charsA = getValue(strA);
        byte[] charsB = getValue(strB);
        int l0 = strA.length();
        int l1 = strB.length();
        int l = Math.min(l0, l1);
        for (int i = 0; i < l; i++) {
            byte bA = charsA[i];
            byte bB = charsB[i];
            if (bA != bB) return bA - bB;
        }
        return l0 - l1;
    }

    @SuppressWarnings("StringEquality")
    public static int String_compareToFast(String self, String other) {
        if (self == other) return 0;
        if (other == null) throw new NullPointerException("String.compareTo");
        Pointer chars0 = castToPtr(getValue(self));
        Pointer chars1 = castToPtr(getValue(other));
        Pointer a0 = add(chars0, objectOverhead);
        Pointer b0 = add(chars1, objectOverhead);
        int l0 = read32(a0);
        int l1 = read32(b0);
        // skip length
        a0 = add(a0, 4);
        b0 = add(b0, 4);
        int l = Math.min(l0, l1);
        long i = diff(findDiscrepancy(a0, add(a0, l), b0), a0);
        if (i < l) return read8(add(a0, i)) - read8(add(b0, i));
        return l0 - l1;
    }

    @Alias(names = "java_lang_String_equals_Ljava_lang_ObjectZ")
    public static boolean String_equals(String self, Object other) {
        if (self == other) return true;
        if (other instanceof String) {
            String otherS = (String) other;
            if (self.length() != otherS.length()) return false;
            if (self.hashCode() != otherS.hashCode()) return false; // slower at first, but faster afterward :)
            return String_compareTo(self, otherS) == 0;
        } else return false;
    }

    @Alias(names = "java_lang_String_replace_CCLjava_lang_String")
    public static String String_replace(String self, char search, char replace) {
        if (search == replace || self.indexOf(search) < 0) return self;
        byte search1 = (byte) search;
        byte replace1 = (byte) replace;
        // todo if search > 127 || replace > 127, this won't work properly
        byte[] data = self.getBytes();
        byte[] clone = new byte[data.length];
        for (int i = 0, l = data.length; i < l; i++) {
            byte di = data[i];
            clone[i] = di == search1 ? replace1 : di;
        }
        return newString(clone);
    }

    @Alias(names = "java_lang_String_getChars_IIACIV")
    public static void String_getChars(String str, int start, int end, char[] dst, int dstStart) {
        byte[] src = getValue(str);
        int length = end - start;
        // todo if char > 127, this won't work properly
        for (int i = 0; i < length; i++) {
            dst[dstStart + i] = (char) src[start + i];
        }
    }

    @Alias(names = "java_lang_String_toCharArray_AC")
    public static char[] String_toCharArray(String str) {
        byte[] src = getValue(str);
        int length = src.length;
        char[] dst = new char[length];
        String_getChars(str, 0, length, dst, 0);
        return dst;
    }

    @NoThrow
    @PureJavaScript(code = "return arg0.value;")
    private static byte[] getValue(String s) {
        return readPtrAtOffset(s, OFFSET_STRING_VALUE);
    }

    @NoThrow
    @PureJavaScript(code = "arg0.value = arg1;")
    private static void setValue(String s, byte[] value) {
        writePtrAtOffset(s, OFFSET_STRING_VALUE, value);
    }

    @NoThrow
    @PureJavaScript(code = "return arg0.hash;")
    private static int getHashCode(String str) {
        return readI32AtOffset(str, OFFSET_STRING_HASH);
    }

    @NoThrow
    @PureJavaScript(code = "arg0.hash = arg1;")
    private static void setHashCode(String str, int hashCode) {
        writeI32AtOffset(str, OFFSET_STRING_HASH, hashCode);
    }

    // most critical, will break switch-case statements, if not ascii
    @Alias(names = "java_lang_String_hashCode_I")
    public static int String_hashCode_I(String self) {
        int hash = getHashCode(self);
        if (hash == 0) {
            for (int i = 0, l = self.length(); i < l; i++) {
                hash = hash * 31 + self.charAt(i);
            }
            setHashCode(self, hash);
        }
        return hash;
    }

    private static String newString(byte[] bytes) {
        int classIndex = getClassId(String.class); // will be 10
        String value = unsafeCast(createInstance(classIndex)); // string class
        setValue(value, bytes);
        return value;
    }

    @Alias(names = {
            "java_lang_String_toLowerCase_Ljava_util_LocaleLjava_lang_String",
            "java_lang_String_toLowerCase_Ljvm_custom_LocaleLjava_lang_String"
    })
    public static String String_toLowerCase(String s, Locale lx) {
        if (s == null) return null;
        byte[] output = null;
        byte[] input = getValue(s);
        for (int i = 0, l = input.length; i < l; i++) {
            byte c = input[i];
            int d = c - 'A';
            if (unsignedLessThan(d, 26)) {
                if (output == null) {
                    output = new byte[s.length()];
                    System.arraycopy(input, 0, output, 0, i);
                }
                c = (byte) (d + 'a');
            }
            if (output != null) output[i] = c;
        }
        if (output == null) return s;
        return newString(output);
    }

    @Alias(names = {
            "java_lang_String_toUpperCase_Ljava_util_LocaleLjava_lang_String",
            "java_lang_String_toUpperCase_Ljvm_custom_LocaleLjava_lang_String"
    })
    public static String String_toUpperCase(String s, Locale lx) {
        if (s == null) return null;
        byte[] output = null;
        byte[] input = getValue(s);
        for (int i = 0, l = input.length; i < l; i++) {
            byte c = input[i];
            int d = c - 'a';
            if (unsignedLessThan(d, 26)) {
                if (output == null) {
                    output = new byte[s.length()];
                    System.arraycopy(input, 0, output, 0, i);
                }
                c = (byte) (d + 'A');
            }
            if (output != null) output[i] = c;
        }
        if (output == null) return s;
        return newString(output);
    }

    @Alias(names = "java_lang_CharSequence_codePoints_Ljava_util_stream_IntStream")
    public static IntStreamV2 java_lang_CharSequence_codePoints_Ljava_util_stream_IntStream(String str) {
        return new IntStreamV2(str);
    }

    // java.lang.Integer would need to replaced;
    // potential gain:
    // - fewer allocations, because skipping char[]
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
