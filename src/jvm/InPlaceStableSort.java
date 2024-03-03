package jvm;

import java.util.Comparator;

import static jvm.JVM32.*;
import static jvm.JavaLang.getAddr;
import static jvm.JavaLang.ptrTo;

// https://www.codeproject.com/Articles/26048/Fastest-In-Place-Stable-Sort
// http://thomas.baudel.name/Visualisation/VisuTri/inplacestablesort.html
class InPlaceStableSort {

	private static <V> int lower(int from, int to, V val, V[] o, Comparator<V> c) {
		int len = to - from, half;
		while (len > 0) {
			half = len >> 1;
			final int mid = from + half;
			if (c.compare(o[mid], val) < 0) {
				from = mid + 1;
				len = len - half - 1;
			} else len = half;
		}
		return from;
	}

	private static <V> int upper(int from, int to, V val, V[] o, Comparator<V> c) {
		int len = to - from, half;
		while (len > 0) {
			half = len >> 1;
			final int mid = from + half;
			if (c.compare(val, o[mid]) < 0) {
				len = half;
			} else {
				from = mid + 1;
				len = len - half - 1;
			}
		}
		return from;
	}

	private static <V> void insertSort(int from, int to, V[] o, Comparator<V> c) {
		// check bounds first, and then we can use unsafe element access
		if (from < 0 || c == null || o.length < to) {
			throw new IllegalArgumentException();
		}
		// it would be nice if we could resolve the method call for c right here
		int addr = getAddr(o) + arrayOverhead;
		int fromAddr = addr + (from << 2);
		int toAddr = addr + (to << 2);
		for (int i = fromAddr + 4; unsignedLessThan(i, toAddr); i += 4) {
			insertSortInner(fromAddr, i, c);
		}
	}

	// it would be nice if we had stable sorting with O(n log n)
	private static <V> void insertSortInner(int lowerBound, int upperBound, Comparator<V> c) {
		for (int j = upperBound; unsignedGreaterThan(j, lowerBound); ) {
			int k = j - 4;
			int ai = read32(j);
			int bi = read32(k);
			final V a = ptrTo(ai);
			final V b = ptrTo(bi);
			if (c.compare(a, b) < 0) {
				write32(k, ai);
				write32(j, bi);
				j = k;
			} else break;
		}
	}

	private static int gcd(int m, int n) {
		while (n != 0) {
			final int t = m % n;
			m = n;
			n = t;
		}
		return m;
	}

	private static <V> void rotate(int from, int mid, int to, V[] o) {
		if (from == mid || mid == to) return;
		int n = gcd(to - from, mid - from);
		while (n-- != 0) {
			V val = o[from + n];
			int shift = mid - from;
			int p1 = from + n, p2 = from + n + shift;
			while (p2 != from + n) {
				o[p1] = o[p2];
				p1 = p2;
				if (to - p2 > shift) p2 += shift;
				else p2 = from + (shift - (to - p2));
			}
			o[p1] = val;
		}
	}

	private static <V> void merge(int from, int pivot, int to, int len1, int len2, V[] o, Comparator<V> c) {
		if (len1 == 0 || len2 == 0) return;
		if (len1 + len2 == 2) {
			V p = o[pivot];
			V f = o[from];
			if (c.compare(p, f) < 0) {
				o[pivot] = f;
				o[from] = p;
			}
			return;
		}
		int cut1, cut2;
		int len11, len22;
		if (len1 > len2) {
			len11 = len1 >> 1;
			cut1 = from + len11;
			cut2 = lower(pivot, to, o[cut1], o, c);
			len22 = cut2 - pivot;
		} else {
			len22 = len2 >> 1;
			cut2 = pivot + len22;
			cut1 = upper(from, pivot, o[cut2], o, c);
			len11 = cut1 - from;
		}
		rotate(cut1, pivot, cut2, o);
		final int new_mid = cut1 + len22;
		merge(from, cut1, new_mid, len11, len22, o, c);
		merge(new_mid, cut2, to, len1 - len11, len2 - len22, o, c);
	}

	public static <V> void sort(int from, int to, V[] o, Comparator<V> c) {
		if (from > to) throw new IllegalArgumentException();
		if (to - from < 12) {
			insertSort(from, to, o, c);
		} else {
			final int middle = (from + to) >>> 1;
			sort(from, middle, o, c);
			sort(middle, to, o, c);
			merge(from, middle, to, middle - from, to - middle, o, c);
		}
	}

	public static void main(String[] args) {
		// works and is stable, confirmed :)
		Object[] array = {5, 4, 3, 3, 2, 1};
		sort(0, array.length, array, Comparator.comparingInt(o -> (int) o));
		for (int i = 0; i < array.length; i++) {
			System.out.println(i + ":" + array[i]);
		}
	}
}