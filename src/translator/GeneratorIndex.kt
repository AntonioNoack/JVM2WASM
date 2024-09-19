package translator

import byteStrings
import dependency.ActuallyUsedIndex
import hIndex
import isRootType
import jvm.JVM32.*
import me.anno.io.Streams.writeLE16
import me.anno.io.Streams.writeLE32
import me.anno.utils.types.Booleans.toInt
import replaceClass1
import utils.*

@Suppress("PropertyName")
object GeneratorIndex {

    val actuallyUsed = ActuallyUsedIndex

    val dataStart = 64
    var stringStart = -1

    private fun code(type: String) = when (type) {
        i32 -> 0
        i64 -> 1
        f32 -> 2
        f64 -> 3
        else -> throw IllegalArgumentException(type)
    }

    fun tri(a: String, b: String, c: String) = tri(code(a), code(b), code(c))
    fun tri(a: Int, b: Int, c: Int) = a + b * 4 + c * 16
    fun pair(a: String, b: String) = pair(code(a), code(b))
    fun pair(a: Int, b: Int) = a + b * 4

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

    val types = HashSet<String>()

    init {
        // static call with no arguments, which can throw an exception
        types.add("\$sRV0")
    }

    // register type in index list
    // return name of that type
    fun getType(descriptor: String, canThrow: Boolean): String {
        val wasmType = splitToType(descriptor, canThrow)
        types.add(wasmType)
        return wasmType
    }

    val translatedMethods = HashMap<MethodSig, String>()

    val nthGetterMethods = HashMap<List<String>, GenericSig>()
    fun getNth(typeStack: List<String>): String {
        return nthGetterMethods.getOrPut(typeStack) {
            val name = "getNth_${nthGetterMethods.size}"
            val method = Builder(
                "(func \$$name (param ${typeStack.joinToString(" ")}) " +
                        "(result ${typeStack.joinToString(" ")} ${typeStack.first()})"
            )
            for (i in typeStack.indices) {
                method.append(" local.get $i")
            }
            method.append(" local.get 0)\n") // the actual value, we're interested in
            GenericSig(name, method.toString())
        }.name
    }

    val usedDup_x1 = BooleanArray(16)
    val usedDup_x2 = BooleanArray(64)

    val usedDup2_x1 = BooleanArray(64)

    val classNames = ArrayList<String>()
    val classIndex = HashMap<String, Int>()

    var lockClasses = false

    fun getClassIndex(name: String): Int {
        val name2 = replaceClass1(name)
        if (name != name2) TODO()
        if ('.' in name) throw IllegalArgumentException(name)
        if (name in classIndex) return classIndex[name]!!
        if ("[]" in name) throw IllegalArgumentException(name)
        return if (!name.startsWith("[")) {
            if (lockClasses) throw IllegalStateException("Missing class $name")
            classIndex.getOrPut(name) {
                classNames.add(name)
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

    val dynMethodIndices = HashMap<Int, HashMap<GenericSig, Int>>()

    fun getDynMethodIdx(clazz: String): Map<GenericSig, Int> {
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
    fun getDynMethodIdx(clazz: String, name: String, descriptor: String): Int {
        if (clazz.startsWith("[L") || clazz.startsWith("[["))
            return getDynMethodIdx("[]", name, descriptor)
        val clazzMap = getDynMethodIdx(clazz)
        return if (lockedDynIndex) {
            clazzMap[GenericSig(name, descriptor)]
                ?: kotlin.run {
                    val mapped = hIndex.methodAliases[methodName(clazz, name, descriptor)]
                    if (mapped != null && (mapped.clazz != clazz || mapped.name != name || mapped.descriptor != descriptor)) {
                        getDynMethodIdx(mapped.clazz, mapped.name, mapped.descriptor)
                    } else {
                        printUsed(MethodSig.c(clazz, name, descriptor))
                        throw IllegalStateException("Missed $clazz/$name/$descriptor, only found $clazzMap")
                    }
                }
        } else {
            (clazzMap as HashMap).getOrPut(GenericSig(name, descriptor)) {
                clazzMap.size
            }
        }
    }

    fun getDynMethodIdx(sig: MethodSig): Int {
        return getDynMethodIdx(sig.clazz, sig.name, sig.descriptor)
    }

    data class FieldData(val offset: Int, val type: String)

    class ClassOffsets(var offset: Int, val fields: HashMap<String, FieldData>) {
        val locked get() = locker != null
        var locker: String? = null
        fun lock(locker: String): ClassOffsets {
            this.locker = locker
            return this
        }

        override fun toString(): String {
            return "+$offset, $fields"
        }
    }

    val fieldOffsets = HashMap<Int, ClassOffsets>()

    var lockFields = false

    fun getFieldOffsets(clazz: String, static: Boolean): ClassOffsets {
        // best sort all fields, and then call this function for all cases, so we get aligned accesses :)
        return fieldOffsets.getOrPut(getClassIndex(clazz) * 2 + static.toInt()) {
            if (static) {
                ClassOffsets(0, HashMap())
            } else {
                // get parent class offset
                val parentClass = hIndex.superClass[clazz]
                if ('.' in (parentClass ?: "")) throw IllegalStateException(parentClass)
                if (parentClass != null) {
                    val parentOffset = getFieldOffsets(parentClass, false).lock(clazz)
                    ClassOffsets(parentOffset.offset, HashMap(parentOffset.fields))
                } else {
                    ClassOffsets(objectOverhead, HashMap())
                }
            }
        }
    }

    fun getFieldOffset(clazz: String, name: String, descriptor: String, static: Boolean): Int? {
        // best sort all fields, and then call this function for all cases, so we get aligned accesses :)
        if (lockClasses && getClassIndexOrNull(clazz) == null) return null
        val fos = getFieldOffsets(clazz, static)
        return if (lockFields) fos.fields[name]?.offset else {
            fos.fields.getOrPut(name) {
                if (!static && clazz == "java/lang/System") TODO("$clazz/$name/$descriptor/$static")
                if (fos.locked) throw IllegalStateException("$clazz has been locked by ${fos.locker}")
                // if(static) println("$clazz/$name/$fieldSize")
                val offset = fos.offset
                fos.offset += storageSize(descriptor)
                FieldData(offset, descriptor)
            }.offset
        }
    }

    val interfaceIndex = HashMap<GenericSig, Int>()
    fun getInterfaceIndex(clazz: String, name: String, descriptor: String): Int {
        // clazz isn't really needed, because there cannot be collisions
        return interfaceIndex.getOrPut(GenericSig(name, descriptor)) {
            // println("interface#${interfaceIndex.size} by $clazz: $name, $descriptor")
            interfaceIndex.size
        }
    }

}