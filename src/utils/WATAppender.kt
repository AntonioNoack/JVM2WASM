package utils

import dIndex
import dependency.ActuallyUsedIndex
import gIndex
import hIndex
import jvm.JVM32.arrayOverhead
import jvm.JVM32.objectOverhead
import me.anno.io.Streams.writeLE32
import me.anno.utils.Color
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertFalse
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

var classInstanceTablePtr = 0
fun appendClassInstanceTable(printer: StringBuilder2, indexStartPtr: Int, numClasses: Int): Int {
    LOGGER.info("[appendClassInstanceTable]")

    classInstanceTablePtr = indexStartPtr

    val fields = gIndex.getFieldOffsets("java/lang/Class", false)
    val clazzIndex = gIndex.getClassIndex("java/lang/Class")
    val classSize = fields.offset

    LOGGER.info("java/lang/Class.fields: ${fields.fields.entries.sortedBy { it.value.offset }}, total size: $classSize")

    val classData = ByteArrayOutputStream2(classSize * numClasses)
    for (i in 0 until numClasses) {
        classData.writeClass(clazzIndex)
        for (j in objectOverhead until classSize) { // nulls for the start
            classData.write(0)
        }
    }

    val nameOffset = fields.fields["name"]?.offset
    if (nameOffset != null) {
        // insert all name pointers
        for (clazz in 0 until numClasses) {
            val name = gIndex.classNames[clazz].replace('/', '.') // getName() returns name with dots
            val strPtr = gIndex.getString(name, indexStartPtr + classData.size(), classData)
            val dstPtr = clazz * classSize + nameOffset
            val pos = classData.position
            classData.position = dstPtr
            classData.writeLE32(strPtr)
            classData.position = pos
        }
    }

    val indexOffset = fields.fields["index"]?.offset
    if (indexOffset != null) {
        // insert all name pointers
        for (clazz in 0 until numClasses) {
            val dstPtr = clazz * classSize + indexOffset
            val pos = classData.position
            classData.position = dstPtr
            classData.writeLE32(clazz)
            classData.position = pos
        }
    }

    fun isNative(type: String) = when (type) {
        "I", "F", "J", "D", "Z", "B", "S", "C" -> true
        else -> false
    }

    val fieldsOffset = fields.fields["fields"]?.offset
    val fieldCache = HashMap<Triple<String, GeneratorIndex.FieldData, Int>, Int>()
    if (fieldsOffset != null) {
        val fieldOffsets0 = gIndex.getFieldOffsets("java/lang/reflect/Field", false)
        val fieldNameOffset = fieldOffsets0.fields["name"]!!.offset
        val fieldSize = fieldOffsets0.offset
        for (clazz in 0 until numClasses) {

            val className = gIndex.classNames[clazz]
            val classPtr = indexStartPtr + clazz * classSize
            // if (className !in dIndex.constructableClasses) continue

            val instanceModifier = 0
            val staticModifier = ACC_STATIC
            val instanceFields = gIndex.getFieldOffsets(className, false).fields
                .entries.map { Triple(it.key, it.value, instanceModifier + isNative(it.value.type).toInt(ACC_NATIVE)) }
            val staticFields = gIndex.getFieldOffsets(className, true).fields
                .entries.map { Triple(it.key, it.value, staticModifier + isNative(it.value.type).toInt(ACC_NATIVE)) }
            val fields2 = (instanceFields + staticFields)
                .sortedWith { a, b ->
                    val na = !isNative(a.second.type)
                    val nb = !isNative(b.second.type)
                    na.compareTo(nb).ifSame {
                        a.first.compareTo(b.first)
                    }
                }

            if (fields2.isEmpty()) {
                // just let it be null
                continue
            }

            val fieldPointers = IntArray(fields2.size) {
                val field = fields2[it]
                fieldCache.getOrPut(field) {
                    // create new field instance
                    val name = gIndex.getString(field.first, indexStartPtr + classData.size(), classData)
                    val fieldPtr = indexStartPtr + classData.size()
                    classData.writeClass(16)
                    for (i in objectOverhead until fieldNameOffset) {
                        classData.write(0)
                    }
                    classData.writeLE32(name) // name
                    classData.writeLE32(field.second.offset) // slot
                    val ci = when (val typeName = field.second.type) {
                        "I" -> gIndex.getClassIndex("int")
                        "F" -> gIndex.getClassIndex("float")
                        "Z" -> gIndex.getClassIndex("boolean")
                        "B" -> gIndex.getClassIndex("byte")
                        "S" -> gIndex.getClassIndex("short")
                        "C" -> gIndex.getClassIndex("char")
                        "J" -> gIndex.getClassIndex("long")
                        "D" -> gIndex.getClassIndex("double")
                        "[]" -> gIndex.getClassIndex("[]")
                        else -> {
                            if (typeName.startsWith("L")) {
                                gIndex.getClassIndexOrParents(typeName.substring(1))
                            } else if (typeName.startsWith("[L") || typeName.startsWith("[[")) {
                                gIndex.getClassIndex("[]")
                            } else if (typeName.startsWith("[") && typeName.length == 2) {
                                gIndex.getClassIndex(typeName)
                            } else {
                                throw IllegalStateException(typeName)
                            }
                        }
                    }
                    classData.writeLE32(indexStartPtr + classSize * ci) // type
                    classData.writeLE32(field.third) // modifiers
                    classData.writeLE32(classPtr) // clazz -> declaring class
                    for (i in 20 + fieldNameOffset until fieldSize) {// 20, because we wrote 5x4 bytes
                        classData.write(0)
                    }
                    if (fieldPtr + fieldSize != indexStartPtr + classData.size())
                        throw IllegalStateException()
                    fieldPtr
                }
            }

            if (clazz < 10) {
                LOGGER.info(
                    "Fields for $clazz [${indexStartPtr + clazz * classSize}]: $className -> $fields2, " +
                            fields2.joinToString { "${gIndex.getString(it.first)}" })
            } else if (clazz == 10 && numClasses > 11) LOGGER.info("...")

            if (clazz > 0 && gIndex
                    .getFieldOffsets(hIndex.superClass[className]!!, false)
                    .fields.any { it.key !in fields2.map { f2 -> f2.first } }
            ) throw IllegalStateException(
                "Fields from super class are missing in $className, " +
                        "super: ${hIndex.superClass[className]!!}, " +
                        "${
                            gIndex
                                .getFieldOffsets(hIndex.superClass[className]!!, false)
                                .fields.filter { it.key !in fields2.map { f2 -> f2.first } }
                        }"
            )

            // create new fields array
            val arrayPtr = indexStartPtr + classData.size()
            classData.writeClass(1)
            classData.writeLE32(fields2.size) // array length

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

    val methodsOffset = fields.fields["methods"]?.offset
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

val nameToMethod
    get() = hIndex.methods.map { it.value }.flatten()
        .associateBy { methodName(it) }

val dynIndex = HashMap<String, Pair<MethodSig, Int>>()
val dynIndexSig = MethodSig.c("", "dynIndex", "()V")

val functionTable = ArrayList<String>()

fun appendDynamicFunctionTable(
    printer: StringBuilder2,
    implementedMethods: Map<String, MethodSig>
) {
    val nameToMethod = nameToMethod
    val dynamicFunctions = implementedMethods
        .entries
        .filter { (_, sig) -> // saving space by remove functions that cannot be invoked dynamically
            sig.clazz != "?" &&
                    sig.name != "<init>" &&
                    sig.name != "<clinit>" &&
                    sig.name !in dynIndex &&
                    sig !in hIndex.staticMethods &&
                    sig !in hIndex.finalMethods &&
                    sig !in hIndex.abstractMethods &&
                    hIndex.getAlias(sig) == sig
        }
        .sortedBy { it.value.name + "/" + it.value.descriptor }
    for ((name, sig) in dynamicFunctions) {
        if (nameToMethod[name] in hIndex.abstractMethods)
            throw IllegalStateException("$name is abstract, but also listed")
        if (name !in dynIndex) {
            dynIndex[name] = sig to dynIndex.size
        }
    }
    printer.append("(table ${dynIndex.size} funcref)\n")
    printer.append("(elem (i32.const 0)\n")
    functionTable.ensureCapacity(dynIndex.size)
    var i = 0
    for ((name, idx) in dynIndex
        .entries.sortedBy { it.value.second }) {
        assertEquals(i++, idx.second)
        var name2 = name
        // resolve by aliases
        while (true) {
            val sig = hIndex.getAlias(name2) ?: break
            val name3 = methodName(sig)
            if (name2 == name3) {
                assertTrue(name2 in implementedMethods) {
                    printUsed(sig)
                    "Missing impl of $name2/$sig"
                }
                break
            }
            name2 = name3
        }

        assertFalse(nameToMethod[name2] in hIndex.abstractMethods) {
            "$name is abstract, but also listed"
        }

        val sig = nameToMethod[name2] ?: idx.first
        assertFalse(sig in hIndex.abstractMethods) { "$name2 is abstract, but also listed" }
        printer.append("  $").append(name2).append('\n')
        functionTable.add(name2)
        ActuallyUsedIndex.add(dynIndexSig, sig)
    }
    printer.append(")\n")
    LOGGER.info("Filtered ${dynamicFunctions.size} dynamic functions from ${implementedMethods.size} methods")
}

var printDebug = true

/**
 *
// super class
// size
// #interfaces
// ...
// #interfaceFunctions
// ...
 * */
fun appendInheritanceTable(printer: StringBuilder2, ptr0: Int, numClasses: Int): Int {
    LOGGER.info("[appendInheritanceTable]")
    val debugInfo = StringBuilder2(1024)
    // done append custom functions
    // append class instanceOf-table
    val classTableData = ByteArrayOutputStream2(numClasses * 4)
    val instTable = ByteArrayOutputStream2()
    var ptr = ptr0 + numClasses * 4
    val staticInitIdx = gIndex.getInterfaceIndex(InterfaceSig.c("<clinit>", "()V"))

    assertEquals(objectOverhead + 8, gIndex.getFieldOffsets("java/lang/String", false).offset)
    for (classId in 0 until numClasses) {
        if (classId == 0 || classId in 17 until 25) {
            // write 0 :), no table space used
            classTableData.writeLE32(0)
        } else {

            val clazz = gIndex.classNames[classId]
            val superClass = hIndex.superClass[clazz]
                ?: throw NullPointerException("Super class of $clazz ($classId) is unknown")

            // filter for existing interfaces :)
            val interfaces = HashSet<String>()
            fun addI(clazz: String) {
                val classInterfaces = hIndex.interfaces[clazz] ?: emptyList()
                interfaces.addAll(classInterfaces)
                for (interfaceI in classInterfaces) {
                    addI(interfaceI)
                }
                addI(hIndex.superClass[clazz] ?: return)
            }
            addI(clazz)

            interfaces.removeIf {
                if (gIndex.getClassIndex(it) >= numClasses) {
                    LOGGER.warn("$it got index too late (interface)")
                    true
                } else false
            }

            classTableData.writeLE32(ptr)

            // super
            // size
            // #interfaces
            // ...
            // #functions

            instTable.writeLE32(gIndex.getClassIndex(superClass))
            val fieldOffsets = gIndex.getFieldOffsets(clazz, false)
            val clazzSize = fieldOffsets.offset
            instTable.writeLE32(clazzSize)
            instTable.writeLE32(interfaces.size)
            for (j in interfaces) {
                instTable.writeLE32(gIndex.getClassIndex(j))
            }
            ptr += interfaces.size * 4 + 12

            if (printDebug) {
                debugInfo.append("[").append(classId).append("]: ").append(clazz).append("\n")
                debugInfo.append("  extends ").append(superClass).append("\n")
                for (interface1 in interfaces) {
                    debugInfo.append("  implements ").append(interface1).append("\n")
                }
                debugInfo.append("  fields[total: ").append(clazzSize).append("]:\n")
                fieldOffsets.fields.entries.sortedBy { it.value.offset }.forEach { (name, data) ->
                    debugInfo.append("    *").append(data.offset).append(": ").append(name)
                        .append(": ").append(data.type).append("\n")
                }
            }

            val print =
                clazz == "me_anno_utils_pooling_Stack_storageXlambdav0_Lme_anno_utils_pooling_StackLme_anno_utils_pooling_StackXLocalStack"
            if (print) println("[$clazz]: $superClass, $interfaces")

            // here is space for a name and maybe more debug information :)
            // append call_dynamic data
            // look up interface functions...
            // and only implement those, that are actually available

            // these functions only need to be available, if the class is considered constructable

            if (clazz in dIndex.constructableClasses &&
                !hIndex.isAbstractClass(clazz) &&
                !hIndex.isInterfaceClass(clazz)
            ) {

                if (printDebug) {
                    debugInfo.append("  constructable & !abstract & !interface\n")
                }

                val implFunctions0 = HashMap<Int, MethodSig>()
                for (sig in dIndex.usedInterfaceCalls) {
                    // only if is actually instance of interface
                    if (sig.clazz in interfaces) {
                        val impl = findMethod(clazz, sig)
                        if (impl == null) {
                            if (print) println("[$clazz] $sig -> null")
                            continue
                        }
                        if (impl in hIndex.abstractMethods) {
                            if (print) println("[$clazz] $sig -> abstract $impl")
                            continue
                        }
                        if (print) println("[$clazz] $sig -> $impl")
                        // printUsed(impl)
                        if (genericsTypes(sig) != genericsTypes(impl)) {
                            println()
                            println("---")
                            printUsed(sig)
                            printUsed(impl)
                            println(methodName(sig))
                            println(methodName(impl))
                            throw IllegalStateException(
                                "$sig cannot be linked to $impl, " +
                                        "because ${genericsTypes(sig)} != ${genericsTypes(impl)}"
                            )
                        }
                        implFunctions0[gIndex.getInterfaceIndex(InterfaceSig(sig))] = impl
                        val name = methodName(impl)
                        if (name !in dynIndex) dynIndex[name] = impl to dynIndex.size
                    }
                }

                if (hIndex.isEnumClass(clazz)) {
                    val impl = findMethod(clazz, "<clinit>", "()V")
                    implFunctions0[staticInitIdx] = impl!!
                    val name = methodName(impl)
                    if (name !in dynIndex) dynIndex[name] = impl to dynIndex.size
                }

                if (print) {
                    println("other functions:")
                    for (sig in hIndex.methods[clazz] ?: emptySet()) {
                        if (sig !in implFunctions0.values) {
                            print("  ")
                            printUsed(sig)
                        }
                    }
                }

                val implFunctions = implFunctions0
                    .entries.sortedBy { it.key } // sorted by id
                instTable.writeLE32(implFunctions.size)
                for ((id, sig) in implFunctions) {
                    instTable.writeLE32(id)
                    instTable.writeLE32(dynIndex[methodName(sig)]!!.second)
                }
                ptr += implFunctions.size * 8 + 4

                if (printDebug) {
                    for ((id, sig) in implFunctions) {
                        debugInfo.append("  method[").append(id).append("]: ").append(sig).append("\n")
                    }
                }

                if (print) println("implemented $implFunctions")

            } else {
                instTable.writeLE32(0)
                ptr += 4
                if (print) println("implemented nothing")
            }

            if (print) {
                println(gIndex.interfaceIndex.entries.filter { it.value == 552 })
                // this must exist and must be used!
                printUsed(
                    MethodSig.c(
                        "java_lang_System_getProperty_Ljava_lang_StringLjava_lang_String",
                        "apply", "(Ljava/lang/Object;)Ljava/lang/Object;"
                    )
                )
            }

            // if (print) throw IllegalStateException()
        }
    }

    if (printDebug) {
        debugFolder.getChild("inheritanceTable.txt")
            .writeBytes(debugInfo.values, 0, debugInfo.size)
    }

    val ptr2 = appendData(printer, ptr0, classTableData, instTable)
    assertEquals(ptr, ptr2)
    return ptr
}

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
        val className = gIndex.classNames[i]
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
                fieldOffsets.fields.entries.sortedBy { it.value.offset }.forEach { (name, data) ->
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

var methodTablePtr = 0
var aidtCtr = 50 // disabled
fun appendInvokeDynamicTable(printer: StringBuilder2, ptr0: Int, numClasses: Int): Int {
    LOGGER.info("[appendInvokeDynamicTable]")
    val debugInfo = StringBuilder2()

    methodTablePtr = ptr0

    val methodTable = ByteArrayOutputStream2(numClasses * 4)
    val table2 = ByteArrayOutputStream2(numClasses * 4)
    var ptr = ptr0 + numClasses * 4
    var numOk = 0
    var numBroken = 0
    var numAbstract = 0
    var numFixed = 0

    // create method table (for inheritance), #resolveIndirect
    fun getDynMethodIdx(clazz: Int): Map<InterfaceSig, Int> {
        val pic = gIndex.dynMethodIndices[clazz]
        if (pic != null) return pic
        if (clazz == 0) throw IllegalStateException("java/lang/Object must have dynamic function table!")
        return getDynMethodIdx(gIndex.getClassIndex(hIndex.superClass[gIndex.classNames[clazz]]!!))
    }

    // printUsed(MethodSig.c("java/lang/Object", "hashCode", "()I"))
    // printUsed(MethodSig.c("jvm/JavaLang", "Object_hashCode", "(Ljava/lang/Object;)I"))
    // printUsed(MethodSig.c("java/util/HashSet", "add", "(Ljava/lang/Object;)Z"))

    /*var clazz = 39
    while (true) {
        printUsed(MethodSig.c(gIndex.classNames[clazz], "hashCode", "()I"))
        clazz = gIndex.getClassIndex(hIndex.superClass[gIndex.classNames[clazz]] ?: break)
    }*/

    for (i in 0 until numClasses) {
        val dynMethods = getDynMethodIdx(i)
        val clazz = gIndex.classNames[i]

        val print = i == 1929
        // could be written to a file for debugging
        if (print) println("  dynMethodIndex[$i: $clazz]: $dynMethods")

        if (gIndex.classNames[i] !in dIndex.constructableClasses) {
            if (print) println("  writing $i: $clazz to null, because not constructable")
            methodTable.writeLE32(0)
            if (printDebug) {
                debugInfo.append("[").append(i).append("]: ").append(clazz).append(" not constructable\n")
            }
        } else {
            if (printDebug) {
                debugInfo.append("[").append(i).append("]: ").append(clazz).append("\n")
            }
            methodTable.writeLE32(ptr)
            val dynIndexToMethod = arrayOfNulls<InterfaceSig>(dynMethods.size)
            table2.writeLE32(dynMethods.size * 4)
            for ((m, idx) in dynMethods) {
                if (dynIndexToMethod[idx] != null) throw IllegalStateException("Index must not appear twice in pic! $dynMethods")
                dynIndexToMethod[idx] = m
            }
            // val print = i == 39
            if (print || aidtCtr < 50) println("  writing $i: $clazz to $ptr, ${dynIndexToMethod.toList()}")
            for (idx in dynIndexToMethod.indices) {

                val sig0 = dynIndexToMethod[idx]!!
                val sig = MethodSig.c(clazz, sig0.name, sig0.descriptor)

                fun methodIsAbstract(sig: MethodSig): Boolean {
                    if (sig in hIndex.abstractMethods) return true
                    if (sig.clazz == "java/lang/Object") return false
                    if (sig in hIndex.jvmImplementedMethods) return false
                    val superClass = hIndex.superClass[sig.clazz] ?: throw NullPointerException(sig.clazz)
                    return methodIsAbstract(sig.withClass(superClass))
                }

                val impl = findMethod(clazz, sig) ?: sig
                // if method is missing, find replacement
                val mapped = hIndex.getAlias(impl)
                val name = methodName(mapped)
                if (print) println("  $idx, $sig0 -> $sig, $impl, $mapped")
                val dynIndexI = dynIndex[name]
                if (dynIndexI != null) {
                    numOk++
                    table2.writeLE32(dynIndexI.second)
                    if (print || aidtCtr++ < 50) println("  $idx -> $dynIndexI")
                    if (printDebug) {
                        debugInfo.append("  ").append(idx).append(": ")
                            .append(dynIndexI.second).append(" // ").append(mapped).append("\n")
                    }
                } else if (methodIsAbstract(mapped)) {
                    numAbstract++
                    // to do redirect to an error function or to -1; don't warn then
                    table2.writeLE32(-1)
                    if (i == 14 && sig.name == "get") {
                        printUsed(sig)
                        if (mapped != sig) printUsed(mapped)
                    }
                    if (print || aidtCtr++ < 50) println("    $idx -> -1") // , available:
                    if (printDebug) {
                        debugInfo.append("  ").append(idx).append(": ")
                            .append(sig0).append(" -> -1 // ").append(mapped).append("\n")
                    }
                } else {
                    if (mapped in dIndex.usedMethods) {
                        numFixed++
                        if (mapped in hIndex.abstractMethods) {
                            printUsed(mapped)
                            throw IllegalStateException("$name, $mapped is abstract, but also listed")
                        }
                        val dynIndexJ = dynIndex.size
                        dynIndex[name] = mapped to dynIndexJ
                        table2.writeLE32(dynIndexJ)
                        if (print || aidtCtr++ < 50) println("    $idx -> $dynIndexJ*")
                        if (printDebug) {
                            debugInfo.append("  ").append(idx).append(": ")
                                .append(dynIndexJ).append("* // ").append(mapped).append("\n")
                        }
                    } else {
                        numBroken++
                        table2.writeLE32(-1)
                        if (printDebug) {
                            debugInfo.append("  ").append(idx).append(": ")
                                .append(sig0).append(" -> -1X // ").append(mapped).append("\n")
                        }
                        if (true) {

                            LOGGER.warn("$sig ($i/$idx) is missing from dynIndex")
                            printUsed(sig)
                            LOGGER.warn("    $idx -> -1*")

                            if (false) {
                                // to do check if any super class or interface is being used...
                                fun checkChildren(clazz: String) {
                                    if (sig.withClass(clazz) in dIndex.usedMethods) {
                                        printUsed(sig)
                                        throw IllegalStateException("$sig is being used by super class $clazz")
                                    }
                                    for (child in hIndex.childClasses[clazz] ?: return) {
                                        checkChildren(child)
                                    }
                                }

                                fun checkSuper(clazz: String) {
                                    if (clazz == "java/lang/Object") return
                                    if (sig.withClass(clazz) in dIndex.usedMethods) {
                                        printUsed(sig)
                                        throw IllegalStateException("$sig is being used by super class $clazz")
                                    }
                                    checkSuper(hIndex.superClass[clazz] ?: return)
                                }

                                checkSuper(sig.clazz)
                                checkChildren(sig.clazz)
                            }
                        }
                    }
                }
            }
            ptr += 4 + 4 * dynIndexToMethod.size
            // if (print) throw IllegalStateException("debug")
        }
    }
    LOGGER.info("  dynamic table, ok: $numOk, abstract: $numAbstract, broken: $numBroken, fixed: $numFixed, index-size: ${dynIndex.size}")
    appendData(printer, ptr0, methodTable)
    appendData(printer, ptr0 + numClasses * 4, table2)
    if (printDebug) {
        debugFolder.getChild("inheritanceTable1.txt")
            .writeBytes(debugInfo.values, 0, debugInfo.size)
    }
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

