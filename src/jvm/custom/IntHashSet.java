package jvm.custom;

import annotations.NoThrow;

import java.util.Arrays;

/**
 * Hash table based implementation of the {@code IntMap} interface.  This
 * implementation provides all of the optional map operations, and permits
 * {@code null} values.  (The {@code IntHashMap} class is roughly
 * equivalent to {@code HashMap}, except that it uses {@code int}s as its keys.
 * This class makes no guarantees as to
 * the order of the map; in particular, it does not guarantee that the order
 * will remain constant over time.<p>
 * <p>
 * This implementation provides constant-time performance for the basic
 * operations ({@code get} and {@code put}), assuming the hash function
 * disperses the elements properly among the buckets.  Iteration over
 * collection views requires time proportional to the "capacity" of the
 * {@code IntHashMap} instance (the number of buckets) plus its size (the number
 * of key-value mappings).  Thus, it's very important not to set the intial
 * capacity too high (or the load factor too low) if iteration performance is
 * important.<p>
 * <p>
 * An instance of {@code HashMap} has two parameters that affect its
 * performance: <i>initial capacity</i> and <i>load factor</i>.  The
 * <i>capacity</i> is the number of buckets in the hash table, and the initial
 * capacity is simply the capacity at the time the hash table is created.  The
 * <i>load factor</i> is a measure of how full the hash table is allowed to
 * get before its capacity is automatically increased.  When the number of
 * entries in the hash table exceeds the product of the load factor and the
 * current capacity, the capacity is roughly doubled by calling the
 * {@code rehash} method.<p>
 * <p>
 * As a general rule, the default load factor (.75) offers a good tradeoff
 * between time and space costs.  Higher values decrease the space overhead
 * but increase the lookup cost (reflected in most of the operations of the
 * {@code IntHashMap} class, including {@code get} and {@code put}).  The
 * expected number of entries in the map and its load factor should be taken
 * into account when setting its initial capacity, so as to minimize the
 * number of {@code rehash} operations.  If the initial capacity is greater
 * than the maximum number of entries divided by the load factor, no
 * {@code rehash} operations will ever occur.<p>
 * <p>
 * If many mappings are to be stored in a {@code IntHashMap} instance, creating
 * it with a sufficiently large capacity will allow the mappings to be stored
 * more efficiently than letting it perform automatic rehashing as needed to
 * grow the table.<p>
 *
 * <b>Note that this implementation is not synchronized.</b> If multiple
 * threads access this map concurrently, and at least one of the threads
 * modifies the map structurally, it <i>must</i> be synchronized externally.
 * (A structural modification is any operation that adds or deletes one or
 * more mappings; merely changing the value associated with a key that an
 * instance already contains is not a structural modification.)  This is
 * typically accomplished by synchronizing on some object that naturally
 * encapsulates the map.
 * <p>
 * The iterators returned by all of this class's "collection view methods" are
 * <i>fail-fast</i>: if the map is structurally modified at any time after the
 * iterator is created, in any way except through the iterator's own
 * {@code remove} or {@code add} methods, the iterator will throw a
 * {@code ConcurrentModificationException}.  Thus, in the face of concurrent
 * modification, the iterator fails quickly and cleanly, rather than risking
 * arbitrary, non-deterministic behavior at an undetermined time in the
 * future.
 * <p>
 * Beta class. Generally use {@link java.util.HashMap} or {@link java.util.EnumMap} instead.
 *
 * @author Based on Sun's java.util.HashMap (modified by koliver)
 */
@SuppressWarnings("unused")
public class IntHashSet {

    private static final int DEFAULT_CAPACITY = 101;

    /**
     * The hash table data.
     */
    private transient IEntry[] table;

    /**
     * The total number of mappings in the hash table.
     */
    private transient int count;

    /**
     * The table is rehashed when its size exceeds this threshold.  (The
     * value of this field is (int)(capacity * loadFactor).)
     */
    private int threshold;

    /**
     * The load factor for the hashtable.
     */
    private final float loadFactor;

    /**
     * Constructs a new, empty map with the specified initial
     * capacity and the specified load factor.
     *
     * @param initialCapacity the initial capacity of the HashMap.
     * @param loadFactor      the load factor of the HashMap
     * @throws IllegalArgumentException if the initial capacity is less
     *                                  than zero, or if the load factor is nonpositive.
     */
    private IntHashSet(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal Initial Capacity: " + initialCapacity);
        }
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new IllegalArgumentException("Illegal Load factor: " + loadFactor);
        }
        if (initialCapacity == 0)
            initialCapacity = 1;
        this.loadFactor = loadFactor;
        this.table = new IEntry[initialCapacity];
        this.threshold = (int) (initialCapacity * loadFactor);
    }

    /**
     * Constructs a new, empty map with the specified initial capacity
     * and default load factor, which is {@code 0.75}.
     *
     * @param initialCapacity the initial capacity of the HashMap.
     * @throws IllegalArgumentException if the initial capacity is less
     *                                  than zero.
     */
    public IntHashSet(int initialCapacity) {
        this(initialCapacity, 0.75f);
    }

    /**
     * Constructs a new, empty map with a default capacity, {@code 101}, and load
     * factor, which is {@code 0.75}.
     */
    public IntHashSet() {
        this(DEFAULT_CAPACITY, 0.75f);
    }

    /**
     * Returns the number of key-value mappings in this map.
     */
    @NoThrow
    public int size() {
        return count;
    }

    /**
     * Returns {@code true} if this map contains a mapping for the specified
     * key.
     *
     * @param key key whose presence in this Map is to be tested.
     */
    @NoThrow
    public boolean contains(int key) {
        IEntry[] tab = this.table;
        int index = (key & 0x7FFFFFFF) % tab.length;
        for (IEntry e = tab[index]; e != null; e = e.next) {
            if (e.key == key) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rehashes the contents of this map into a new {@code HashMap} instance
     * with a larger capacity. This method is called automatically when the
     * number of keys in this map exceeds its capacity and load factor.
     */
    @NoThrow
    private void rehash() {
        int oldCapacity = this.table.length;
        IEntry[] oldMap = this.table;

        int newCapacity = oldCapacity * 2 + 1;
        IEntry[] newMap = new IEntry[newCapacity];

        this.threshold = (int) (newCapacity * loadFactor);
        this.table = newMap;

        for (int i = oldCapacity; i-- > 0; ) {
            for (IEntry old = oldMap[i]; old != null; ) {
                IEntry e = old;
                old = old.next;

                int index = (e.key & 0x7FFFFFFF) % newCapacity;
                e.next = newMap[index];
                newMap[index] = e;
            }
        }
    }

    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for this key, the old
     * value is replaced.
     *
     * @param key key with which the specified value is to be associated.
     * @return whether the set was modified
     */
    public boolean add(int key) {
        // Makes sure the key is not already in the HashMap.
        IEntry[] tab = table;
        int index = (key & 0x7FFFFFFF) % tab.length;
        // first look if there was an old value at key
        for (IEntry e = tab[index]; e != null; e = e.next) {
            if (key == e.key) return false;
        }

        if (count >= threshold) {
            // Rehash the table if the threshold is exceeded
            rehash();

            tab = this.table;
            index = (key & 0x7FFFFFFF) % tab.length;
        }

        // Creates the new entry.
        IEntry e = new IEntry(key, tab[index]);
        tab[index] = e;
        count++;
        return true;
    }

    public void clear() {
        Arrays.fill(table, null);
        count = 0;
    }

    /**
     * Removes the mapping for this key from this map if present.
     *
     * @param key key whose mapping is to be removed from the map.
     * @return previous value associated with specified key, or {@code null}
     * if there was no mapping for key.  A {@code null} return can
     * also indicate that the map previously associated {@code null}
     * with the specified key.
     */
    @NoThrow
    public boolean remove(int key) {
        IEntry[] tab = this.table;

        int index = (key & 0x7FFFFFFF) % tab.length;

        for (IEntry e = tab[index], prev = null; e != null; prev = e, e = e.next) {
            if (key == e.key) {
                if (prev != null) {
                    prev.next = e.next;
                } else {
                    tab[index] = e.next;
                }

                count--;
                // e.value = null;, probably just for GC...
                return true;
            }
        }

        return false;
    }

    private static class IEntry {
        final int key;
        IEntry next;

        IEntry(int key, IEntry next) {
            this.key = key;
            this.next = next;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof IEntry))
                return false;
            IEntry e = (IEntry) o;
            return e.key == key;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(key);
        }

    }

}