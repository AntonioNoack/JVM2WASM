package interpreter.memory

import allocationStart
import gIndex
import interpreter.WASMEngine
import jvm.JVM32
import jvm.JVM32.*
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertNotEquals
import me.anno.utils.assertions.assertTrue
import me.anno.utils.files.Files.formatFileSize
import me.anno.utils.structures.Recursion.processRecursive2
import me.anno.utils.structures.lists.Lists.createList
import org.apache.logging.log4j.LogManager
import utils.StaticClassIndices.*
import utils.is32Bits
import utils.lookupStaticVariable
import java.nio.ByteBuffer

object MemoryOptimizer {

    private val LOGGER = LogManager.getLogger(MemoryOptimizer::class)

    // todo we could use a flag in GC, whether an instance once was tracked by WeakRef,
    //  which saves us tons of IntHashMap.remove()-calls

    fun readClassId(memory: ByteBuffer, addr: Int): Int {
        return memory.getInt(addr) and 0xffffff
    }

    fun readGCIteration(memory: ByteBuffer, addr: Int): Int {
        return memory.getInt(addr).ushr(24)
    }

    fun writeGCIteration(memory: ByteBuffer, addr: Int, iteration: Int) {
        val classId = readClassId(memory, addr)
        val newValue = classId or iteration.shl(24)
        memory.putInt(addr, newValue)
    }

    fun readArrayLength(memory: ByteBuffer, addr: Int): Int {
        return memory.getInt(addr + objectOverhead)
    }

    fun getArray0(addr: Int): Int {
        return addr + arrayOverhead
    }

    fun readIntArray(memory: ByteBuffer, addr: Int): IntArray {
        assertNotEquals(0, addr)
        assertEquals(INT_ARRAY, readClassId(memory, addr))
        val size = readArrayLength(memory, addr)
        val addr0 = getArray0(addr)
        return IntArray(size) { memory.getInt(addr0 + it.shl(2)) }
    }

    fun readPointer(memory: ByteBuffer, addr: Int): Int {
        return if (is32Bits) {
            memory.getInt(addr)
        } else {
            memory.getLong(addr).toInt()
        }
    }

    fun readArrayOfIntArrays(memory: ByteBuffer, addr: Int): List<IntArray?> {
        assertNotEquals(0, addr)
        assertEquals(OBJECT_ARRAY, readClassId(memory, addr))
        val size = readArrayLength(memory, addr)
        val addr0 = getArray0(addr)
        return createList(size) {
            val addrI = readPointer(memory, addr0 + it * JVM32.ptrSize)
            if (addrI != 0) readIntArray(memory, addrI) else null
        }
    }

    lateinit var staticFields: IntArray
    lateinit var classSizes: IntArray

    fun optimizeMemory(engine: WASMEngine) {
        // todo find all used instances...

        val memory = engine.buffer
        // access baked data from GCTraversal
        val staticFields0 = gIndex.getFieldOffset("jvm/GCTraversal", "staticFields", "[I", true)!!
        val staticFields1 = lookupStaticVariable("jvm/GCTraversal", staticFields0)
        staticFields = readIntArray(memory, memory.getInt(staticFields1))

        // val classSizes0 = gIndex.getFieldOffset("jvm/GCTraversal", "classSizes", "[I", true)!!
        // val classSizes1 = lookupStaticVariable("jvm/GCTraversal", classSizes0)
        // val classSizes = readIntArray(memory, memory.getInt(classSizes1))
        classSizes = gIndex.classNamesByIndex.map(gIndex::getInstanceSize).toIntArray()

        val instanceFields0 = gIndex.getFieldOffset("jvm/GCTraversal", "fieldOffsetsByClass", "[I", true)!!
        val instanceFields1 = lookupStaticVariable("jvm/GCTraversal", instanceFields0)
        val instanceFields2 = readArrayOfIntArrays(memory, memory.getInt(instanceFields1))

        // todo run on all dynamically allocated memory after a WASMEngine ran
        //  - collect which instances are in use
        //  - collect how much space we can save
        //  - remap instances, and all references to them (!!! WeakRef, too)

        // verify our generation hasn't been used yet
        val thisIteration = 16
        val allocationPtr = getAllocationPointer(engine)
        forAllDynamicInstances(engine) { addr ->
            assertTrue(readClassId(memory, addr) in 0 until gIndex.classIndex.size)
            assertNotEquals(thisIteration, readGCIteration(memory, addr))
        }

        val staticValues = ArrayList<Int>()
        forAllStaticInstances(memory) { addr ->
            if (isDynamicInstance(addr, allocationPtr)) staticValues.add(addr)
        }

        var usedMemory = 0
        processRecursive2(staticValues) { addr, remaining ->
            assertTrue(isDynamicInstance(addr, allocationPtr))
            if (readGCIteration(memory, addr) != thisIteration) {
                writeGCIteration(memory, addr, thisIteration)

                usedMemory += getInstanceSize(memory, addr)

                val classId = readClassId(memory, addr)
                assertTrue(classId in gIndex.classNamesByIndex.indices)
                // println("Checking $addr, ${gIndex.classNamesByIndex[classId]}")
                if (classId !in FIRST_ARRAY..LAST_ARRAY) {
                    val offsets = instanceFields2[classId]
                    if (offsets != null) {
                        // iterate over all fields, which are not native
                        for (i in offsets.indices) {
                            val offset = offsets[i]
                            val addr2 = readPointer(memory, addr + offset)
                            if (isDynamicInstance(addr2, allocationPtr)) remaining.add(addr2)
                        }
                    }
                } else if (classId == OBJECT_ARRAY) {
                    // iterate over all contents
                    val length = readArrayLength(memory, addr)
                    for (i in 0 until length) {
                        val offset = arrayOverhead + i * ptrSize
                        val addr2 = readPointer(memory, addr + offset)
                        if (isDynamicInstance(addr2, allocationPtr)) remaining.add(addr2)
                    }
                }// else just native content in a native array
            }
        }

        val totalDynamicMemory = getAllocationPointer(engine) - allocationStart
        LOGGER.info("Used memory: ${usedMemory.formatFileSize()} / ${totalDynamicMemory.formatFileSize()}")

        // todo (un??)fortunately, compacting the memory is really worth it, as only 43% are actually used
        //  we could save 2.7 MiB by implementing this

    }

    fun isDynamicInstance(addr: Int, allocationPtr: Int): Boolean {
        assertTrue(addr in 0 until allocationPtr) {
            "Illegal address $addr !in 0 until $allocationPtr"
        }
        return addr >= allocationStart
    }

    fun forAllInstances(engine: WASMEngine, callback: (addr: Int) -> Unit) {
        forAllStaticInstances(engine.buffer, callback)
        // todo iterate over class, method and field instances
        forAllDynamicInstances(engine, callback)
    }

    private fun getArraySize(memory: ByteBuffer, addr: Int): Int {
        val classId = readClassId(memory, addr)
        assertTrue(classId in FIRST_ARRAY..LAST_ARRAY)
        val length = readArrayLength(memory, addr)
        val typeShift = getTypeShift(classId)
        val rawSize = arrayOverhead + length.shl(typeShift)
        return adjustCallocSize(rawSize)
    }

    private fun getTypeShift(classId: Int): Int {
        return when (classId) {
            BYTE_ARRAY, BOOLEAN_ARRAY -> 0
            SHORT_ARRAY, CHAR_ARRAY -> 1
            INT_ARRAY, FLOAT_ARRAY -> 2
            OBJECT_ARRAY -> if (is32Bits) 2 else 3
            LONG_ARRAY, DOUBLE_ARRAY -> 3
            else -> throw IllegalArgumentException("$classId")
        }
    }

    private fun getAllocationPointer(engine: WASMEngine): Int {
        return engine.globals["allocationPointer"]!!.toInt()
    }

    private fun forAllDynamicInstances(engine: WASMEngine, callback: (addr: Int) -> Unit) {
        // iterate over all dynamic space
        var addr = allocationStart
        val allocationPointer = getAllocationPointer(engine)
        val memory = engine.buffer
        while (addr < allocationPointer) {
            callback(addr)
            addr += getInstanceSize(memory, addr)
        }
    }

    private fun getInstanceSize(memory: ByteBuffer, addr: Int): Int {
        val classId = readClassId(memory, addr)
        return if (classId in FIRST_ARRAY..LAST_ARRAY) {
            getArraySize(memory, addr)
        } else classSizes[classId]
    }

    private fun forAllStaticInstances(memory: ByteBuffer, callback: (addr: Int) -> Unit) {
        forAllStaticInstanceFields { ptr ->
            val instance = readPointer(memory, ptr)
            if (instance != 0) {
                assertTrue(instance in 1 until memory.capacity())
                callback(instance)
            }
        }
    }

    private fun forAllStaticInstanceFields(callback: (addr: Int) -> Unit) {
        // iterate over all static space
        val staticFields = staticFields
        for (i in staticFields.indices) {
            callback(staticFields[i])
        }
    }

}