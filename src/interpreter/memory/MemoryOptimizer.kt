package interpreter.memory

import allocationStart
import gIndex
import globals
import interpreter.WASMEngine
import jvm.JVMFlags.is32Bits
import jvm.JVMFlags.ptrSize
import jvm.JVMShared.*
import me.anno.utils.algorithms.Recursion.processRecursiveSet
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertNotEquals
import me.anno.utils.assertions.assertTrue
import me.anno.utils.files.Files.formatFileSize
import org.apache.logging.log4j.LogManager
import utils.*
import utils.StaticClassIndices.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

object MemoryOptimizer {

    private val LOGGER = LogManager.getLogger(MemoryOptimizer::class)

    // todo we could use a flag in GC, whether an instance once was tracked by WeakRef,
    //  which saves us tons of IntHashMap.remove()-calls

    private fun readClassId(memory: ByteBuffer, addr: Int): Int {
        return memory.getInt(addr) and 0xffffff
    }

    private fun readGCIteration(memory: ByteBuffer, addr: Int): Int {
        return memory.getInt(addr).ushr(24)
    }

    private fun writeGCIteration(memory: ByteBuffer, addr: Int, iteration: Int) {
        val classId = readClassId(memory, addr)
        val newValue = classId or iteration.shl(24)
        memory.putInt(addr, newValue)
    }

    private fun readArrayLength(memory: ByteBuffer, addr: Int): Int {
        return memory.getInt(addr + objectOverhead)
    }

    private fun readIntArray(memory: ByteBuffer, addr: Int): IntArray {
        assertNotEquals(0, addr)
        assertEquals(INT_ARRAY, readClassId(memory, addr))
        val size = readArrayLength(memory, addr)
        return IntArray(size) { memory.getInt(addr + arrayOverhead + it.shl(2)) }
    }

    private fun readPointer(memory: ByteBuffer, addr: Int): Int {
        return if (is32Bits) {
            memory.getInt(addr)
        } else {
            memory.getLong(addr).toInt()
        }
    }

    private fun writePointer(memory: ByteBuffer, addr: Int, value: Int) {
        if (is32Bits) {
            memory.putInt(addr, value)
        } else {
            memory.putLong(addr, value.toLong())
        }
    }

    private fun readArrayOfIntArrays(memory: ByteBuffer, addr: Int): List<IntArray?> {
        assertNotEquals(0, addr)
        assertEquals(OBJECT_ARRAY, readClassId(memory, addr))
        val size = readArrayLength(memory, addr)
        return List(size) {
            val addrI = readPointer(memory, addr + arrayOverhead + it * ptrSize)
            if (addrI != 0) readIntArray(memory, addrI) else null
        }
    }

    private lateinit var staticFields: IntArray
    private lateinit var classSizes: IntArray

    fun optimizeMemory(engine: WASMEngine, printer: StringBuilder2): Int {

        val memory = engine.buffer
        // access baked data from GCTraversal
        val staticFields0 = gIndex.getFieldOffset("jvm/GCTraversal", "staticFieldOffsets", "[I", true)!!
        val staticFields1 = lookupStaticVariable("jvm/GCTraversal", staticFields0)
        staticFields = readIntArray(memory, memory.getInt(staticFields1))

        // val classSizes0 = gIndex.getFieldOffset("jvm/GCTraversal", "classSizes", "[I", true)!!
        // val classSizes1 = lookupStaticVariable("jvm/GCTraversal", classSizes0)
        // val classSizes = readIntArray(memory, memory.getInt(classSizes1))
        classSizes = gIndex.classNames.map(gIndex::getInstanceSize).toIntArray()

        val instanceFields0 = gIndex.getFieldOffset("jvm/GCTraversal", "instanceFieldOffsets", "[I", true)!!
        val instanceFields1 = lookupStaticVariable("jvm/GCTraversal", instanceFields0)
        val instanceFields = readArrayOfIntArrays(memory, memory.getInt(instanceFields1))

        // run on all dynamically allocated memory after a WASMEngine ran
        //  - collect which instances are in use
        //  - collect how much space we can save
        //  - remap instances, and all references to them (!!! WeakRef, too)

        // todo since we want to make all space non-GC-able,
        //  clear WeakRef.weakRefInstances before collecting memory (free those instances, too)
        //  -> set WeakRef.weakRefInstances.count to zero
        //  -> clear WeakRef.weakRefInstances.table
        //  -> set WeakRef.next to null

        val allocationPtr = getAllocationPointer(engine)
        // we counted 52k instances to be kept...
        val usedAddresses = ArrayList<Int>(1 shl 16)
        val usedMemorySize = collectUsedAddresses(
            engine, memory, instanceFields,
            allocationPtr, usedAddresses
        )

        val totalDynamicMemory = getAllocationPointer(engine) - allocationStart
        LOGGER.info(
            "Used memory: ${usedMemorySize.formatFileSize()} / ${totalDynamicMemory.formatFileSize()}, " +
                    "#instances: ${usedAddresses.size}"
        )

        // (un??)fortunately, compacting the memory is really worth it, as only 43% are actually used
        //  we could save 2.7 MiB by implementing this

        val newBytes = ByteArray(usedMemorySize)
        remapMemory(memory, usedAddresses, newBytes, allocationPtr, instanceFields)

        // todo if we're not writing certain fields after static-init, we could remove them here, too

        // todo compact strings, too:
        //  we need our new code for that,
        //  use new special const for that(?)

        // more memory was changed than just dynamic allocated things, especially the static memory space
        // todo is there more data slices that have been changed?
        appendStaticMemory(engine, printer)

        return finishMemoryReplacement(printer, newBytes)
    }

    fun justAppendData(engine: WASMEngine, printer: StringBuilder2): Int {
        appendStaticMemory(engine, printer)
        val i0 = allocationStart
        val i1 = getAllocationPointer(engine)
        val newBytes = engine.bytes.copyOfRange(i0, i1)
        return finishMemoryReplacement(printer, newBytes)
    }

    private fun appendStaticMemory(engine: WASMEngine, printer: StringBuilder2) {
        val i0 = staticFieldsStartPtr
        val i1 = staticFieldsEndPtr
        val staticFields = engine.bytes.copyOfRange(i0, i1)
        appendData(printer, i0, staticFields)
    }

    private fun collectUsedAddresses(
        engine: WASMEngine, memory: ByteBuffer, instanceFields: List<IntArray?>,
        allocationPtr: Int, usedAddresses: ArrayList<Int>
    ): Int {

        // verify our generation hasn't been used yet
        val thisIteration = 16
        forAllDynamicInstances(engine) { addr ->
            assertTrue(readClassId(memory, addr) in 0 until gIndex.classIndex.size)
            assertNotEquals(thisIteration, readGCIteration(memory, addr))
        }

        val staticValues = ArrayList<Int>()
        forAllStaticInstances(memory) { addr ->
            if (isDynamicInstance(addr, allocationPtr)) staticValues.add(addr)
        }


        var usedMemorySize = 0
        processRecursiveSet(staticValues) { addr, remaining ->
            assertTrue(isDynamicInstance(addr, allocationPtr))
            if (readGCIteration(memory, addr) != thisIteration) {
                writeGCIteration(memory, addr, thisIteration)

                usedMemorySize += getInstanceSize(memory, addr)
                usedAddresses += addr

                val classId = readClassId(memory, addr)
                assertTrue(classId in gIndex.classNames.indices)
                // println("Checking $addr, ${gIndex.classNamesByIndex[classId]}")
                if (classId !in FIRST_ARRAY..LAST_ARRAY) {
                    val offsets = instanceFields[classId]
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

        return usedMemorySize
    }

    private fun remapMemory(
        memory: ByteBuffer, usedAddresses: ArrayList<Int>,
        newBytes: ByteArray, allocationPtr: Int,
        instanceFields: List<IntArray?>
    ) {
        // when we compact things, we could as well sort them by class or size 🤔
        var newAllocationPointer = allocationStart
        usedAddresses.sort() // not necessary, probably just nice for consistent memory locality
        val memoryMap = usedAddresses.associateWith { addr ->
            val size = getInstanceSize(memory, addr)
            val newAddr = newAllocationPointer
            newAllocationPointer += size
            newAddr
        }

        val newMemory = ByteBuffer.wrap(newBytes)
            .order(ByteOrder.LITTLE_ENDIAN)

        fun mapAddress(oldAddr: Int): Int {
            return if (isDynamicInstance(oldAddr, allocationPtr)) {
                memoryMap[oldAddr]!!
            } else oldAddr
        }

        forAllStaticInstanceFields { fieldAddr ->
            val oldAddr = readPointer(memory, fieldAddr)
            writePointer(memory, fieldAddr, mapAddress(oldAddr))
        }

        val weakRefClassId = gIndex.getClassIdOrNull("jvm/custom/WeakRef")
        val weakRefAddressOffset = gIndex.getFieldOffset("jvm/custom/WeakRef", "address", "I", false)
        val dstOffset = allocationStart
        for (k in usedAddresses.indices) {
            val oldAddr = usedAddresses[k]
            val newAddr = memoryMap[oldAddr]!!
            val classId = readClassId(memory, oldAddr)
            val instanceSize = getInstanceSize(memory, oldAddr)

            // copy over all fields
            newMemory.position(newAddr - dstOffset) // copy over memory
            memory.position(oldAddr).limit(oldAddr + instanceSize)
            newMemory.put(memory).putInt(newAddr - dstOffset, classId) // set classId without bogus GC marker
            memory.position(0).limit(memory.capacity()) // reset array for further reading

            fun replaceReferenceAtOffset(offset: Int) {
                val oldAddrI = readPointer(memory, oldAddr + offset)
                val newAddrI = mapAddress(oldAddrI)
                writePointer(newMemory, newAddr + offset - dstOffset, newAddrI)
            }

            when (classId) {
                OBJECT_ARRAY -> {
                    // copy over all instances
                    val length = readArrayLength(memory, oldAddr)
                    for (i in 0 until length) {
                        replaceReferenceAtOffset(arrayOverhead + ptrSize * i)
                    }
                }
                in FIRST_ARRAY..LAST_ARRAY -> {
                    // no references need to be replaced
                }
                else -> {
                    // replace all addresses
                    val offsets = instanceFields[classId]
                    if (offsets != null) for (offset in offsets) {
                        replaceReferenceAtOffset(offset)
                    }

                    if (classId == weakRefClassId && weakRefAddressOffset != null) {
                        val oldAddrI = memory.getLong(oldAddr + weakRefAddressOffset).toInt()
                        val newAddrI = mapAddress(oldAddrI).toLong()
                        newMemory.putLong(newAddr + weakRefAddressOffset - dstOffset, newAddrI)
                    }
                }
            }
        }
    }

    private fun finishMemoryReplacement(printer: StringBuilder2, newBytes: ByteArray): Int {
        appendData(printer, allocationStart, newBytes)
        // increase allocation start after everything that was static-inited:
        //  that way we can decrease our GC efforts
        allocationStart += newBytes.size
        setGlobal("allocationStart", if (is32Bits) allocationStart else allocationStart.toLong())
        setGlobal("allocationPointer", if (is32Bits) allocationStart else allocationStart.toLong())
        return allocationStart
    }

    private fun setGlobal(name: String, value: Number) {
        globals[name]!!.initialValue = value
    }

    private fun isDynamicInstance(addr: Int, allocationPtr: Int): Boolean {
        assertTrue(addr in 0 until allocationPtr) {
            "Illegal address $addr !in 0 until $allocationPtr"
        }
        return addr >= allocationStart
    }

    private fun getArraySize(memory: ByteBuffer, addr: Int): Long {
        val classId = readClassId(memory, addr)
        assertTrue(classId in FIRST_ARRAY..LAST_ARRAY)
        val length = readArrayLength(memory, addr).toLong()
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
            getArraySize(memory, addr).toInt()
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