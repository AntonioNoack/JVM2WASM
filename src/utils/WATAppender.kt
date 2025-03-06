package utils

import gIndex
import hIndex
import jvm.JVM32.arrayOverhead
import jvm.JVM32.objectOverhead
import me.anno.io.Streams.writeLE32
import me.anno.utils.Color
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.Compare.ifSame
import me.anno.utils.types.Booleans.toInt
import org.apache.logging.log4j.LogManager
import org.objectweb.asm.Opcodes.ACC_NATIVE
import org.objectweb.asm.Opcodes.ACC_STATIC
import resources
import translator.GeneratorIndex
import translator.GeneratorIndex.stringStart
import translator.MethodTranslator
import wasm.parser.DataSection
import java.io.OutputStream
import kotlin.math.abs

private val LOGGER = LogManager.getLogger("WATAppender")

fun OutputStream.writeClass(clazz: Int) {
    if (clazz < 0 || clazz > 0xffffff) throw IllegalArgumentException()
    writeLE32(clazz) // class
    for (i in 4 until objectOverhead) write(0)
}

data class FieldEntry(val name: String, val field: GeneratorIndex.FieldData, val modifiers: Int)

fun OutputStream.writeEmptyClass(clazzIndex: Int, classSize: Int) {
    writeClass(clazzIndex)
    for (j in objectOverhead until classSize) { // nulls for the start
        write(0)
    }
}

/**
 * returns whether a type is native;
 * all objects and arrays are not native, and therefore need garbage collection;
 * all integers, chars and floats are native
 * */
fun isNativeType(type: String) = when (type) {
    "I", "F", "J", "D", "Z", "B", "S", "C" -> true
    else -> false
}

/**
 * insert all name pointers
 * */
private fun fillInClassNames(
    numClasses: Int, classData: ByteArrayOutputStream2,
    classSize: Int, nameOffset: Int, indexStartPtr: Int
) {
    for (clazz in 0 until numClasses) {
        val name = gIndex.classNamesByIndex[clazz].replace('/', '.') // getName() returns name with dots
        val strPtr = gIndex.getString(name, indexStartPtr + classData.size(), classData)
        val dstPtr = clazz * classSize + nameOffset
        val pos = classData.position
        classData.position = dstPtr
        classData.writeLE32(strPtr)
        classData.position = pos
    }
}

private fun fillInClassIndices(
    numClasses: Int, classData: ByteArrayOutputStream2,
    classSize: Int, indexOffset: Int
) {
    for (clazz in 0 until numClasses) {
        val dstPtr = clazz * classSize + indexOffset
        val pos = classData.position
        classData.position = dstPtr
        classData.writeLE32(clazz)
        classData.position = pos
    }
}

var classInstanceTablePtr = 0
fun appendClassInstanceTable(printer: StringBuilder2, indexStartPtr: Int, numClasses: Int): Int {
    LOGGER.info("[appendClassInstanceTable]")

    classInstanceTablePtr = indexStartPtr

    val classFields = gIndex.getFieldOffsets("java/lang/Class", false)
    val classClassIndex = gIndex.getClassIndex("java/lang/Class")
    val fieldClassIndex = gIndex.getClassIndex("java/lang/reflect/Field")
    val classSize = classFields.offset

    LOGGER.info("java/lang/Class.fields: ${classFields.fields.entries.sortedBy { it.value.offset }}, total size: $classSize")

    val classData = ByteArrayOutputStream2(classSize * numClasses)
    for (i in 0 until numClasses) {
        classData.writeEmptyClass(classClassIndex, classSize)
    }

    val nameOffset = classFields.get("name")?.offset
    if (nameOffset != null) fillInClassNames(numClasses, classData, classSize, nameOffset, indexStartPtr)

    val indexOffset = classFields.get("index")?.offset
    if (indexOffset != null) fillInClassIndices(numClasses, classData, classSize, indexOffset)

    val fieldsOffset = classFields.get("fields")?.offset
    if (fieldsOffset != null) {
        val fieldCache = HashMap<FieldEntry, Int>()
        val fieldOffsets0 = gIndex.getFieldOffsets("java/lang/reflect/Field", false)
        val fieldNameOffset = fieldOffsets0.get("name")!!.offset
        val fieldSize = fieldOffsets0.offset
        for (clazz in 0 until numClasses) {

            val className = gIndex.classNamesByIndex[clazz]
            val classPtr = indexStartPtr + clazz * classSize
            // if (className !in dIndex.constructableClasses) continue

            val instanceModifier = 0
            val staticModifier = ACC_STATIC
            val instanceFields = gIndex.getFieldOffsets(className, false).fields
                .entries.map { (name, field) ->
                    FieldEntry(name, field, instanceModifier + isNativeType(field.type).toInt(ACC_NATIVE))
                }

            val staticFields = gIndex.getFieldOffsets(className, true).fields
                .entries.map { (name, field) ->
                    FieldEntry(name, field, staticModifier + isNativeType(field.type).toInt(ACC_NATIVE))
                }

            val allFields = (instanceFields + staticFields)
                .sortedWith { a, b ->
                    // this sorting could be used to optimize a little
                    // todo do we use this sorting anywhere? if not, we could remove it, too
                    val na = !isNativeType(a.field.type)
                    val nb = !isNativeType(b.field.type)
                    na.compareTo(nb).ifSame { a.name.compareTo(b.name) }
                }

            if (allFields.isEmpty()) {
                // let the field-array pointer be null
                continue
            }

            val fieldPointers = IntArray(allFields.size) {
                val field = allFields[it]
                fieldCache.getOrPut(field) {
                    // create new field instance
                    val name = gIndex.getString(field.name, indexStartPtr + classData.size(), classData)
                    val fieldPtr = indexStartPtr + classData.size()
                    classData.writeClass(fieldClassIndex)
                    for (i in objectOverhead until fieldNameOffset) {
                        classData.write(0)
                    }
                    classData.writeLE32(name) // name
                    classData.writeLE32(field.field.offset) // slot
                    val ci = when (val typeName = field.field.type) {
                        in NativeTypes.nativeTypes -> gIndex.getClassIndex(typeName)
                        "[]" -> gIndex.getClassIndex("[]")
                        else -> {
                            if (
                                typeName.startsWith("[") &&
                                typeName.length == 2 &&
                                typeName[1] in NativeTypes.joined
                            ) { // native array
                                gIndex.getClassIndex(typeName)
                            } else if (typeName.startsWith("[")) {
                                gIndex.getClassIndex("[]")
                            } else {
                                gIndex.getClassIndexOrParents(typeName)
                            }
                        }
                    }
                    classData.writeLE32(indexStartPtr + classSize * ci) // type
                    classData.writeLE32(field.modifiers)
                    classData.writeLE32(classPtr) // clazz -> declaring class
                    for (i in 20 + fieldNameOffset until fieldSize) {// 20, because we wrote 5x4 bytes
                        classData.write(0)
                    }
                    assertEquals(fieldPtr + fieldSize, indexStartPtr + classData.size())
                    fieldPtr
                }
            }

            if (clazz < 10) {
                LOGGER.info(
                    "Fields for $clazz [${indexStartPtr + clazz * classSize}]: $className -> $allFields, " +
                            allFields.joinToString { "${gIndex.getString(it.name)}" })
            } else if (clazz == 10 && numClasses > 11) LOGGER.info("...")

            // create new fields array
            val arrayPtr = indexStartPtr + classData.size()
            classData.writeClass(1) // object array
            classData.writeLE32(allFields.size) // array length

            for (fieldPtr in fieldPointers) {
                classData.writeLE32(fieldPtr)
            }

            val dstPtr = clazz * classSize + fieldsOffset
            val pos = classData.position
            classData.position = dstPtr
            classData.writeLE32(arrayPtr)
            classData.position = pos

        }
    }

    val methodsOffset = classFields.get("methods")?.offset
    if (methodsOffset != null) {
        // insert all name pointers
        val emptyArrayPtr = indexStartPtr + classData.size()
        classData.writeClass(1)
        classData.writeLE32(0) // length
        for (clazz in 0 until numClasses) {
            // todo find all methods
            // todo append all methods
            val dstPtr = clazz * classSize + methodsOffset
            val pos = classData.position
            classData.position = dstPtr
            classData.writeLE32(emptyArrayPtr)
            classData.position = pos
        }
    }

    return appendData(printer, indexStartPtr, classData)

}

var throwableLookupPtr = 0
fun appendThrowableLookup(printer: StringBuilder2, ptr0: Int): Int {
    LOGGER.info("[appendThrowableLookup]")
    throwableLookupPtr = ptr0
    return appendData(printer, ptr0, MethodTranslator.callTable)
}

var resourceTablePtr = 0
fun appendResourceTable(printer: StringBuilder2, ptr0: Int): Int {
    LOGGER.info("[appendResourceTable]")
    resourceTablePtr = ptr0
    val resources = resources
    val table = ByteArrayOutputStream2(
        resources.size * 8 + 4 +
                resources.sumOf { it.first.length + it.second.size }
                + resources.size * arrayOverhead * 2
    )
    table.writeLE32(resources.size)
    for (i in 0 until resources.size * 8) { // fill pointers with data :)
        table.write(0)
    }
    for (i in resources.indices) {
        val resource = resources[i]
        val keyPtr = gIndex.getString(resource.first, ptr0 + table.position, table)
        val valuePtr = ptr0 + table.position
        val value = resource.second
        table.writeClass(5) // byte[]
        table.writeLE32(value.size)
        table.write(value)
        val pos = table.position
        table.position = i * 8 + 4
        table.writeLE32(keyPtr)
        table.writeLE32(valuePtr)
        table.position = pos
    }
    return appendData(printer, ptr0, table)
}

fun appendFunctionTypes(printer: StringBuilder2) {
    // append all wasm types
    // (type $ft (func (param i32)))
    for (type in gIndex.types) {
        printer.append("(type \$")
        type.toString(printer)
        printer.append(" (func (param")
        for (param in type.params) {
            printer.append(' ').append(param)
        }
        printer.append(") (result")
        for (result in type.results) {
            printer.append(' ').append(result)
        }
        printer.append(")))\n")
    }
}

fun calculateNameToMethod(): Map<String, MethodSig> {
    return hIndex.methodsByClass.map { it.value }.flatten()
        .associateBy { methodName(it) }
}

val functionTable = ArrayList<String>()

var printDebug = true

var staticTablePtr = -1
var clInitFlagTable = 0
val staticLookup = HashMap<String, Int>()
fun appendStaticInstanceTable(printer: StringBuilder2, ptr0: Int, numClasses: Int): Int {
    LOGGER.info("[appendStaticInstanceTable]")
    val debugInfo = StringBuilder2()
    staticTablePtr = ptr0
    clInitFlagTable = ptr0 + 4 * numClasses // 4 bytes for offset to static memory
    var ptr = clInitFlagTable + numClasses // 1 byte for flag for init
    val staticBuffer = ByteArrayOutputStream2(numClasses * 4)
    for (i in 0 until numClasses) {
        val className = gIndex.classNamesByIndex[i]
        val fieldOffsets = gIndex.getFieldOffsets(className, true)
        val size = fieldOffsets.offset
        if (size == 0) {
            staticBuffer.writeLE32(0)
        } else {
            // println("writing $i static $className to $ptr, size: $size")
            staticBuffer.writeLE32(ptr)
            staticLookup[className] = ptr
            if (printDebug) {
                debugInfo.append("[").append(i).append("] ")
                    .append(className).append(": *").append(ptr).append("\n")
                fieldOffsets.allFields().entries.sortedBy { it.value.offset }.forEach { (name, data) ->
                    debugInfo.append("  *").append(data.offset).append(": ").append(name)
                        .append(": ").append(data.type).append("\n")
                }
            }
            ptr += size
        }
    }
    if (printDebug) {
        debugFolder.getChild("staticInstances.txt")
            .writeBytes(debugInfo.values, 0, debugInfo.size)
    }
    val ptr2 = appendData(printer, staticTablePtr, staticBuffer)
    assertTrue(ptr >= ptr2)
    return ptr
}

val segments = ArrayList<DataSection>()

fun appendData(printer: StringBuilder2, startIndex: Int, vararg data: ByteArrayOutputStream2): Int {
    val totalSize = data.sumOf { it.size() }
    val joined = ByteArray(totalSize)
    var offset = 0
    for (dataI in data) {
        dataI.data.values.copyInto(joined, offset, 0, dataI.size())
        offset += dataI.size()
    }
    return appendData(printer, startIndex, joined)
}

fun appendData(printer: StringBuilder2, startIndex: Int, data: ByteArray): Int {
    val segment = startIndex until (startIndex + data.size)
    val mid1 = segment.first + segment.last
    val length1 = segment.last - segment.first
    for (seg in segments) {
        val first = seg.startIndex
        val last = seg.startIndex + seg.content.size
        val mid2 = first + last
        val length2 = last - first
        if (abs(mid1 - mid2) < length1 + length2)
            throw IllegalStateException("Overlapping segments $segment, $seg")
    }
    segments.add(DataSection(startIndex, data))
    printer.append("(data ($ptrType.const ${startIndex}) \"")
    writeData(printer, data)
    printer.append("\")\n")
    return startIndex + data.size
}

fun appendStringData(printer: StringBuilder2, gIndex: GeneratorIndex): Int {
    LOGGER.info("[appendStringData]")
    return appendData(printer, stringStart, gIndex.stringOutput)
}

fun writeData(printer: StringBuilder2, bytes: ByteArray) {
    printer.ensureExtra(3 * bytes.size)
    for (b in bytes) {
        val i = b.toInt()
        val c = i.toChar()
        if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9') printer.append(c)
        else printer.append('\\').append(Color.hex4(i shr 4)).append(Color.hex4(i))
    }
}

