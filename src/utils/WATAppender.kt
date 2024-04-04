package utils

import dIndex
import gIndex
import hIndex
import jvm.JVM32.arrayOverhead
import jvm.JVM32.objectOverhead
import me.anno.io.Streams.writeLE32
import me.anno.maths.Maths.hasFlag
import me.anno.utils.Color
import me.anno.utils.structures.Compare.ifSame
import me.anno.utils.types.Booleans.toInt
import org.objectweb.asm.Opcodes.*
import resources
import translator.GeneratorIndex
import translator.GeneratorIndex.stringStart
import translator.MethodTranslator
import java.io.OutputStream
import kotlin.math.abs

fun OutputStream.writeClass(clazz: Int) {
    if (clazz < 0 || clazz > 0xffffff) throw IllegalArgumentException()
    writeLE32(clazz) // class
    for (i in 4 until objectOverhead) write(0)
}

var classInstanceTablePtr = 0
fun appendClassInstanceTable(printer: StringBuilder2, indexStartPtr: Int, numClasses: Int): Int {

    classInstanceTablePtr = indexStartPtr

    val fields = gIndex.getFieldOffsets("java/lang/Class", false)
    val clazzIndex = gIndex.getClassIndex("java/lang/Class")
    val classSize = fields.offset

    println("class fields: ${fields.fields.entries.sortedBy { it.value.offset }}, total size: $classSize")

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

            if (clazz < 10) println("fields for $clazz [${indexStartPtr + clazz * classSize}]: $className -> $fields2, " +
                    fields2.joinToString { "${gIndex.getString(it.first)}" })
            else if (clazz == 10 && numClasses > 11) println("...")

            if (clazz > 0 && gIndex
                    .getFieldOffsets(hIndex.superClass[className]!!, false)
                    .fields.any { it.key !in fields2.map { f2 -> f2.first } }
            ) throw IllegalStateException("Fields from super class are missing in $className, " +
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
    throwableLookupPtr = ptr0
    return appendData(printer, ptr0, MethodTranslator.callTable)
}

var resourceTablePtr = 0
fun appendResourceTable(printer: StringBuilder2, ptr0: Int): Int {
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
    for (type in gIndex.types.toSortedSet()) {
        printer.append("(type ").append(type).append(" (func (param")
        // define all params and result types
        if (type.startsWith("\$f")) printer.append(' ').append(ptrType) // instance
        else if (!type.startsWith("\$s")) throw IllegalStateException()
        loop@ for (j in 2 until type.length) {// parameters
            when (type[j]) {
                'A', 'V' -> {}
                '0' -> printer.append(' ').append(i32)
                '1' -> printer.append(' ').append(i64)
                '2' -> printer.append(' ').append(f32)
                '3' -> printer.append(' ').append(f64)
                'R' -> printer.append(") (result")
                else -> throw NotImplementedError("$type -> ${type[j]}")
            }
        }
        printer.append(")))\n")
    }
}

val nameToMethod
    get() = hIndex.methods.map { it.value }.flatten()
        .associateBy { methodName(it) }

val dynIndex = HashMap<String, Int>()
val dynIndexSig = MethodSig.c("", "dynIndex", "")
fun appendDynamicFunctionTable(
    printer: StringBuilder2,
    implementedMethods: Map<String, MethodSig>
) {
    val nameToMethod = nameToMethod
    val dynamicFunctions = implementedMethods
        .entries
        .filter { (_, it) -> // saving space by remove functions that cannot be invoked dynamically
            it.clazz != "?" &&
                    it.name != "<init>" &&
                    it.name != "<clinit>" &&
                    it.name !in dynIndex &&
                    it !in hIndex.staticMethods &&
                    it !in hIndex.finalMethods &&
                    it !in hIndex.abstractMethods &&
                    methodName(it) !in hIndex.methodAliases
        }
        .sortedBy { it.value.name + "/" + it.value.descriptor }
    for ((name, _) in dynamicFunctions) {
        if (nameToMethod[name] in hIndex.abstractMethods)
            throw IllegalStateException("$name is abstract, but also listed")
        if (name !in dynIndex)
            dynIndex[name] = dynIndex.size
    }
    printer.append("(table ${dynIndex.size} funcref)\n")
    printer.append("(elem (i32.const 0)\n")
    val dynIndex2 = arrayOfNulls<String>(dynIndex.size)
    for ((name, idx) in dynIndex) {
        var name2 = name
        // resolve by aliases
        while (true) {
            val sig = hIndex.methodAliases[name2] ?: break
            val name3 = methodName(sig)
            if (name2 == name3) {
                if (name2 !in implementedMethods) {
                    printUsed(sig)
                    throw NotImplementedError("Missing impl of $name2/$sig")
                }
                break
            }
            name2 = name3
        }
        if (dynIndex2[idx] != null) {
            throw IllegalStateException("Duplicate entry $idx: $name -> $name2 != ${dynIndex2[idx]}")
        }
        if (nameToMethod[name2] in hIndex.abstractMethods)
            throw IllegalStateException("$name is abstract, but also listed")
        dynIndex2[idx] = name2
    }
    for (idx in dynIndex2.indices) {
        val name = dynIndex2[idx] ?: throw NullPointerException("Missing index $idx")
        val sig = nameToMethod[name]
        if (sig in hIndex.abstractMethods) throw IllegalStateException("$name is abstract, but also listed")
        gIndex.actuallyUsed.add(dynIndexSig, name)
        printer.append("  $").append(name).append('\n')
    }
    printer.append(")\n")
    println("filtered ${dynamicFunctions.size} dynamic functions from ${implementedMethods.size} methods")
}

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
    // done append custom functions
    // append class instanceOf-table
    val classTableData = ByteArrayOutputStream2(numClasses * 4)
    val instTable = ByteArrayOutputStream2()
    var ptr = ptr0 + numClasses * 4
    val staticInitIdx = gIndex.getInterfaceIndex("", "<clinit>", "()V")

    if (gIndex.getFieldOffsets("java/lang/String", false).offset != objectOverhead + 8)
        throw IllegalStateException("Expected string to have size objectOverhead + 8 for hash and char[]")

    for (classId in 0 until numClasses) {
        if (classId == 0 || classId in 17 until 25) {
            // write 0 :), no table space used
            classTableData.writeLE32(0)
        } else {

            val clazz = gIndex.classNames[classId]
            val superClass = hIndex.superClass[clazz]
                ?: throw NullPointerException("Super class of $clazz ($classId) is unknown")

            val flags = hIndex.classFlags[clazz] ?: 0

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
                    println("warn: $it got index too late (interface)")
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
            instTable.writeLE32(gIndex.getFieldOffsets(clazz, false).offset)
            instTable.writeLE32(interfaces.size)
            for (j in interfaces) {
                instTable.writeLE32(gIndex.getClassIndex(j))
            }
            ptr += interfaces.size * 4 + 12

            val print =
                clazz == "me_anno_utils_pooling_Stack_storageXlambdav0_Lme_anno_utils_pooling_StackLme_anno_utils_pooling_StackXLocalStack"
            if (print) println("[$clazz]: $superClass, $interfaces")

            // here is space for a name and maybe more debug information :)
            // append call_dynamic data
            // look up interface functions...
            // and only implement those, that are actually available

            // these functions only need to be available, if the class is considered constructable

            if (clazz in dIndex.constructableClasses && !flags.hasFlag(ACC_ABSTRACT) && !flags.hasFlag(ACC_INTERFACE)) {

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
                        if (genericsTypies(sig) != genericsTypies(impl)) {
                            println()
                            println("---")
                            printUsed(sig)
                            printUsed(impl)
                            println(methodName(sig))
                            println(methodName(impl))
                            throw IllegalStateException(
                                "$sig cannot be linked to $impl, " +
                                        "because ${genericsTypies(sig)} != ${genericsTypies(impl)}"
                            )
                        }
                        implFunctions0[gIndex.getInterfaceIndex(sig.clazz, sig.name, sig.descriptor)] = impl
                        val name = methodName(impl)
                        if (name !in dynIndex) dynIndex[name] = dynIndex.size
                    }
                }

                if (flags.hasFlag(ACC_ENUM)) {
                    val impl = findMethod(clazz, "<clinit>", "()V")
                    implFunctions0[staticInitIdx] = impl!!
                    val name = methodName(impl)
                    if (name !in dynIndex) dynIndex[name] = dynIndex.size
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
                    .entries
                    .sortedBy { it.key } // sorted by id
                instTable.writeLE32(implFunctions.size)
                for ((id, sig) in implFunctions) {
                    instTable.writeLE32(id)
                    instTable.writeLE32(dynIndex[methodName(sig)]!!)
                }
                ptr += implFunctions.size * 8 + 4

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

    val ptr2 = appendData(printer, ptr0, classTableData, instTable)
    if (ptr != ptr2) throw IllegalStateException()
    return ptr
}

var staticTablePtr = -1
var clInitFlagTable = 0
val staticLookup = HashMap<String, Int>()
fun appendStaticInstanceTable(printer: StringBuilder2, ptr0: Int, numClasses: Int): Int {
    staticTablePtr = ptr0
    clInitFlagTable = ptr0 + 4 * numClasses // 4 bytes for offset to static memory
    var ptr = clInitFlagTable + numClasses // 1 byte for flag for init
    val staticBuffer = ByteArrayOutputStream2(numClasses * 4)
    for (i in 0 until numClasses) {
        val className = gIndex.classNames[i]
        val size = gIndex.getFieldOffsets(className, true).offset
        if (size == 0) {
            staticBuffer.writeLE32(0)
        } else {
            // println("writing $i static $className to $ptr, size: $size")
            staticBuffer.writeLE32(ptr)
            staticLookup[className] = ptr
            ptr += size
        }
    }
    val ptr2 = appendData(printer, staticTablePtr, staticBuffer)
    if (ptr < ptr2) throw IllegalStateException("$ptr >= $ptr2")
    return ptr
}

var methodTablePtr = 0
var aidtCtr = 50 // disabled
fun appendInvokeDynamicTable(printer: StringBuilder2, ptr0: Int, numClasses: Int): Int {

    methodTablePtr = ptr0

    val methodTable = ByteArrayOutputStream2(numClasses * 4)
    val table2 = ByteArrayOutputStream2(numClasses * 4)
    var ptr = ptr0 + numClasses * 4
    var numOk = 0
    var numBroken = 0
    var numAbstract = 0
    var numFixed = 0

    // create method table (for inheritance), #resolveIndirect
    fun getDynMethodIdx(clazz: Int): Map<GenericSig, Int> {
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
        val pic = getDynMethodIdx(i)
        val clazz = gIndex.classNames[i]
        if (gIndex.classNames[i] !in dIndex.constructableClasses) {
            // println("writing $i: $clazz to null, because not constructable")
            methodTable.writeLE32(0)
        } else {
            // todo: if all is the same as the parent class, we could link to the parent class :)
            methodTable.writeLE32(ptr)
            val revPic = arrayOfNulls<GenericSig>(pic.size)
            table2.writeLE32(pic.size * 4)
            for ((m, idx) in pic) {
                if (revPic[idx] != null) throw IllegalStateException("Index must not appear twice in pic! $pic")
                revPic[idx] = m
            }
            val print = i == 39
            // if (print || aidtCtr < 50) println("writing $i: $clazz to $ptr, ${revPic.toList()}")
            for (idx in revPic.indices) {

                val sig0 = revPic[idx]!!
                val sig = MethodSig.c(clazz, sig0.name, sig0.descriptor)

                fun methodIsAbstract(sig: MethodSig): Boolean {
                    if (sig in hIndex.abstractMethods) return true
                    if (sig.clazz == "java/lang/Object") return false
                    if (sig in hIndex.jvmImplementedMethods) return false
                    val superClass = hIndex.superClass[sig.clazz] ?: throw NullPointerException(sig.clazz)
                    return methodIsAbstract(MethodSig.c(superClass, sig.name, sig.descriptor))
                }

                val impl = findMethod(clazz, sig) ?: sig
                val name = methodName(impl)
                // if method is missing, find replacement
                val mapped = hIndex.methodAliases[name]
                // if (print) println("$idx, $sig0 -> $sig, $impl, $mapped")
                val dynIndexI = dynIndex[name]
                if (dynIndexI != null) {
                    numOk++
                    table2.writeLE32(dynIndexI)
                    if (print || aidtCtr++ < 50) println("  $idx -> $dynIndexI")
                } else if (methodIsAbstract(mapped ?: impl)) {
                    numAbstract++
                    // to do redirect to an error function or to -1; don't warn then
                    table2.writeLE32(-1)
                    if (i == 14 && sig.name == "append") {
                        printUsed(sig)
                        if (mapped != null) printUsed(mapped)
                    }
                    if (print || aidtCtr++ < 50) println("  $idx -> -1") // , available:
                    /*for (m in hIndex.methods[clazz]!!.filter { it.name == sig.name }) {
                        printUsed(m)
                    }*/
                } else {
                    val sig2 = mapped ?: sig
                    if (sig2 in dIndex.usedMethods) {
                        numFixed++
                        if (sig2 in hIndex.abstractMethods) {
                            printUsed(sig2)
                            throw IllegalStateException("$name, $sig2 is abstract, but also listed")
                        }
                        val dynIndexJ = dynIndex.size
                        dynIndex[name] = dynIndexJ
                        table2.writeLE32(dynIndexJ)
                        if (print || aidtCtr++ < 50) println("  $idx -> $dynIndexJ*")
                    } else {
                        numBroken++
                        table2.writeLE32(-1)
                        if (print || aidtCtr++ < 50) {

                            println("[WARN] $sig ($i/$idx) is missing from dynIndex")
                            printUsed(sig)
                            println("  $idx -> -1*")

                            // to do check if any super class or interface is being used...
                            fun checkChildren(clazz: String) {
                                if (MethodSig.c(clazz, sig.name, sig.descriptor) in dIndex.usedMethods) {
                                    printUsed(sig)
                                    throw IllegalStateException("$sig is being used by super class $clazz")
                                }
                                for (child in hIndex.childClasses[clazz] ?: return) {
                                    checkChildren(child)
                                }
                            }

                            fun checkSuper(clazz: String) {
                                if (MethodSig.c(clazz, sig.name, sig.descriptor) in dIndex.usedMethods) {
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
            ptr += 4 + 4 * revPic.size
            // if (print) throw IllegalStateException("debug")
        }
    }
    println("dynamic table, ok: $numOk, abstract: $numAbstract, broken: $numBroken, fixed: $numFixed, index-size: ${dynIndex.size}")
    appendData(printer, ptr0, methodTable)
    appendData(printer, ptr0 + numClasses * 4, table2)
    return ptr
}

fun appendData(printer: StringBuilder2, startIndex: Int, vararg data: ByteArrayOutputStream2) =
    appendData(printer, startIndex, *data.map { it.toByteArray() }.toTypedArray())

val segments = ArrayList<IntRange>()

fun appendData(printer: StringBuilder2, startIndex: Int, vararg data: ByteArray): Int {
    val segment = startIndex until (startIndex + data.sumOf { it.size })
    val mid1 = segment.first + segment.last
    val length1 = segment.last - segment.first
    for (seg in segments) {
        val mid2 = seg.first + seg.last
        val length2 = seg.last - seg.first
        if (abs(mid1 - mid2) < length1 + length2)
            throw IllegalStateException("Overlapping segments $segment, $seg")
    }
    segments.add(segment)
    printer.append("(data ($ptrType.const ${startIndex}) \"")
    var index = startIndex
    for (dataI in data) {
        writeData(printer, dataI)
        index += dataI.size
    }
    printer.append("\")\n")
    return index
}

fun appendStringData(printer: StringBuilder2, gIndex: GeneratorIndex): Int {
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

