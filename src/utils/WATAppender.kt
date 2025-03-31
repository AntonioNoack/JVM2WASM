package utils

import dIndex
import gIndex
import hIndex
import implementedMethods
import jvm.JVM32.*
import jvm.JVMShared.intSize
import me.anno.io.Streams.writeLE32
import me.anno.io.Streams.writeLE64
import me.anno.utils.Color
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertTrue
import me.anno.utils.types.Booleans.toInt
import org.apache.logging.log4j.LogManager
import org.objectweb.asm.Opcodes.*
import resources
import translator.GeneratorIndex
import translator.GeneratorIndex.alignBuffer
import translator.GeneratorIndex.alignPointer
import translator.GeneratorIndex.checkAlignment
import translator.GeneratorIndex.stringStart
import translator.MethodTranslator
import utils.Annotations.appendAnnotations
import utils.MethodResolver.resolveMethod
import utils.StaticClassIndices.BYTE_ARRAY
import utils.StaticClassIndices.OBJECT_ARRAY
import utils.StaticFieldOffsets.*
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

data class FieldEntry(val field: GeneratorIndex.FieldData, val sig: FieldSig, val modifiers: Int) {
    val name get() = sig.name
}

/**
 * returns whether a type is native;
 * all objects and arrays are not native, and therefore need garbage collection;
 * all integers, chars and floats are native
 * */
fun isNativeType(type: String) = when (type) {
    "I", "F", "J", "D", "Z", "B", "S", "C" -> throw IllegalArgumentException()
    else -> type in NativeTypes.nativeTypes
}

fun ByteArrayOutputStream2.writePointerAt(ptr: Int, at: Int) {
    val pos = position
    position = at
    writePointer(ptr)
    position = pos
}

fun ByteArrayOutputStream2.writeLE32At(value: Int, at: Int) {
    val pos = position
    position = at
    writeLE32(value)
    position = pos
}

fun ByteArrayOutputStream2.writeLE64At(value: Long, at: Int) {
    val pos = position
    position = at
    writeLE64(value)
    position = pos
}

fun ByteArrayOutputStream2.writeLE32At(value: Float, at: Int) {
    val pos = position
    position = at
    writeLE32(value)
    position = pos
}

fun ByteArrayOutputStream2.writeLE64At(value: Double, at: Int) {
    val pos = position
    position = at
    writeLE64(value)
    position = pos
}

private fun fillInClassNames(numClasses: Int, classData: ByteArrayOutputStream2, classSize: Int) {
    for (clazz in 0 until numClasses) {
        val name = gIndex.classNamesByIndex[clazz].replace('/', '.') // getName() returns name with dots
        val strPtr = gIndex.getString(name, classInstanceTablePtr, classData)
        classData.writePointerAt(strPtr, clazz * classSize + OFFSET_CLASS_NAME)
    }
}

private fun fillInClassSimpleNames(numClasses: Int, classData: ByteArrayOutputStream2, classSize: Int) {
    for (clazz in 0 until numClasses) {
        val fullName = gIndex.classNamesByIndex[clazz]
        val simpleName = fullName.split('/', '.').last()
        val strPtr = gIndex.getString(simpleName, classInstanceTablePtr, classData)
        classData.writePointerAt(strPtr, clazz * classSize + OFFSET_CLASS_SIMPLE_NAME)
    }
}

private fun fillInClassIndices(numClasses: Int, classData: ByteArrayOutputStream2, classSize: Int) {
    for (classIndex in 0 until numClasses) {
        classData.writeLE32At(classIndex, classIndex * classSize + OFFSET_CLASS_INDEX)
    }
}

private fun fillInClassModifiers(numClasses: Int, classData: ByteArrayOutputStream2, classSize: Int) {
    for (clazz in 0 until numClasses) {
        val className = gIndex.classNamesByIndex[clazz]
        val modifiers = hIndex.classFlags[className] ?: 0
        classData.writeLE32At(modifiers, clazz * classSize + OFFSET_CLASS_MODIFIERS)
    }
}

private fun fillInFields(
    numClasses: Int, classData: ByteArrayOutputStream2,
    classSize: Int, indexStartPtr: Int,
    emptyArrayPtr: Int
) {

    checkAlignment(indexStartPtr)

    val fieldClassIndex = gIndex.getClassIndex("java/lang/reflect/Field")
    val fieldSize = gIndex.getInstanceSize("java/lang/reflect/Field")
    val fieldCache = HashMap<FieldEntry, Int>()
    for (classIndex in 0 until numClasses) {

        val className = gIndex.classNamesByIndex[classIndex]

        val instanceFields0 = gIndex.getFieldOffsets(className, false).fields
        val staticFields0 = gIndex.getFieldOffsets(className, true).fields

        val dstPointer = classIndex * classSize + OFFSET_CLASS_FIELDS
        if (instanceFields0.isEmpty() && staticFields0.isEmpty()) {
            classData.writePointerAt(emptyArrayPtr, dstPointer)
            continue
        }

        val instanceFields = instanceFields0.map { (name, field) ->
            val sig = FieldSig(className, name, field.type, false)
            val modifiers = isNativeType(field.type).toInt(ACC_NATIVE)
            FieldEntry(field, sig, modifiers)
        }

        val staticFields = staticFields0.map { (name, field) ->
            val sig = FieldSig(className, name, field.type, true)
            val modifiers = ACC_STATIC + isNativeType(field.type).toInt(ACC_NATIVE)
            FieldEntry(field, sig, modifiers)
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

        classData.writePointerAt(arrayPtr, dstPointer)

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

fun getClassInstancePtr(classIndex: Int, indexStartPtr: Int, classSize: Int): Int {
    return indexStartPtr + classIndex * classSize
}

private fun appendFieldInstance(
    field: FieldEntry, indexStartPtr: Int, classData: ByteArrayOutputStream2,
    declaringClassIndex: Int, fieldClassIndex: Int,
    classSize: Int, fieldSize: Int
): Int {

    // create new field instance
    // name must be before fieldPtr, because the name might be new!!
    val namePtr = gIndex.getString(field.name, indexStartPtr, classData)
    val annotations = (hIndex.fieldAnnotations[field.sig] ?: emptyList())
        .filter { it.implClass in dIndex.constructableClasses }
    val annotationsPtr = appendAnnotations(annotations, indexStartPtr, classData)

    alignBuffer(classData)
    val fieldPtr = indexStartPtr + classData.size()
    classData.writeClass(fieldClassIndex)
    classData.writeLE32(field.field.offset) // slot
    classData.writePointer(namePtr) // name
    val typeClassIndex = getTypeClassIndex(field.field.type)
    classData.writePointer(getClassInstancePtr(typeClassIndex, indexStartPtr, classSize)) // type
    classData.writePointer(getClassInstancePtr(declaringClassIndex, indexStartPtr, classSize)) // declaring class
    classData.writePointer(annotationsPtr)
    val allowedExtraFlags = ACC_PRIVATE
    val extraFlags = (hIndex.fieldFlags[field.sig] ?: 0) and allowedExtraFlags
    classData.writeLE32(field.modifiers or extraFlags) // modifiers
    classData.fill(objectOverhead + 2 * intSize + 4 * ptrSize, fieldSize)
    assertEquals(fieldPtr + fieldSize, indexStartPtr + classData.size())
    checkAlignment(classData.position)
    return fieldPtr
}

var classInstanceTablePtr = 0
fun appendClassInstanceTable(printer: StringBuilder2, ptr: Int, numClasses: Int): Int {
    LOGGER.info("[appendClassInstanceTable]")

    val indexStartPtr = alignPointer(ptr)
    classInstanceTablePtr = indexStartPtr

    val classClassIndex = gIndex.getClassIndex("java/lang/Class")
    val classSize = gIndex.getInstanceSize("java/lang/Class")

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

    alignBuffer(classData, 4)
    val emptyArrayPtr = indexStartPtr + classData.size()
    classData.writeClass(OBJECT_ARRAY)
    classData.writeLE32(0) // length

    Annotations.defineEmptyArray(emptyArrayPtr)

    fillInFields(numClasses, classData, classSize, indexStartPtr, emptyArrayPtr)
    fillInMethods(numClasses, classData, classSize, indexStartPtr, false, emptyArrayPtr)
    fillInMethods(numClasses, classData, classSize, indexStartPtr, true, emptyArrayPtr)

    return appendData(printer, indexStartPtr, classData)
}

private fun hasBeenImplemented(sig: MethodSig): Boolean {
    val resolved = resolveMethod(sig, true) ?: return false
    return methodName(resolved) in implementedMethods
}

private fun fillInMethods(
    numClasses: Int, classData: ByteArrayOutputStream2,
    classSize: Int, indexStartPtr: Int,
    writeConstructors: Boolean,
    emptyArrayPtr: Int
) {

    // insert all name pointers
    val methodCache = HashMap<MethodSig, Int>()

    val className = if (writeConstructors) "java/lang/reflect/Constructor" else "java/lang/reflect/Method"
    val methodClassIndex = gIndex.getClassIndex(className)
    val methodSize = gIndex.getInstanceSize(className)

    fun isConstructableOrStatic(sig: MethodSig): Boolean {
        return sig.clazz in dIndex.constructableClasses || hIndex.isStatic(sig)
    }

    fun isInitAsExpected(sig: MethodSig): Boolean {
        return ((sig.name == INSTANCE_INIT) == writeConstructors)
    }

    val methodsByClass =
        dIndex.usedMethods.filter { sig ->
            hasBeenImplemented(sig) &&
                    isConstructableOrStatic(sig) &&
                    isInitAsExpected(sig) &&
                    isCallable(sig)
        }.groupBy { it.clazz }

    val methodsForClass = ArrayList<Collection<MethodSig>>(numClasses)
    val offsetClassMethods = if (writeConstructors) OFFSET_CLASS_CONSTRUCTORS else OFFSET_CLASS_METHODS
    for (classId in 0 until numClasses) {
        // find all methods with valid call signature
        val clazzName = gIndex.classNamesByIndex[classId]
        val superClass = hIndex.superClass[clazzName]
        val superClassIdx = if (superClass != null) gIndex.getClassIndex(superClass) else -1
        val superMethods =
            if (superClass != null) methodsForClass.getOrNull(superClassIdx)
                ?: throw IllegalStateException(
                    "Classes must be ordered for GC-Init! " +
                            "$clazzName[${classId}] >= $superClass[$superClassIdx]"
                )
            else emptySet()

        val isConstructable = clazzName in dIndex.constructableClasses
        val methods = if (writeConstructors) {
            if (isConstructable) {
                val selfMethods1 = methodsByClass[clazzName] ?: emptyList()
                selfMethods1.filter { hasBeenImplemented(it) } // filter by implemented methods
            } else emptyList()
        } else {
            val selfMethods1 = methodsByClass[clazzName] ?: emptyList()
            (selfMethods1 + superMethods.map {
                // withClass is only needed, if !static
                if (hIndex.isStatic(it)) it
                else it.withClass(clazzName)
            }).distinct() // remove duplicates
        }
        methodsForClass.add(methods)

        // append all methods
        val arrayToWrite = if (methods.isNotEmpty()) {
            val methodPointers = methods.map { method ->
                val callSignature = CallSignature.c(method)
                assertTrue(callSignature in hIndex.implementedCallSignatures) {
                    "Missing call signature for $method: $callSignature"
                }
                methodCache.getOrPut(method) {
                    appendMethodInstance(
                        method, indexStartPtr, classData, classId,
                        methodClassIndex, classSize, methodSize,
                        writeConstructors
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

        val dstPointer = classId * classSize + offsetClassMethods
        classData.writePointerAt(arrayToWrite, dstPointer)
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

private fun getReturnTypePtr(returnType0: String?, indexStartPtr: Int, classSize: Int): Int {
    val returnType = returnType0 ?: "void"
    // return type; if unknown = not constructable, just return java/lang/Object
    // that isn't really wrong, null is java/lang/Object
    val returnTypeClass = gIndex.getClassIndexOrNull(returnType) ?: 0
    return getClassInstancePtr(returnTypeClass, indexStartPtr, classSize)
}

fun appendMethodInstance(
    sig: MethodSig, indexStartPtr: Int, classData: ByteArrayOutputStream2,
    declaringClassIndex: Int, methodClassIndex: Int, classSize: Int, methodSize: Int,
    writeConstructors: Boolean
): Int {

    val namePtr = gIndex.getString(sig.name, indexStartPtr, classData) // might be new -> must be before ptr-calc
    val callSignature = CallSignature.c(sig).format()
    val callSignaturePtr = gIndex.getString(callSignature, indexStartPtr, classData)

    val dynamicIndex = DynIndex.getDynamicIndex(declaringClassIndex, sig, InterfaceSig(sig), -1, null)

    val params = sig.descriptor.params
    val parameterTypeArrayPtr = parameterArrays.getOrPut(params) {
        appendParamsArray(indexStartPtr, classData, params, classSize)
    }

    val methodPtr = indexStartPtr + classData.position
    classData.writeClass(methodClassIndex)
    assertEquals(objectOverhead, OFFSET_METHOD_SLOT)
    classData.writeLE32(dynamicIndex) // slot
    if (!writeConstructors) {
        classData.writePointer(namePtr) // name
        classData.writePointer(getReturnTypePtr(sig.descriptor.returnType, indexStartPtr, classSize)) // return type
    } // else constructors neither have name nor return type
    classData.writePointer(parameterTypeArrayPtr) // parameters
    classData.writePointer(callSignaturePtr) // callSignature
    classData.writePointer(getClassInstancePtr(declaringClassIndex, indexStartPtr, classSize)) // declaredClass
    val modifiers = hIndex.methodFlags[sig] ?: 0
    classData.writeLE32(modifiers) // e.g., static flag
    classData.fill(objectOverhead + 2 * intSize + (if (writeConstructors) 3 else 5) * ptrSize, methodSize)
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

val dataSections = ArrayList<DataSection>()

fun appendData(printer: StringBuilder2, startIndex: Int, data: ByteArrayOutputStream2): Int {
    alignBuffer(data)
    return appendData(printer, startIndex, data.toByteArray())
}

private fun checkNoOtherSectionOverlaps(startIndex: Int, dataSize: Int) {
    val segment = startIndex until (startIndex + dataSize)
    val mid1 = segment.first + segment.last
    val length1 = segment.last - segment.first
    for (seg in dataSections) {
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

    dataSections.add(DataSection(startIndex, data))
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

