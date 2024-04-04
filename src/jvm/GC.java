package jvm;

import annotations.Alias;
import annotations.JavaScript;
import annotations.NoThrow;
import jvm.custom.IntHashMap;
import jvm.custom.IntHashSet;
import jvm.custom.WeakRef;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static jvm.JVM32.*;
import static jvm.JavaLang.*;

public class GC {

	public static int freeMemory = 0;

	public static byte iteration = 0;

	public static final int GCOffset = 3;

	public static final int[] largestGaps = new int[32];// 16 * 2 (length, start)

	public static int generation = 1;

	@NoThrow
	@JavaScript(code = "markJSReferences()")
	private static native void markJSReferences();

	@NoThrow
	@Alias(names = "gc")
	public static void invokeGC() {
		// log("Running GC");
		GCX.isInited = false;
		// run gc:
		// - nothing is running -> we can safely ignore the stack
		iteration++;
		generation++;
		cachedStartPtr = getAllocationStart();
		// log("Traversing Static Instances");
		traverseStaticInstances();
		// log("Marking JS References");
		markJSReferences();
		// log("Finding Largest Gaps");
		findLargestGaps();
		// log("Finished GC");
	}

	@NoThrow
	public static int findGap(int size) {
		int ptr = getAddr(largestGaps);
		final int length = arrayLength(ptr);
		ptr += arrayOverhead;
		final int endPtr = ptr + (length << 2);
		final int sizeWithHelper = size + arrayOverhead;
		while (ptr < endPtr) {
			int available = read32(ptr);
			// three cases:
			//  a) we fit perfectly
			//  b) we fit, and let stuff remain
			//  c) we don't fit
			if (available == size) {
				return findGapReplace(ptr, size);
			} else if (unsignedGreaterThanEqual(available, sizeWithHelper)) {
				return findGapFitIntoSpace(ptr, size, available);
			} else {
				ptr += 8;
			}
		}
		return 0; // no gap was found
	}

	@NoThrow
	private static int findGapReplace(int ptr, int size) {
		// if (printCtr++ < 0) log("GC replacing", size);
		write32(ptr, 0); // nothing is remaining
		freeMemory -= size;
		return read32(ptr + 4);// address
	}

	@NoThrow
	private static int findGapFitIntoSpace(final int ptr, final int size, final int available) {
		final int sizeWithHelper = size + arrayOverhead;
		// if (printCtr++ < 0) log("GC shrinking", ptr, available, size);
		final int addr = read32(ptr + 4);
		// shrink array
		write32(addr + objectOverhead, available - sizeWithHelper);
		write32(ptr, available - size);
		// calculate new pointer, and we're done :D
		freeMemory -= size;
		return addr + available - size;
	}

	private static int smallestSize, smallestSizeIndex;

	private static void findLargestGaps() {

		// - find the largest gaps to reuse the memory there
		// for that, iterate over all allocated memory
		int instance = getAllocationStart();
		final int iter = iteration;
		final int endPtr = getNextPtr();
		boolean wasUsed = true;
		int gapStart = instance;
		final int[] largestGaps = GC.largestGaps;
		Arrays.fill(largestGaps, 0);
		final int nc = numClasses();

		// log("Scanning", instance, endPtr, iter);

		if (classSizes.length != nc)
			throw new IllegalStateException("ClassSizes.length != nc");

		int gapCounter = 0;

		smallestSize = arrayOverhead << 1;
		smallestSizeIndex = 0;

		freeMemory = 0;

		int sizesPtr = getAddr(classSizes) + arrayOverhead;
		IntHashSet ofInterest = WeakRef.ofInterest;
		IntHashMap lastDeleted = WeakRef.lastDeleted;

		while (unsignedLessThan(instance, endPtr)) {

			// when we find a not-used section, replace it with byte[] for faster future traversal (if possible)
			int clazz = readClass(instance);
			if (unsignedGreaterThanEqual(clazz, nc)) {
				log("Handling", instance, clazz);
				log("Illegal class index {} >= {} at {}!", clazz, nc, instance);
				return;
			}

			int size;
			if (unsignedLessThan(clazz - 1, 9)) { // clazz > 0 && clazz < 10
				// handle arrays by size
				size = arrayOverhead + (arrayLength(instance) << getTypeShift(clazz));
			} else {
				// handle class instance
				size = read32(sizesPtr + (clazz << 2));
			}

			// log("Handling", instance, clazz, size);

			if (unsignedLessThan(size, objectOverhead)) {
				log("Handling", instance, clazz);
				log("Illegal instance size!", size);
				return;
			}

			size = adjustCallocSize(size);

			if (unsignedLessThan(endPtr, instance + size)) {
				log("Handling", instance, clazz);
				log("Illegal end", instance + size, size, endPtr);
				throwJs();
			}

			boolean isUsed = read8(instance + GCOffset) == iter;
			if (isUsed != wasUsed) {
				if (isUsed) {
					gapCounter++;
					// handle empty space in between
					// what if this is > 2 GiB? will be an illegal array length ->
					// nobody will access it anyway -> doesn't matter, as long as we compute everything with unsigned ints :)
					int available = instance - gapStart;
					freeMemory += available;
					if (unsignedGreaterThan(available, smallestSize)) {
						// log("Handling", instance, clazz);
						// log("Found gap :)", available);
						handleGap(available, gapStart);
					} // else gap too small for us to care for
					// retry to find the error
				} else {
					gapStart = instance;
				}
				wasUsed = isUsed;
			}

			if (!isUsed) {
				Finalizer.call(ptrTo(instance));
				if (ofInterest.contains(instance)) {// bad!, allocates an integer instance of every utils.single call...
					lastDeleted.put(getAddr(instance), generation, 0);
				}
			}

			instance += size;

		}

		// if the last object is not being used, we can reduce the nextPtr() by its size
		if (!wasUsed && getNextPtr() == endPtr) {
			setNextPtr(gapStart);
			// log("Reduced max memory to, by", gapStart, endPtr - gapStart);
		}

		// log("Done Scanning :), instances:", numInstances);
		// log("Gaps:", gapCounter);
		GCX.isInited = gapCounter > 0;
	}

	private static void handleGap(int available, int gapStart) {

		// first step: replace with byte array, so that next time we can skip over it faster
		// clear(gapStart, gapStart + available);

		write32(gapStart, 5); // byte array
		write32(gapStart + objectOverhead, available - arrayOverhead); // length

		int smallestSizeIndex = GC.smallestSizeIndex;
		int[] largestGaps = GC.largestGaps;

		largestGaps[smallestSizeIndex] = available;
		largestGaps[smallestSizeIndex + 1] = gapStart;

		int smallestSize = available;

		// reevaluate smallest size
		for (int i = 0; i < 16; i++) {
			int index = i + i;
			int prev = largestGaps[index];
			if (unsignedLessThan(prev, smallestSize)) {
				smallestSize = prev;
				smallestSizeIndex = index;
			}
		}

		GC.smallestSize = smallestSize;
		GC.smallestSizeIndex = smallestSizeIndex;

	}

	@NoThrow
	private static void traverseStaticInstances() {
		int ptr = getAddr(staticFields);
		int length = arrayLength(ptr);
		ptr += arrayOverhead;
		int endPtr = ptr + (length << 2);
		// log("Traversing static instances", ptr, endPtr);
		while (ptr < endPtr) {
			int fieldAddr = read32(ptr);
			int fieldValue = read32(fieldAddr);
			traverse(fieldValue);
			ptr += 4;
		}
	}

	private static int cachedStartPtr;

	@NoThrow
	@Alias(names = "gcMarkUsed")
	private static void traverse(int instance) {
		// check addr for ignored sections and NULL
		if (unsignedGreaterThanEqual(instance, cachedStartPtr)) {
			int statePtr = instance + GCOffset;
			byte state = read8(statePtr);
			byte iter = iteration;
			// log("Traversal", addr, iter, state);
			if (state != iter) {
				write8(statePtr, iter);
				int clazz = readClass(instance);
				if (clazz == 1) {
					traverseObjectArray(instance);
				} else {
					traverseInstance(instance, clazz);
				}
			}// else already done
		}// else ignore it
	}

	@NoThrow
	private static void traverseObjectArray(int instance) {
		int length = arrayLength(instance);
		int ptr = instance + arrayOverhead;
		int endPtr = ptr + (length << 2);
		while (unsignedLessThan(ptr, endPtr)) {
			traverse(read32(ptr));
			ptr += 4;
		}
	}

	@NoThrow
	private static void traverseInstance(int instance, int clazz) {
		int fields = read32(getAddr(fieldsByClass) + arrayOverhead + (clazz << 2));// fieldsByClass[clazz]
		if (fields == 0) return; // no fields to process
		int size = arrayLength(fields);
		fields += arrayOverhead;
		int endPtr = fields + (size << 2);
		while (fields < endPtr) {
			int offset = read32(fields);
			traverse(read32(instance + offset));
			fields += 4;
		}
	}

	private static final int[][] fieldsByClass;
	private static final int[] staticFields;
	public static final int[] classSizes;

	static {
		log("Creating GC field table");
		// classes
		int nc = numClasses();
		int[][] fieldsByClass2 = new int[nc][];
		fieldsByClass = fieldsByClass2;
		int[] classSizes2 = new int[nc];
		classSizes = classSizes2;
		int staticFieldCtr = 0;
		for (int i = 0; i < nc; i++) {
			classSizes2[i] = getInstanceSize(i);
			Class<Object> clazz = ptrTo(findClass(i));
			int fields = getFields(clazz);
			if (fields == 0) continue;
			// count fields
			int fieldCtr = 0;
			int length = arrayLength(fields);
			int ptr = fields + arrayOverhead;
			for (int j = 0; j < length; j++) {
				Field field = ptrTo(read32(ptr));
				int mods = field.getModifiers();
				// check if type is relevant
				if (!Modifier.isNative(mods)) {
					if (Modifier.isStatic(mods)) {
						staticFieldCtr++;
					} else {
						fieldCtr++;
					}
				}
				ptr += 4;
			}
			if (fieldCtr > 0) {
				int[] offsets = new int[fieldCtr];
				fieldsByClass2[i] = offsets;
				ptr = fields + arrayOverhead;
				fieldCtr = 0;
				for (int j = 0; j < length; j++) {
					Field field = ptrTo(read32(ptr));
					int mods = field.getModifiers();
					// check if type is relevant
					if (!Modifier.isNative(mods) && !Modifier.isStatic(mods)) {// masking could be optimized
						offsets[fieldCtr++] = getFieldOffset(field);
					}
					ptr += 4;
				}
			}
		}
		int ctr = 0;
		int[] staticFields2 = new int[staticFieldCtr];
		// log("Counted {} static fields", staticFieldCtr);
		staticFields = staticFields2;
		for (int i = 0; i < nc; i++) {
			Class<Object> clazz = ptrTo(findClass(i));
			int fields = getFields(clazz);
			if (fields == 0) continue;
			// count fields
			int length = arrayLength(fields);
			int staticOffset = findStatic(i, 0);
			int ptr = fields + arrayOverhead;
			for (int j = 0; j < length; j++) {
				Field field = ptrTo(read32(ptr));
				int mods = field.getModifiers();
				// check if type is relevant
				if (!Modifier.isNative(mods)) {
					if (Modifier.isStatic(mods)) {
						staticFields2[ctr++] = staticOffset + getFieldOffset(field);
					}
				}
				ptr += 4;
			}
		}
		log("Finished initializing GC");
	}

}
