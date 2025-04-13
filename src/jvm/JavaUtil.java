package jvm;

import annotations.Alias;
import annotations.JavaScript;
import annotations.NoThrow;
import annotations.RevAlias;
import jvm.custom.GMTTimeZone;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.ToIntFunction;

import static jvm.ArrayAccessSafe.arrayLength;
import static jvm.JVMFlags.is32Bits;
import static jvm.JVMFlags.ptrSizeBits;
import static jvm.JVMShared.*;
import static jvm.JavaLang.toFixed;
import static jvm.Pointer.add;
import static jvm.Pointer.getAddrS;

public class JavaUtil {

    @Alias(names = "java_util_TimeZone_setDefaultZone_Ljava_util_TimeZone")
    public static TimeZone java_util_TimeZone_setDefaultZone_Ljava_util_TimeZone() {
        return TimeZone.getTimeZone("GMT");
    }

    @Alias(names = "java_util_TimeZone_getTimeZone_Ljava_lang_StringZLjava_util_TimeZone")
    public static TimeZone java_util_TimeZone_getTimeZone_Ljava_lang_StringZLjava_util_TimeZone(String name, boolean stuff) {
        return GMTTimeZone.INSTANCE;
    }

    public static Object toFixed2(Object obj, int v) {
        if (obj instanceof BigDecimal) {
            // new DecimalFormat("0.00").format(obj);
            // suboptimal solution
            String str = obj.toString();
            int j = str.indexOf('.');
            int k = j + v;
            return j >= 0 && k < str.length() ? str.substring(0, k) : str;
        } else {
            return toFixed(((Number) obj).doubleValue(), v);
        }
    }

    private static int formatFloat(int i, int l, String format, StringBuilder result, Object obj) {
        if (i < l && obj instanceof Number) {
            char c = format.charAt(i++);
            if (c >= '0' && c <= '9' && i < l) {
                char d = format.charAt(i++);
                int v = c - '0';
                if (d >= '0' && d <= '9' && i < l) {
                    v = v * 10 + d - '0';
                    i++;
                }
                obj = toFixed2(obj, v);
            }
        }
        result.append(obj);
        return i;
    }

    private static Object formatHex(Object obj) {
        if (obj instanceof Integer) {
            return Integer.toHexString((int) obj);
        } else if (obj instanceof Long) {
            return Long.toHexString((long) obj);
        } else if (obj instanceof Byte) {
            return Integer.toHexString((byte) obj);
        } else if (obj instanceof Short) {
            return Integer.toHexString((short) obj);
        } else if (obj instanceof BigInteger) {
            return ((BigInteger) obj).toString(16);
        } else return obj;
    }

    @Alias(names = "java_lang_String_format_Ljava_lang_StringAWLjava_lang_String")
    public static String String_format(String format, Object[] args) {
        return String_format(null, format, args);
    }

    @Alias(names = {
            "java_lang_String_format_Ljava_util_LocaleLjava_lang_StringAWLjava_lang_String",
            "java_lang_String_format_Ljvm_custom_LocaleLjava_lang_StringAWLjava_lang_String"
    })
    public static String String_format(Locale locale, String format, Object[] args) {
        int idx = 0;
        StringBuilder result = new StringBuilder(format.length());
        for (int i = 0, l = format.length(); i < l; ) {
            char c = format.charAt(i++);
            if (c == '%' && i < l) {
                c = format.charAt(i++);
                Object obj = args != null && idx < args.length ? args[idx++] : null;
                // https://www.javatpoint.com/java-string-format
                switch (c) {
                    case '.':
                        i = formatFloat(i, l, format, result, obj);
                        break;
                    // todo respect digit limit and such :)
                    /*case 'f':
                        result.append(obj);
                        break;
                    case 'a':
                        // hex result of fp value
                        result.append(obj);
                        break;
                    case 'd':
                        result.append(obj);
                        break;*/
                    case 'x':
                        result.append(formatHex(obj));
                        break;
                    case 'h':
                        if (obj == null) result.append("null");
                        else result.append(obj.hashCode());
                        break;
                    case 'n':
                        result.append('\n');
                        idx--;
                        break;
                    case 'c':
                        if (obj instanceof Character) {
                            result.append((char) obj);
                        } else result.append(obj);
                        break;
                    default:
                        result.append('%');
                        result.append(c);
                        break;
                }
            } else result.append(c);
        }
        return result.toString();
    }

    @Alias(names = "java_util_Arrays_sort_AWIILjava_util_ComparatorV")
    public static <V> void Arrays_sort_AWIILjava_util_ComparatorV(V[] o, int start, int end, Comparator<V> c) {
        InPlaceStableSort.sort(start, end, o, c);
    }

    @Alias(names = "java_util_Arrays_sort_AWLjava_util_ComparatorV")
    public static <V> void Arrays_sort_AWLjava_util_ComparatorV(V[] o, Comparator<V> c) {
        InPlaceStableSort.sort(0, o.length, o, c);
    }

    private static Comparator<Object> comparator;

    @Alias(names = "java_util_Arrays_sort_AWV")
    public static <V> void Arrays_sort_AWV(V[] o) {
        if (comparator == null) //noinspection unchecked
            comparator = (a, b) -> ((Comparable<Object>) a).compareTo(b);
        InPlaceStableSort.sort(0, o.length, o, comparator);
    }

    @NoThrow
    @Alias(names = "static_java_util_ArraysXLegacyMergeSort_V")
    public static void static_java_util_ArraysXLegacyMergeSort_V() {
    }

    @Alias(names = "java_util_concurrent_locks_ReentrantLock_lock_V")
    public static void java_util_concurrent_locks_ReentrantLock_lock_V(ReentrantLock lock) {
    }

    @Alias(names = "java_util_concurrent_locks_ReentrantLock_unlock_V")
    public static void java_util_concurrent_locks_ReentrantLock_unlock_V(ReentrantLock lock) {
    }

    @Alias(names = "java_util_concurrent_locks_ReentrantLock_tryLock_Z")
    public static boolean java_util_concurrent_locks_ReentrantLock_tryLock_Z(ReentrantLock lock) {
        return true;
    }

    @NoThrow
    @JavaScript(code = "return Math.random()")
    public static native double seedUniquifier();

    @NoThrow
    @Alias(names = "java_util_Random_seedUniquifier_J")
    public static long java_util_Random_seedUniquifier_J() {
        return (long) (seedUniquifier() * 8e18);// must not be negative, or it will complain
    }

    // these functions cause illegal memory accesses :/
    private static void rangeCheck(int length, int start, int end) {
        if (start > end) {
            throw new IllegalArgumentException("fromIndex(" + start + ") > toIndex(" + end + ")");
        } else if (start < 0) {
            throw new ArrayIndexOutOfBoundsException(start);
        } else if (end > length) {
            throw new ArrayIndexOutOfBoundsException(end);
        }
    }

    @Alias(names = "java_util_Arrays_fill_AZIIZV")
    public static void Arrays_fill(boolean[] data, int start, int end, boolean value) {
        fill(data, start, end, 0, b2i(value) * 0x1010101);
    }

    @Alias(names = "java_util_Arrays_fill_ABIIBV")
    public static void Arrays_fill(byte[] data, int start, int end, byte value) {
        int value1 = ((int) value & 0xff);
        value1 = (value1 << 8) | value1;
        value1 = (value1 << 16) | value1;
        fill(data, start, end, 0, value1);
    }

    @Alias(names = "java_util_Arrays_fill_ACIICV")
    public static void Arrays_fill(char[] data, int start, int end, char value) {
        int value1 = ((int) value & 0xffff);
        value1 = (value1 << 8) | value1;
        value1 = (value1 << 16) | value1;
        fill(data, start, end, 1, value1);
    }

    @Alias(names = "java_util_Arrays_fill_ASIISV")
    public static void Arrays_fill(short[] data, int start, int end, short value) {
        int value1 = ((int) value & 0xffff);
        value1 = (value1 << 8) | value1;
        value1 = (value1 << 16) | value1;
        fill(data, start, end, 1, value1);
    }

    @Alias(names = "java_util_Arrays_fill_AFIIFV")
    public static void Arrays_fill(float[] data, int start, int end, float value) {
        fill(data, start, end, 2, Float.floatToRawIntBits(value));
    }

    @Alias(names = "java_util_Arrays_fill_AIIIIV")
    public static void Arrays_fill(int[] data, int start, int end, int value) {
        fill(data, start, end, 2, value);
    }

    @Alias(names = "java_util_Arrays_fill_ADIIDV")
    public static void Arrays_fill(double[] data, int start, int end, double value) {
        fill(data, start, end, 3, Double.doubleToRawLongBits(value));
    }

    @Alias(names = "java_util_Arrays_fill_AJIIJV")
    public static void Arrays_fill(long[] data, int start, int end, long value) {
        fill(data, start, end, 3, value);
    }

    @Alias(names = "java_util_Arrays_fill_AWIILjava_lang_ObjectV")
    public static void Arrays_fill(Object[] data, int start, int end, Object value) {
        long value1 = getAddrS(value);
        if (is32Bits) value1 = (value1 << 32) | value1;
        fill(data, start, end, ptrSizeBits, value1);
    }

    public static void fill(Object data, int start, int end, int shift, int fillValue) {
        long fillValue1 = Integer.toUnsignedLong(fillValue);
        fillValue1 = (fillValue1 << 32) | fillValue1;
        fill(data, start, end, shift, fillValue1);
    }

    public static void fill(Object data, int start, int end, int shift, long fillValue) {
        rangeCheck(arrayLength(data), start, end);
        Pointer ptr = add(castToPtr(data), arrayOverhead);
        fill64(add(ptr, ((long) start << shift)), add(ptr, ((long) end << shift)), fillValue);
    }

    @Alias(names = "static_java_util_BitSet_V")
    public static void static_java_util_BitSet_V() {
    }

    @Alias(names = {"java_util_Stack_addElement_Ljava_lang_ObjectV", "java_util_ArrayList_addElement_Ljava_lang_ObjectV"})
    public static <V> void Stack_addElement(ArrayList<V> self, V sth) {
        self.add(sth);
    }

    @Alias(names = "java_util_Stack_elementAt_ILjava_lang_Object")
    public static <V> Object Stack_elementAt(ArrayList<V> self, int index) {
        return self.get(index);
    }

    @Alias(names = "java_util_Stack_removeElementAt_IV")
    public static <V> void Stack_removeElementAt(ArrayList<V> self, int index) {
        self.remove(index);
    }

    @Alias(names = "java_util_ArrayList_push_Ljava_lang_ObjectLjava_lang_Object")
    public static <V> Object ArrayList_push(ArrayList<V> self, V element) {
        self.add(element);
        return element;
    }

    @Alias(names = "java_util_ArrayList_pop_Ljava_lang_Object")
    public static <V> Object ArrayList_pop(ArrayList<V> self) {
        return self.remove(self.size() - 1);
    }

    @Alias(names = "java_util_ArrayList_peek_Ljava_lang_Object")
    public static <V> Object ArrayList_peek(ArrayList<V> self) {
        return self.get(self.size() - 1);
    }

    @Alias(names = "java_util_ArrayList_copyInto_AWV")
    public static <V> void Stack_copyInto(ArrayList<V> self, Object[] dst) throws NoSuchFieldException, IllegalAccessException {
        Object[] src = (Object[]) ArrayList.class.getDeclaredField("elementData").get(self);
        System.arraycopy(src, 0, dst, 0, self.size());
    }

    @RevAlias(name = "new_java_util_HashMap_IFV")
    public static native void new_java_util_HashMap_IFIV(Object self, int initialCapacity, float loadFactor);

    @Alias(names = "new_java_util_HashMap_IFIV")
    public static void new_java_util_HashMap_IFIV(Object self, int initialCapacity, float loadFactor, int concurrencyLevel) {
        new_java_util_HashMap_IFIV(self, initialCapacity, loadFactor);
    }

    @NoThrow // why does this exist? probably an optimization of some kind
    @Alias(names = "java_util_HashMap_comparableClassFor_Ljava_lang_ObjectLjava_lang_Class")
    public static Object java_util_HashMap_comparableClassFor_Ljava_lang_ObjectLjava_lang_Class(Object instance) {
        return null;
    }

    // this was weird, requiring that the comparator is serializable..., and failing on that,
    // because my lambdas aren't serializable out of the box
    // todo can we find out what tells us that it needs to be serialize???
    @Alias(names = "java_util_Comparator_comparingInt_Ljava_util_function_ToIntFunctionLjava_util_Comparator")
    public static <V> Comparator<V> Comparator_comparingInt(ToIntFunction<V> func) {
        return new IntComparator<>(func);
    }

    private static final class IntComparator<V> implements Comparator<V> {
        private final ToIntFunction<V> map;

        private IntComparator(ToIntFunction<V> map) {
            this.map = map;
        }

        @Override
        public int compare(V o1, V o2) {
            return Integer.compare(
                    map.applyAsInt(o1),
                    map.applyAsInt(o2)
            );
        }
    }

}
