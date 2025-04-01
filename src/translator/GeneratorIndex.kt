package translator

import alignFieldsProperly
import byteStrings
import crashOnAllExceptions
import dependency.ActuallyUsedIndex
import hIndex
import isRootType
import jvm.JVM32.*
import jvm.JVMShared.intSize
import me.anno.io.Streams.writeLE16
import me.anno.io.Streams.writeLE32
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertFail
import me.anno.utils.assertions.assertFalse
import me.anno.utils.assertions.assertTrue
import me.anno.utils.types.Booleans.toInt
import me.anno.utils.types.Ints.isPowerOf2
import replaceClass
import replaceClassNullable
import useWASMExceptions
import utils.*
import utils.Param.Companion.toParams
import utils.PrintUsed.printUsed
import utils.WASMTypes.*
import wasm.instr.FuncType
import wasm.instr.Instructions.Return
import wasm.instr.ParamGet
import wasm.parser.FunctionImpl

object GeneratorIndex {

    val actuallyUsed = ActuallyUsedIndex

    val dataStart = 64
    var stringStart = -1

    // string -> index
    val stringSet = HashMap<String, Int>()
    var totalStringSize = 0
    private var stringOffset: Int = stringStart

    var stringClass = -1
    var stringArrayClass = -1

    // here we could create an unpack function, that checks the address,
    // if even, return it, if odd, create new string and char[] on heap using byte[]
    // pro: easy to implement; con: must not be GCed... -> flag :)
    // pro: better package size; con: traffic is compressed anyway, isn't it? ; con: slower
    val stringOutput = ByteArrayOutputStream2()
    fun getString(str: String): Int {
        if (stringStart < 0) throw IllegalStateException("Strings must not be requested before the start is defined")
        val ptr = getString(str, stringStart, stringOutput)
        stringOffset = stringStart + stringOutput.size()
        return ptr
    }

    var stringInstanceSize = -1
    fun getString(str: String, ptr0: Int, buffer: ByteArrayOutputStream2): Int {
        val stringClassName = "java/lang/String"
        val stringClass = getClassId(stringClassName)
        val stringInstanceSize = getInstanceSize(stringClassName)
        return stringSet.getOrPut(str) {

            val alignment = ptrSize
            alignBuffer(buffer, alignment)

            val ptr = ptr0 + buffer.position

            // append string class
            buffer.writeClass(stringClass)
            buffer.writeLE32(str.hashCode()) // hash, precomputed for faster start times :3
            buffer.writePointer(ptr + stringInstanceSize) // ptr to char array
            buffer.fill(objectOverhead + intSize + ptrSize, stringInstanceSize)

            // append char/byte array
            buffer.writeClass(stringArrayClass)
            val length: Int
            if (byteStrings) {
                val chars = str.toByteArray()
                length = chars.size
                buffer.writeLE32(length) // length
                buffer.write(chars)
            } else {
                val chars = str.toCharArray()
                length = chars.size
                buffer.writeLE32(length) // length
                for (chr in chars) { // data
                    buffer.writeLE16(chr.code)
                }
            }

            totalStringSize += objectOverhead + ptrSize + intSize + // String
                    arrayOverhead + length * (if (byteStrings) 1 else 2) // char[]
            ptr
        }
    }

    val types = HashSet<FuncType>()

    init {
        // static call with no arguments, which can throw an exception
        val staticInitResult = if (useWASMExceptions || crashOnAllExceptions) emptyList() else listOf(ptrType)
        types.add(FuncType(emptyList(), staticInitResult))
    }

    // register type in index list
    // return name of that type
    fun getType(static: Boolean, descriptor: Descriptor, canThrow: Boolean): FuncType {
        val wasmType = descriptorToFuncType(static, descriptor, canThrow)
        types.add(wasmType)
        return wasmType
    }

    val translatedMethods = HashMap<MethodSig, FunctionImpl>(8192)

    val nthGetterMethods = HashMap<List<String>, FunctionImpl>(64)
    fun getNth(typeStack: List<String>): String {
        return nthGetterMethods.getOrPut(typeStack) {
            val name0 = typeStack.joinToString("") {
                when (it) {
                    i32 -> "i"
                    i64 -> "l"
                    f32 -> "f"
                    f64 -> "d"
                    else -> assertFail(it)
                }
            }
            val name = "getNth_$name0"
            FunctionImpl(
                name, typeStack.toParams(), typeStack + typeStack.first(),
                emptyList(), typeStack.indices.map { ParamGet[it] } + ParamGet[0] + Return,
                false
            )
        }.funcName
    }

    val classNamesByIndex = ArrayList<String>(4096)
    val classIndex = HashMap<String, Int>(4096)

    var lockClasses = false

    fun getClassId(name0: String): Int {
        val name = replaceClass(name0)
        assertFalse('.' in name)
        if (name in classIndex) return classIndex[name]!!
        assertFalse("[]" in name)
        return if (!name.startsWith("[")) {
            if (lockClasses) throw IllegalStateException("Missing class $name")

            val superClass = replaceClassNullable(hIndex.superClass[name])
            if (superClass != null && superClass !in classIndex) {
                // ensure classes are ordered
                getClassId(superClass)
            }

            addClassIndex(name)
        } else classIndex["[]"]!!
    }

    private fun addClassIndex(name: String): Int {
        return classIndex.getOrPut(name) {
            classNamesByIndex.add(name)
            classIndex.size
        }
    }

    fun getClassIndexOrNull(name: String): Int? {
        if ('.' in name) throw IllegalArgumentException(name)
        if (name in classIndex) return classIndex[name]!!
        if ("[]" in name) throw IllegalArgumentException(name)
        return if (!name.startsWith("[")) classIndex[name] else classIndex["[]"]!!
    }

    fun getClassIndexOrParents(name: String): Int {
        if ('.' in name) throw IllegalArgumentException(name)
        if (name in classIndex) return classIndex[name]!!
        if ("[]" in name) throw IllegalArgumentException(name)
        return if (!name.startsWith("[")) {
            if (lockClasses) getClassIndexOrParents(
                hIndex.superClass[name]
                    ?: "java/lang/Object"
            )
            else getClassId(name)
        } else classIndex["[]"]!!
    }

    val dynMethodIndices = HashMap<Int, HashMap<InterfaceSig, Int>>()

    fun getDynMethodIdx(clazz: String): Map<InterfaceSig, Int> {
        if (clazz.startsWith("[L") || clazz.startsWith("[["))
            return getDynMethodIdx("[]")
        if (lockedDynIndex) {
            return dynMethodIndices[getClassId(clazz)]
                ?: emptyMap()
        } else {
            val clazzMap = dynMethodIndices.getOrPut(getClassId(clazz)) {
                // find parent class, and index all their functions!!
                if (isRootType(clazz)) {
                    HashMap()
                } else {
                    val superClass =
                        hIndex.superClass[clazz] ?: throw NullPointerException("Missing super clazz of $clazz")
                    val baseFields = getDynMethodIdx(superClass)
                    HashMap(baseFields)
                }
            }
            if (!isRootType(clazz)) {
                val superIdx = getClassId(hIndex.superClass[clazz]!!)
                val superTable = dynMethodIndices[superIdx]
                    ?: throw IllegalStateException("Missing table for ${hIndex.superClass[clazz]}")
                if (clazzMap.size < superTable.size) {
                    throw IllegalStateException("$clazzMap of $clazz is missing entries from super class ${hIndex.superClass[clazz]}: $superTable")
                }
            }
            return clazzMap
        }
    }

    var lockedDynIndex = false
    fun getDynMethodIdx(clazz: String, name: String, descriptor: Descriptor): Int {
        if (clazz.startsWith("[L") || clazz.startsWith("[["))
            return getDynMethodIdx("[]", name, descriptor)
        val clazzMap = getDynMethodIdx(clazz)
        return if (lockedDynIndex) {
            clazzMap[InterfaceSig.c(name, descriptor)]
                ?: kotlin.run {
                    val mapped = hIndex.getAlias(MethodSig.c(clazz, name, descriptor))
                    if (mapped.clazz != clazz || mapped.name != name || mapped.descriptor != descriptor) {
                        getDynMethodIdx(mapped.clazz, mapped.name, mapped.descriptor)
                    } else {
                        printUsed(MethodSig.c(clazz, name, descriptor))
                        throw IllegalStateException("Missed $clazz/$name/$descriptor, only found $clazzMap")
                    }
                }
        } else {
            val sig = InterfaceSig.c(name, descriptor)
            (clazzMap as HashMap).getOrPut(sig) { clazzMap.size }
        }
    }

    fun getDynMethodIdx(sig: MethodSig): Int {
        return getDynMethodIdx(sig.clazz, sig.name, sig.descriptor)
    }

    data class FieldData(val offset: Int, val type: String)
    data class Gap(val offset: Int, val size: Int)

    class ClassOffsets(var offset: Int, private val parentFields: ClassOffsets?) {

        val fields = HashMap<String, FieldData>()
        val fieldGaps = ArrayList<Gap>()

        var staticOffsetPtr = -1

        init {
            if (parentFields != null) {
                fieldGaps.addAll(parentFields.fieldGaps)
            }
        }

        val locked get() = locker != null
        var locker: String? = null
        fun lock(locker: String): ClassOffsets {
            this.locker = locker
            return this
        }

        override fun toString(): String {
            return "+$offset, $fields"
        }

        /**
         * finds gap and returns the offset for it;
         * increases class size if necessary
         * */
        fun findAndRemoveGap(newFieldSize: Int): Int {
            if (alignFieldsProperly) {
                val gap = findGap(newFieldSize)
                if (gap != null) {
                    return removeGap(gap, newFieldSize)
                } else {
                    val remainder = offset % newFieldSize
                    if (remainder != 0) {
                        // align properly
                        fieldGaps.add(Gap(offset, remainder))
                        offset += remainder
                    }
                }
            }
            return findGapUnaligned(newFieldSize)
        }

        private fun findGapUnaligned(newFieldSize: Int): Int {
            val fieldOffset = offset
            offset += newFieldSize
            return fieldOffset
        }

        private fun findGap(newFieldSize: Int): Gap? {
            return fieldGaps
                .filter { it.size >= newFieldSize }
                .minByOrNull { it.size }
        }

        private fun removeGap(gap: Gap, newFieldSize: Int): Int {
            assertTrue(newFieldSize <= gap.size)
            assertTrue(fieldGaps.remove(gap))
            if (newFieldSize < gap.size) {
                // insert remainder back into list
                fieldGaps.add(Gap(gap.offset, gap.size - newFieldSize))
            }
            // remove at the end of the gap
            return gap.offset + gap.size - newFieldSize
        }

        fun allFields(): Map<String, FieldData> {
            val result = HashMap<String, FieldData>()
            var offsets = this
            while (true) {
                result.putAll(offsets.fields)
                offsets = offsets.parentFields ?: break
            }
            return result
        }

        fun hasFields(): Boolean {
            var offsets = this
            while (true) {
                if (offsets.fields.isNotEmpty()) return true
                offsets = offsets.parentFields ?: break
            }
            return false
        }

        fun get(name: String): FieldData? {
            return fields[name] ?: parentFields?.get(name)
        }

        fun getOffset(name: String): Int? {
            return get(name)?.offset
        }

        fun getOrPut(name: String, put: () -> FieldData): FieldData {
            val prev = get(name)
            if (prev != null) return prev
            val newInstance = put()
            fields[name] = newInstance
            return newInstance
        }
    }

    val fieldOffsets = HashMap<Int, ClassOffsets>(8192)
    var lockFields = false

    fun getInstanceSize(clazz: String): Int {
        val offsets = getFieldOffsets(clazz, false)
            .lock("getInstanceSize")
        return alignPointer(offsets.offset)
    }

    fun alignPointer(ptr: Int, alignment1: Int = alignment): Int {
        assertTrue(alignment1.isPowerOf2())
        val remainder = ptr and (alignment1 - 1)
        return if (remainder > 0) ptr + alignment1 - remainder else ptr
    }

    fun alignBuffer(buffer: ByteArrayOutputStream2, alignment1: Int = alignment) {
        buffer.fill(buffer.position, alignPointer(buffer.position, alignment1))
    }

    // alignment of 4 is needed for Float[] in WebGL anyway
    // if we want proper alignment of everything, we need to account for longs and doubles,
    // and therefore must use 8-byte alignment
    val alignment = if (is32Bits && !alignFieldsProperly) 4 else 8

    fun checkAlignment(ptr: Int, alignment1: Int = alignment) {
        assertEquals(ptr, alignPointer(ptr, alignment1))
    }

    fun getFieldOffsets(clazz0: String, static: Boolean): ClassOffsets {
        val clazz = replaceClass(clazz0)
        // best sort all fields, and then call this function for all cases, so we get aligned accesses :)
        return fieldOffsets.getOrPut(getClassId(clazz) * 2 + static.toInt()) {
            if (static) {
                ClassOffsets(0, null)
            } else {
                // get parent class offset
                val parentClass = hIndex.superClass[clazz]
                if (parentClass != null) {
                    val parentOffset = getFieldOffsets(parentClass, false).lock(clazz)
                    ClassOffsets(parentOffset.offset, parentOffset)
                } else {
                    ClassOffsets(objectOverhead, null)
                }
            }
        }
    }

    fun getFieldOffset(clazz: String, name: String, descriptor: String, static: Boolean): Int? {
        assertTrue(!descriptor.endsWith(';'), descriptor)
        // best sort all fields, and then call this function for all cases, so we get aligned accesses :)
        if (lockClasses && getClassIndexOrNull(clazz) == null) return null
        val fieldOffsets = getFieldOffsets(clazz, static)
        return if (lockFields) fieldOffsets.getOffset(name) else {
            fieldOffsets.getOrPut(name) {
                assertFalse(fieldOffsets.locked) { "$clazz has been locked by ${fieldOffsets.locker}" }
                val fieldSize = storageSize(descriptor)
                val offset = fieldOffsets.findAndRemoveGap(fieldSize)
                FieldData(offset, descriptor)
            }.offset
        }
    }

    val interfaceIndex = HashMap<InterfaceSig, Int>()
    fun getInterfaceIndex(key: InterfaceSig): Int {
        // clazz isn't really needed, because there cannot be collisions
        return interfaceIndex.getOrPut(key) {
            // println("interface#${interfaceIndex.size} by $clazz: $name, $descriptor")
            interfaceIndex.size
        }
    }

}