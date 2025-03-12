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

private fun fillInClassModifiers(
    numClasses: Int, classData: ByteArrayOutputStream2,
    classSize: Int, modifierOffset: Int
) {
    for (clazz in 0 until numClasses) {
        val dstPtr = clazz * classSize + modifierOffset
        val pos = classData.position
        val className = gIndex.classNamesByIndex[clazz]
        val modifiers = hIndex.classFlags[className] ?: 0
        classData.position = dstPtr
        classData.writeLE32(modifiers)
        classData.position = pos
    }
}

private fun fillInFields(
    numClasses: Int, classData: ByteArrayOutputStream2,
    classSize: Int, indexStartPtr: Int,
    fieldsOffset: Int,
) {
    val fieldClassIndex = gIndex.getClassIndex("java/lang/reflect/Field")
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
                appendFieldInstance(
                    field, indexStartPtr, classData,
                    fieldClassIndex, fieldNameOffset, classSize, classPtr, fieldSize
                )
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

private fun getTypeClassIndex(typeName: String): Int {
    return when (typeName) {
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
}

private fun getClassInstancePtr(classIndex: Int, indexStartPtr: Int, classSize: Int): Int {
    return indexStartPtr + classIndex * classSize
}

private fun appendFieldInstance(
    field: FieldEntry, indexStartPtr: Int, classData: ByteArrayOutputStream2,
    fieldClassIndex: Int, fieldNameOffset: Int, classSize: Int, classPtr: Int, fieldSize: Int
): Int {
    // create new field instance
    // name must be before fieldPtr, because the name might be new!!
    val name = gIndex.getString(field.name, indexStartPtr + classData.size(), classData)
    val fieldPtr = indexStartPtr + classData.size()
    classData.writeClass(fieldClassIndex)
    for (i in objectOverhead until fieldNameOffset) {
        classData.write(0)
    }
    classData.writeLE32(name) // name
    classData.writeLE32(field.field.offset) // slot
    val typeClassIndex = getTypeClassIndex(field.field.type)
    classData.writeLE32(getClassInstancePtr(typeClassIndex, indexStartPtr, classSize)) // type
    classData.writeLE32(field.modifiers) // modifiers
    classData.writeLE32(classPtr) // clazz -> declaring class
    for (i in 20 + fieldNameOffset until fieldSize) {// 20, because we wrote 5x4 bytes
        classData.write(0)
    }
    assertEquals(fieldPtr + fieldSize, indexStartPtr + classData.size())
    return fieldPtr
}

var classInstanceTablePtr = 0
fun appendClassInstanceTable(printer: StringBuilder2, indexStartPtr: Int, numClasses: Int): Int {
    LOGGER.info("[appendClassInstanceTable]")

    classInstanceTablePtr = indexStartPtr

    val classFields = gIndex.getFieldOffsets("java/lang/Class", false)
    val classClassIndex = gIndex.getClassIndex("java/lang/Class")
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

    val modifierOffset = classFields.get("modifiers")?.offset
    if (modifierOffset != null) fillInClassModifiers(numClasses, classData, classSize, modifierOffset)

    val fieldsOffset = classFields.get("fields")?.offset
    if (fieldsOffset != null) fillInFields(numClasses, classData, classSize, indexStartPtr, fieldsOffset)

    val methodsOffset = classFields.get("methods")?.offset
    if (methodsOffset != null) {
        // insert all name pointers
        val emptyArrayPtr = indexStartPtr + classData.size()
        val methodCache = HashMap<MethodSig, Int>()
        classData.writeClass(1)
        classData.writeLE32(0) // length

        val methodClassIndex = gIndex.getClassIndex("java/lang/reflect/Method")
        val methodSize = gIndex.getFieldOffsets("java/lang/reflect/Method", false).offset
        val methodNameOffset = gIndex.getFieldOffset("java/lang/reflect/Method", "name", "java/lang/String", false)!!

        for (clazz in 0 until numClasses) {
            // todo find all methods
            val methods = listOf<MethodSig>()
            // append all methods
            val dstPtr = clazz * classSize + methodsOffset
            val arrayToWrite = if (methods.isNotEmpty()) {
                val methodPointers = methods.map { method ->
                    methodCache.getOrPut(method) {
                        appendMethodInstance(
                            method, indexStartPtr, classData, methodClassIndex,
                            methodNameOffset, classSize, methodSize
                        )
                    }
                }

                val arrayPtr = indexStartPtr + classData.size()
                classData.writeClass(1)
                classData.writeLE32(methodPointers.size)
                for (methodPtr in methodPointers) {
                    classData.writeLE32(methodPtr)
                }

                arrayPtr
            } else emptyArrayPtr

            val pos = classData.position
            classData.position = dstPtr
            classData.writeLE32(arrayToWrite)
            classData.position = pos
        }
    }

    return appendData(printer, indexStartPtr, classData)

}

fun appendMethodInstance(
    sig: MethodSig, indexStartPtr: Int, classData: ByteArrayOutputStream2,
    methodClassIndex: Int, methodNameOffset: Int, classSize: Int, methodSize: Int
): Int {
    val name = gIndex.getString(sig.name, indexStartPtr, classData) // might be new -> must be before ptr-calc
    val methodPtr = indexStartPtr + classData.position
    classData.writeClass(methodClassIndex)
    for (i in objectOverhead until methodNameOffset) {
        classData.write(0)
    }
    classData.writeLE32(name) // name
    // todo write return type
    // todo write parameters
    // todo write callSignature,
    // todo write declaredClass,
    // todo write slot for calling it
    for (i in 4 until methodSize) {
        classData.write(0)
    }
    assertEquals(methodPtr, classData.position - methodSize)
    return methodPtr
}

var stackTraceTablePtr = 0
fun appendStackTraceTable(printer: StringBuilder2, ptr0: Int): Int {
    LOGGER.info("[appendThrowableLookup]")
    stackTraceTablePtr = ptr0
    return appendData(printer, ptr0, MethodTranslator.stackTraceTable)
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
var staticInitFlagTablePtr = 0
val staticLookup = HashMap<String, Int>()
fun appendStaticInstanceTable(printer: StringBuilder2, ptr0: Int, numClasses: Int): Int {
    LOGGER.info("[appendStaticInstanceTable]")
    val debugInfo = StringBuilder2()
    staticTablePtr = ptr0
    staticInitFlagTablePtr = ptr0 + 4 * numClasses // 4 bytes for offset to static memory
    var ptr = staticInitFlagTablePtr + numClasses // 1 byte for flag for init
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

fun appendData(printer: StringBuilder2, startIndex: Int, data: ByteArrayOutputStream2): Int {
    return appendData(printer, startIndex, data.toByteArray())
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

