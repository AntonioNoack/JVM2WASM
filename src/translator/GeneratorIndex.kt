package translator

import byteStrings
import crashOnAllExceptions
import dependency.ActuallyUsedIndex
import hIndex
import isRootType
import jvm.JVM32.*
import me.anno.io.Streams.writeLE16
import me.anno.io.Streams.writeLE32
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertFail
import me.anno.utils.assertions.assertFalse
import me.anno.utils.assertions.assertTrue
import me.anno.utils.types.Booleans.toInt
import replaceClass
import useWASMExceptions
import utils.*
import utils.Param.Companion.toParams
import utils.WASMTypes.*
import wasm.instr.FuncType
import wasm.instr.Instruction
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
        val ptr = getString(str, stringStart + stringOutput.size(), stringOutput)
        stringOffset = stringStart + stringOutput.size()
        return ptr
    }

    private val stringInstanceSize = objectOverhead + 8 // ptr + hash
    fun getString(str: String, ptr: Int, buffer: ByteArrayOutputStream2): Int {
        return stringSet.getOrPut(str) {
            // append string class
            buffer.writeClass(stringClass)
            buffer.writeLE32(ptr + stringInstanceSize) // ptr to char array
            buffer.writeLE32(str.hashCode()) // hash, precomputed for faster start times :3
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
            totalStringSize += objectOverhead + ptrSize + 4 + // String
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

    val translatedMethods = HashMap<MethodSig, FunctionImpl>()

    val nthGetterMethods = HashMap<List<String>, FunctionImpl>()
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

    val classNamesByIndex = ArrayList<String>()
    val classIndex = HashMap<String, Int>()

    var lockClasses = false

    fun getClassIndex(name: String): Int {
        val name2 = replaceClass(name)
        assertEquals(name2, name)
        assertFalse('.' in name)
        if (name in classIndex) return classIndex[name]!!
        assertFalse("[]" in name)
        return if (!name.startsWith("[")) {
            if (lockClasses) throw IllegalStateException("Missing class $name")
            classIndex.getOrPut(name) {
                classNamesByIndex.add(name)
                classIndex.size
            }
        } else classIndex["[]"]!!
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
            else getClassIndex(name)
        } else classIndex["[]"]!!
    }

    val dynMethodIndices = HashMap<Int, HashMap<InterfaceSig, Int>>()

    fun getDynMethodIdx(clazz: String): Map<InterfaceSig, Int> {
        if (clazz.startsWith("[L") || clazz.startsWith("[["))
            return getDynMethodIdx("[]")
        if (lockedDynIndex) {
            return dynMethodIndices[getClassIndex(clazz)]
                ?: emptyMap()
        } else {
            val clazzMap = dynMethodIndices.getOrPut(getClassIndex(clazz)) {
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
                val superIdx = getClassIndex(hIndex.superClass[clazz]!!)
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
                    val mapped = hIndex.getAlias(MethodSig.c(clazz, name, descriptor, false))
                    if (mapped.clazz != clazz || mapped.name != name || mapped.descriptor != descriptor) {
                        getDynMethodIdx(mapped.clazz, mapped.name, mapped.descriptor)
                    } else {
                        printUsed(MethodSig.c(clazz, name, descriptor, false))
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

    class ClassOffsets(var offset: Int, private val parentFields: ClassOffsets?) {
        val fields = HashMap<String, FieldData>()

        val locked get() = locker != null
        var locker: String? = null
        fun lock(locker: String): ClassOffsets {
            this.locker = locker
            return this
        }

        override fun toString(): String {
            return "+$offset, $fields"
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

    fun getFieldOffsets(clazz: String, static: Boolean): ClassOffsets {
        // best sort all fields, and then call this function for all cases, so we get aligned accesses :)
        return fieldOffsets.getOrPut(getClassIndex(clazz) * 2 + static.toInt()) {
            if (static) {
                ClassOffsets(0, null)
            } else {
                // get parent class offset
                val parentClass = hIndex.superClass[clazz]
                if ('.' in (parentClass ?: "")) throw IllegalStateException(parentClass)
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
        assertTrue(!descriptor.endsWith(';') && descriptor != "I", descriptor)
        // best sort all fields, and then call this function for all cases, so we get aligned accesses :)
        if (lockClasses && getClassIndexOrNull(clazz) == null) return null
        val fieldOffsets = getFieldOffsets(clazz, static)
        return if (lockFields) fieldOffsets.get(name)?.offset else {
            fieldOffsets.getOrPut(name) {
                assertFalse(fieldOffsets.locked) { "$clazz has been locked by ${fieldOffsets.locker}" }
                // todo align fields, and fill them into gaps, where possible
                // if(static) println("$clazz/$name/$fieldSize")
                val offset = fieldOffsets.offset
                fieldOffsets.offset += storageSize(descriptor)
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