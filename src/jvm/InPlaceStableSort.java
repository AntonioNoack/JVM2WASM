package jvm;

import java.util.Comparator;

// https://www.codeproject.com/Articles/26048/Fastest-In-Place-Stable-Sort
// http://thomas.baudel.name/Visualisation/VisuTri/inplacestablesort.html

/**
 * sorting for object arrays, in-place, stable and fast
 */
class InPlaceStableSort {

    private static <V> void slowSort(int from, int to, V[] array, Comparator<V> c) {
        for (int i = from, to1 = to - 1; i < to1; i++) {
            for (int j = i + 1; j < to; j++) {
                V vi = array[i];
                V vj = array[j];
                if (c.compare(vi, vj) > 0) {
                    array[i] = vj;
                    array[j] = vi;
                }
            }
        }
    }

    private static <V> void merge(int from, int pivot, int to, V[] arr, Comparator<V> c) {
        while (from < pivot && pivot < to) {
            if (c.compare(arr[from], arr[pivot]) <= 0) {
                from++;
            } else {
                V tmp = arr[pivot];
                int k = pivot;
                while (k > from) {
                    arr[k] = arr[k - 1];
                    k--;
                }
                arr[from] = tmp;
                from++;
                pivot++;
            }
        }
    }

    public static <V> void sort(int from, int to, V[] o, Comparator<V> c) {
        if (from > to) throw new IllegalArgumentException();
        if (to - from < 12) {
            slowSort(from, to, o, c);
        } else {
            final int middle = (from + to) >>> 1;
            sort(from, middle, o, c);
            sort(middle, to, o, c);
            merge(from, middle, to, o, c);
        }
    }

    public static void main(String[] args) {
        // works and is stable, confirmed :)
        Object[] array = {5, 4, 17, 156, 132, 3, 0, 3, 2, 1};
        sort(0, array.length, array, Comparator.comparingInt(o -> (int) o));
        for (int i = 0; i < array.length; i++) {
            System.out.println(i + ":" + array[i]);
        }
    }
}