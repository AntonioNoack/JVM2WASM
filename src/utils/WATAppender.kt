package utils

import dIndex
import gIndex
import hIndex
import jvm.JVM32.*
import jvm.JVMShared.intSize
import me.anno.io.Streams.writeLE32
import me.anno.io.Streams.writeLE64
import me.anno.utils.Color
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertTrue
import me.anno.utils.types.Booleans.toInt
import org.apache.logging.log4j.LogManager
import org.objectweb.asm.Opcodes.ACC_NATIVE
import org.objectweb.asm.Opcodes.ACC_STATIC
import resources
import translator.GeneratorIndex
import translator.GeneratorIndex.alignBuffer
import translator.GeneratorIndex.alignPointer
import translator.GeneratorIndex.checkAlignment
import translator.GeneratorIndex.stringStart
import translator.MethodTranslator
import utils.StaticClassIndices.BYTE_ARRAY
import utils.StaticClassIndices.OBJECT_ARRAY
import utils.StaticFieldOffsets.OFFSET_CLASS_METHODS
import utils.StaticFieldOffsets.OFFSET_METHOD_SLOT
import wasm.parser.DataSection
import java.io.OutputStream
import kotlin.math.abs

private val LOGGER = LogManager.getLogger("WATAppender")

fun OutputStream.writeClass(clazz: Int) {
    assertTrue(clazz in 0 until gIndex.classIndex.size)
    assertTrue(clazz < 0xffffff) // else there is GC issues
    writeLE32(clazz) // class
    fill(4, objectOverhead)
}

fun OutputStream.writePointer(value: Int) {
    if (is32Bits) writeLE32(value)
    else writeLE64(value.toLong())
}

fun OutputStream.fill(from: Int, to: Int) {
    assertTrue(from <= to)
    for (i in from until to) write(0)
}

data class FieldEntry(val name: String, val field: GeneratorIndex.FieldData, val modifiers: Int)

/**
 * returns whether a type is native;
 * all objects and arrays are not native, and therefore need garbage collection;
 * all integers, chars and floats are native
 * */
fun isNativeType(type: String) = when (type) {
    "I", "F", "J", "D", "Z", "B", "S", "C" -> throw IllegalArgumentException()
    else -> type in NativeTypes.nativeTypes
}

private fun fillInClassNames(numClasses: Int, classData: ByteArrayOutputStream2, classSize: Int) {
    for (clazz in 0 until numClasses) {
        val name = gIndex.classNamesByIndex[clazz].replace('/', '.') // getName() returns name with dots
        checkAlignment(classData.position)
        val strPtr = gIndex.getString(name, classInstanceTablePtr, classData)
        checkAlignment(classData.position)
        val pos = classData.position
        classData.position = clazz * classSize + StaticFieldOffsets.OFFSET_CLASS_NAME
        classData.writePointer(strPtr)
        classData.position = pos
    }
}

private fun fillInClassSimpleNames(numClasses: Int, classData: ByteArrayOutputStream2, classSize: Int) {
    for (clazz in 0 until numClasses) {
        val fullName = gIndex.classNamesByIndex[clazz]
        val simpleName = fullName.split('/', '.').last()
        val strPtr = gIndex.getString(simpleName, classInstanceTablePtr, classData)
        val pos = classData.position
        classData.position = clazz * classSize + StaticFieldOffsets.OFFSET_CLASS_SIMPLE_NAME
        classData.writePointer(strPtr)
        classData.position = pos
    }
}

private fun fillInClassIndices(numClasses: Int, classData: ByteArrayOutputStream2, classSize: Int) {
    for (classIndex in 0 until numClasses) {
        val pos = classData.position
        classData.position = classIndex * classSize + StaticFieldOffsets.OFFSET_CLASS_INDEX
        classData.writeLE32(classIndex)
        classData.position = pos
    }
}

private fun fillInClassModifiers(numClasses: Int, classData: ByteArrayOutputStream2, classSize: Int) {
    for (clazz in 0 until numClasses) {
        val dstPtr = clazz * classSize + StaticFieldOffsets.OFFSET_CLASS_MODIFIERS
        val pos = classData.position
        val className = gIndex.classNamesByIndex[clazz]
        val modifiers = hIndex.classFlags[className] ?: 0
        classData.position = dstPtr
        classData.writePointer(modifiers)
        classData.position = pos
    }
}

private fun fillInFields(
    numClasses: Int, classData: ByteArrayOutputStream2,
    classSize: Int, indexStartPtr: Int,
) {

    checkAlignment(indexStartPtr)

    val emptyFieldPtr = indexStartPtr + classData.position
    classData.writeClass(OBJECT_ARRAY)
    classData.writeLE32(0) // length = 0

    val fieldClassIndex = gIndex.getClassIndex("java/lang/reflect/Field")
    val fieldSize = gIndex.getInstanceSize("java/lang/reflect/Field")
    val fieldCache = HashMap<FieldEntry, Int>()
    for (classIndex in 0 until numClasses) {

        val className = gIndex.classNamesByIndex[classIndex]

        val instanceFields0 = gIndex.getFieldOffsets(className, false).fields
        val staticFields0 = gIndex.getFieldOffsets(className, true).fields

        if (instanceFields0.isEmpty() && staticFields0.isEmpty()) {
            val pos = classData.position
            classData.position = classIndex * classSize + StaticFieldOffsets.OFFSET_CLASS_FIELDS
            classData.writePointer(emptyFieldPtr)
            classData.position = pos
            continue
        }

        val instanceFields = instanceFields0.map { (name, field) ->
            FieldEntry(name, field, isNativeType(field.type).toInt(ACC_NATIVE))
        }

        val staticFields = staticFields0.map { (name, field) ->
            FieldEntry(name, field, ACC_STATIC + isNativeType(field.type).toInt(ACC_NATIVE))
        }

        val allFields = instanceFields + staticFields
        val fieldPointers = IntArray(allFields.size) {
            val field = allFields[it]
            fieldCache.getOrPut(field) {
                appendFieldInstance(
                    field, indexStartPtr, classData,
                    classIndex, fieldClassIndex,
                    classSize, fieldSize
                )
            }
        }

        if (classIndex < 10) {
            LOGGER.info(
                "Fields for $classIndex [${indexStartPtr + classIndex * classSize}]: $className -> $allFields, " +
                        allFields.joinToString { "${gIndex.getString(it.name)}" })
        } else if (classIndex == 10 && numClasses > 11) LOGGER.info("...")

        // create new fields array
        alignBuffer(classData, ptrSize)
        val arrayPtr = indexStartPtr + classData.size()
        classData.writeClass(OBJECT_ARRAY) // object array
        classData.writeLE32(allFields.size) // array length
        for (fieldPtr in fieldPointers) {
            classData.writePointer(fieldPtr)
        }

        val pos = classData.position
        classData.position = classIndex * classSize + StaticFieldOffsets.OFFSET_CLASS_FIELDS
        classData.writePointer(arrayPtr)
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
    declaringClassIndex: Int, fieldClassIndex: Int,
    classSize: Int, fieldSize: Int
): Int {
    // create new field instance
    // name must be before fieldPtr, because the name might be new!!
    val name = gIndex.getString(field.name, indexStartPtr, classData)
    alignBuffer(classData)
    val fieldPtr = indexStartPtr + classData.size()
    classData.writeClass(fieldClassIndex)
    classData.writeLE32(field.field.offset) // slot
    classData.writePointer(name) // name
    val typeClassIndex = getTypeClassIndex(field.field.type)
    classData.writePointer(getClassInstancePtr(typeClassIndex, indexStartPtr, classSize)) // type
    classData.writePointer(getClassInstancePtr(declaringClassIndex, indexStartPtr, classSize)) // declaring class
    classData.writeLE32(field.modifiers) // modifiers
    classData.fill(objectOverhead + 2 * intSize + 3 * ptrSize, fieldSize)
    assertEquals(fieldPtr + fieldSize, indexStartPtr + classData.size())
    checkAlignment(classData.position)
    return fieldPtr
}

var classInstanceTablePtr = 0
fun appendClassInstanceTable(printer: StringBuilder2, indexStartPtr: Int, numClasses: Int): Int {
    LOGGER.info("[appendClassInstanceTable]")

    checkAlignment(indexStartPtr)
    classInstanceTablePtr = indexStartPtr

    val classFields = gIndex.getFieldOffsets("java/lang/Class", false)
    val classClassIndex = gIndex.getClassIndex("java/lang/Class")
    val classSize = gIndex.getInstanceSize("java/lang/Class")

    LOGGER.info("java/lang/Class.fields: ${classFields.fields.entries.sortedBy { it.value.offset }}, total size: $classSize")

    val classData = ByteArrayOutputStream2(classSize * numClasses)
    for (i in 0 until numClasses) {
        classData.writeClass(classClassIndex)
        classData.fill(objectOverhead, classSize)
    }
    checkAlignment(classData.position)

    fillInClassNames(numClasses, classData, classSize)
    fillInClassSimpleNames(numClasses, classData, classSize)
    fillInClassIndices(numClasses, classData, classSize)
    fillInClassModifiers(numClasses, classData, classSize)

    val fieldsOffset = classFields.getOffset("fields")
    if (fieldsOffset != null) fillInFields(numClasses, classData, classSize, indexStartPtr)

    val methodsOffset = classFields.getOffset("methods")
    if (methodsOffset != null) fillInMethods(numClasses, classData, classSize, indexStartPtr)

    return appendData(printer, indexStartPtr, classData)
}

private fun fillInMethods(
    numClasses: Int, classData: ByteArrayOutputStream2,
    classSize: Int, indexStartPtr: Int,
) {
    // insert all name pointers
    val methodCache = HashMap<MethodSig, Int>()
    val emptyArrayPtr = indexStartPtr + classData.size()
    classData.writeClass(OBJECT_ARRAY)
    classData.writeLE32(0) // length

    val methodClassIndex = gIndex.getClassIndex("java/lang/reflect/Method")
    val methodSize = gIndex.getInstanceSize("java/lang/reflect/Method")

    val methodsByClass = dIndex.usedMethods
        .filter { isCallable(it) }.groupBy { it.clazz }
    val methodsForClass = ArrayList<Set<MethodSig>>(numClasses)
    for (declaringClassIndex in 0 until numClasses) {
        // find all methods with valid call signature
        val clazzName = gIndex.classNamesByIndex[declaringClassIndex]
        val superClass = hIndex.superClass[clazzName]
        val superClassIdx = if (superClass != null) gIndex.getClassIndex(superClass) else -1
        val superMethods =
            if (superClass != null) methodsForClass.getOrNull(superClassIdx)
                ?: throw IllegalStateException(
                    "Classes must be ordered for GC-Init! " +
                            "$clazzName[${declaringClassIndex}] >= $superClass[$superClassIdx]"
                )
            else emptySet()
        val superMethods1 = methodsByClass[clazzName] ?: emptyList()
        val methods = (superMethods1.map { it.withClass(clazzName) } + superMethods).toHashSet()
        methodsForClass.add(methods)
        // append all methods
        val arrayToWrite = if (methods.isNotEmpty()) {
            val methodPointers = methods.map { method ->
                assertTrue(CallSignature.c(method) in hIndex.implementedCallSignatures)
                methodCache.getOrPut(method) {
                    appendMethodInstance(
                        method, indexStartPtr, classData, declaringClassIndex,
                        methodClassIndex, classSize, methodSize
                    )
                }
            }

            val arrayPtr = indexStartPtr + classData.size()
            classData.writeClass(OBJECT_ARRAY)
            classData.writeLE32(methodPointers.size)
            for (methodPtr in methodPointers) {
                classData.writePointer(methodPtr)
            } // this is always aligned :)

            arrayPtr
        } else emptyArrayPtr

        val pos = classData.position
        classData.position = declaringClassIndex * classSize + OFFSET_CLASS_METHODS
        classData.writePointer(arrayToWrite)
        classData.position = pos
    }
}

fun isCallable(sig: MethodSig): Boolean {
    return CallSignature.c(sig) in hIndex.implementedCallSignatures
}

val parameterArrays = HashMap<List<String>, Int>()

fun appendParamsArray(
    indexStartPtr: Int, classData: ByteArrayOutputStream2,
    params: List<String>, classSize: Int,
): Int {
    val ptr = indexStartPtr + classData.position
    classData.writeClass(OBJECT_ARRAY)
    classData.writeLE32(params.size)
    for (i in params.indices) {
        val typeIndex = gIndex.getClassIndexOrParents(params[i])
        classData.writePointer(getClassInstancePtr(typeIndex, indexStartPtr, classSize))
    }
    // ensure alignment? done
    return ptr
}

private fun getReturnTypePtr(returnType: String?, indexStartPtr: Int, classSize: Int): Int {
    return if (returnType != null) {
        // return type; if unknown = not constructable, just return java/lang/Object
        // that isn't really wrong, null is java/lang/Object
        val returnTypeClass = gIndex.getClassIndexOrNull(returnType) ?: 0
        getClassInstancePtr(returnTypeClass, indexStartPtr, classSize)
    } else 0
}

fun appendMethodInstance(
    sig: MethodSig, indexStartPtr: Int, classData: ByteArrayOutputStream2,
    declaringClassIndex: Int, methodClassIndex: Int, classSize: Int, methodSize: Int
): Int {
    val namePtr = gIndex.getString(sig.name, indexStartPtr, classData) // might be new -> must be before ptr-calc
    val callSignature = CallSignature.c(sig).format()
    val callSignaturePtr = gIndex.getString(callSignature, indexStartPtr, classData)

    val dynamicIndex =
        if (false) DynIndex.getDynamicIndex(declaringClassIndex, sig, InterfaceSig(sig), -1, null)
        else -1
    // todo mark this method as used-by-reflections,
    //  and translate all used-by-reflection methods, too

    val params = sig.descriptor.params
    val parameterTypeArrayPtr = parameterArrays.getOrPut(params) {
        appendParamsArray(indexStartPtr, classData, params, classSize)
    }
    // todo prepare parameters array
    // todo this array could be cached

    val methodPtr = indexStartPtr + classData.position
    classData.writeClass(methodClassIndex)
    assertEquals(objectOverhead, OFFSET_METHOD_SLOT)
    classData.writeLE32(dynamicIndex) // slot
    classData.writePointer(namePtr) // name
    classData.writePointer(getReturnTypePtr(sig.descriptor.returnType, indexStartPtr, classSize)) // return type
    classData.writePointer(parameterTypeArrayPtr) // parameters
    classData.writePointer(callSignaturePtr) // callSignature
    classData.writePointer(getClassInstancePtr(declaringClassIndex, indexStartPtr, classSize)) // declaredClass
    val modifiers = hIndex.methodFlags[sig] ?: 0
    classData.writeLE32(modifiers) // e.g., static flag
    classData.fill(objectOverhead + 2 * intSize + 5 * ptrSize, methodSize)
    assertEquals(methodPtr + methodSize, indexStartPtr + classData.position)
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
    val elementSize = 2 * ptrSize
    val stringOverhead = objectOverhead + arrayOverhead
    val tableSizeGuess = resources.size * elementSize + intSize +
            resources.sumOf { (path, data) ->
                path.length + data.size
            } + resources.size * (arrayOverhead + stringOverhead)
    val table = ByteArrayOutputStream2(tableSizeGuess)
    table.writeLE32(resources.size)
    table.fill(0, resources.size * elementSize)
    // append names without stride for better cache locality:
    val names = resources.map { (path, _) ->
        gIndex.getString(path, ptr0, table)
    }
    for (i in resources.indices) {
        val resource = resources[i]
        val keyPtr = names[i]
        val valuePtr = ptr0 + table.position
        val value = resource.second
        table.writeClass(BYTE_ARRAY)
        table.writeLE32(value.size)
        table.write(value)
        val pos = table.position
        table.position = i * elementSize + intSize
        table.writePointer(keyPtr)
        table.writePointer(valuePtr)
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

var staticFieldOffsetsPtr = -1
var staticInitFlagsPtr = -1
var staticFieldsStartPtr = -1
var staticFieldsEndPtr = -1

fun lookupStaticVariable(className: String, offset: Int): Int {
    val offsets = gIndex.getFieldOffsets(className, true)
    assertTrue(offsets.staticOffsetPtr != -1)
    return offsets.staticOffsetPtr + offset
}

fun appendStaticInstanceTable(printer: StringBuilder2, ptr0: Int, numClasses: Int): Int {
    LOGGER.info("[appendStaticInstanceTable]")
    val debugInfo = StringBuilder2()
    staticFieldOffsetsPtr = ptr0
    staticInitFlagsPtr = ptr0 + 4 * numClasses // 4 bytes for offset to static memory
    staticFieldsStartPtr = alignPointer(staticInitFlagsPtr + numClasses) // 1 byte for flag for init
    var ptr = staticFieldsStartPtr
    val staticFieldOffsets = ByteArrayOutputStream2(numClasses * 4)
    for (i in 0 until numClasses) {
        val className = gIndex.classNamesByIndex[i]
        val fieldOffsets = gIndex.getFieldOffsets(className, true)
        val size = fieldOffsets.offset

        // todo we could be less strict, if there is only smaller-sized fields
        ptr = alignPointer(ptr)
        staticFieldOffsets.writeLE32(if (size == 0) 0 else ptr)
        fieldOffsets.staticOffsetPtr = ptr
        if (printDebug && size > 0) {
            debugInfo.append("[").append(i).append("] ")
                .append(className).append(": *").append(ptr).append("\n")
            fieldOffsets.allFields().entries.sortedBy { it.value.offset }.forEach { (name, data) ->
                debugInfo.append("  *").append(data.offset).append(": ").append(name)
                    .append(": ").append(data.type).append("\n")
            }
        }
        ptr += size
    }
    if (printDebug) {
        debugFolder.getChild("staticInstances.txt")
            .writeBytes(debugInfo.values, 0, debugInfo.size)
    }

    assertEquals(staticFieldOffsets.position, 4 * numClasses)
    appendData(printer, staticFieldOffsetsPtr, staticFieldOffsets)
    staticFieldsEndPtr = alignPointer(ptr)
    return staticFieldsEndPtr
}

val segments = ArrayList<DataSection>()

fun appendData(printer: StringBuilder2, startIndex: Int, data: ByteArrayOutputStream2): Int {
    alignBuffer(data)
    return appendData(printer, startIndex, data.toByteArray())
}

private fun checkNoOtherSectionOverlaps(startIndex: Int, dataSize: Int) {
    val segment = startIndex until (startIndex + dataSize)
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
}

fun appendData(printer: StringBuilder2, startIndex: Int, data: ByteArray): Int {

    checkAlignment(startIndex)
    checkAlignment(data.size)

    checkNoOtherSectionOverlaps(startIndex, data.size)

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

