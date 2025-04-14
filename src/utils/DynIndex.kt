package utils

import dIndex
import dependency.ActuallyUsedIndex
import gIndex
import hIndex
import implementedMethods
import jvm.JVMShared.objectOverhead
import me.anno.io.Streams.writeLE32
import me.anno.utils.algorithms.Recursion
import me.anno.utils.assertions.assertFalse
import me.anno.utils.assertions.assertTrue
import org.apache.logging.log4j.LogManager
import translator.GeneratorIndex
import translator.GeneratorIndex.alignPointer
import utils.Descriptor.Companion.voidDescriptor
import utils.MethodResolver.resolveMethod
import utils.PrintUsed.printUsed

object DynIndex {

    private data class DynIndexEntry(val sampleSig: MethodSig, val name: String, val index: Int)

    private val LOGGER = LogManager.getLogger(DynIndex::class)

    private val dynIndex = HashMap<String, DynIndexEntry>(4096)
    private val dynIndexSorted = ArrayList<DynIndexEntry>(4096)
    val dynIndexSig = MethodSig.c("", "dynIndex", voidDescriptor)

    private fun addDynIndex(sig: MethodSig, name: String = methodName(sig)): Int {
        return dynIndex.getOrPut(name) {
            val entry = DynIndexEntry(sig, name, dynIndex.size)
            dynIndexSorted.add(entry)
            entry
        }.index
    }

    private fun getDynIndex(sig: MethodSig): DynIndexEntry {
        val name = methodName(sig)
        return dynIndex[name]!!
    }

    private fun resolveByAlias(name: String): String {
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
        return name2
    }

    private fun findDynamicMethods(): List<MutableMap.MutableEntry<String, MethodSig>> {
        return implementedMethods.entries
            .filter { (_, sig) -> // saving space by remove functions that cannot be invoked dynamically
                sig.className != INTERFACE_CALL_NAME &&
                        sig.name != INSTANCE_INIT &&
                        sig.name != STATIC_INIT &&
                        sig.name !in dynIndex &&
                        !hIndex.isStatic(sig) &&
                        !hIndex.isFinal(sig) &&
                        !hIndex.isAbstract(sig) &&
                        hIndex.getAlias(sig) == sig
            }.sortedBy { it.key }
    }

    fun calculateDynamicFunctionTable() {
        val nameToMethod = hIndex.methodByName
        val dynamicMethods = findDynamicMethods()
        for ((name, sig) in dynamicMethods) {
            val sig1 = nameToMethod[name]
            if (sig1 != null && hIndex.isAbstract(sig1))
                throw IllegalStateException("$name is abstract, but also listed")
            addDynIndex(sig, name)
        }
        functionTable.ensureCapacity(dynIndex.size)
        for ((sig0, name, _) in dynIndexSorted) {
            val name2 = resolveByAlias(name)

            val sig2 = nameToMethod[name2]
            assertFalse(sig2 != null && hIndex.isAbstract(sig2)) { "$name is abstract, but also listed" }

            val sig = nameToMethod[name2] ?: sig0
            assertFalse(hIndex.isAbstract(sig)) { "$name2 is abstract, but also listed" }

            functionTable.add(name2)
            ActuallyUsedIndex.add(dynIndexSig, sig)
        }
        LOGGER.info("${dynamicMethods.size}/${implementedMethods.size} implemented methods are dynamic")
    }

    fun appendDynamicFunctionTable(printer: StringBuilder2) {
        printer.append("(table ${dynIndex.size} funcref)\n")
        printer.append("(elem (i32.const 0)\n")
        for (i in functionTable.indices) {
            printer.append("  $").append(functionTable[i]).append('\n')
        }
        printer.append(")\n")
    }

    /**
     * create method table (for inheritance), #resolveIndirect
     * */
    private fun getDynamicMethodsByClassIndex(clazz: Int): Map<InterfaceSig, Int> {
        val pic = gIndex.dynMethodIndices[clazz]
        if (pic != null) return pic
        if (clazz == 0) throw IllegalStateException("java/lang/Object must have dynamic function table!")
        val superClazz = hIndex.superClass[gIndex.classNames[clazz]] ?: "java/lang/Object"
        return getDynamicMethodsByClassIndex(gIndex.getClassId(superClazz))
    }

    private fun remapDynamicMethods(dynamicMethods: Map<InterfaceSig, Int>): Array<InterfaceSig?> {
        val dynIndexToMethod = arrayOfNulls<InterfaceSig>(dynamicMethods.size)
        for ((m, idx) in dynamicMethods) {
            if (dynIndexToMethod[idx] != null) throw IllegalStateException("Index must not appear twice in pic! $dynamicMethods")
            dynIndexToMethod[idx] = m
        }
        return dynIndexToMethod
    }

    private fun methodIsAbstract(sig: MethodSig): Boolean {
        if (hIndex.isAbstract(sig)) return true
        if (sig.className == "java/lang/Object") return false
        if (sig in hIndex.jvmImplementedMethods) return false
        var superClass = hIndex.superClass[sig.className]
        if (superClass == null) {
            LOGGER.warn("Super class of ${sig.className} is missing")
            superClass = "java/lang/Object"
        }
        return methodIsAbstract(sig.withClass(superClass))
    }

    private var printDynamicIndex = false

    fun getDynamicIndex(classId: Int, sig: MethodSig, sig0: InterfaceSig, idx: Int, debugInfo: StringBuilder2?): Int {
        val print = printDynamicIndex
        val impl = resolveMethod(sig, true) ?: sig
        // if method is missing, find replacement
        val mapped = hIndex.getAlias(impl)
        val name = methodName(mapped)
        if (print) println("  $idx, $sig0 -> $sig, $impl, $mapped")
        val dynIndexI = dynIndex[name]
        if (dynIndexI != null) {
            numOk++
            if (print || aidtCtr++ < 50) println("  $idx -> $dynIndexI")
            if (printDebug && debugInfo != null) {
                debugInfo.append("  ").append(idx).append(": ")
                    .append(dynIndexI.index).append(" // ").append(mapped).append("\n")
            }
            return dynIndexI.index
        } else if (methodIsAbstract(mapped)) {
            numAbstract++
            // to do redirect to an error function or to -1; don't warn then
            if (classId == 14 && sig.name == "get") {
                printUsed(sig)
                if (mapped != sig) printUsed(mapped)
            }
            if (print || aidtCtr++ < 50) println("    $idx -> -1") // , available:
            if (printDebug && debugInfo != null) {
                debugInfo.append("  ").append(idx).append(": ")
                    .append(sig0).append(" -> -1 // ").append(mapped).append("\n")
            }
            return -1
        } else {
            if (mapped in dIndex.usedMethods) {
                numFixed++
                if (hIndex.isAbstract(mapped)) {
                    printUsed(mapped)
                    throw IllegalStateException("$name, $mapped is abstract, but also listed")
                }
                val dynIndexJ = addDynIndex(mapped, name)
                if (print || aidtCtr++ < 50) println("    $idx -> $dynIndexJ*")
                if (printDebug && debugInfo != null) {
                    debugInfo.append("  ").append(idx).append(": ")
                        .append(dynIndexJ).append("* // ").append(mapped).append("\n")
                }
                return dynIndexJ
            } else {
                numBroken++
                if (printDebug && debugInfo != null) {
                    debugInfo.append("  ").append(idx).append(": ")
                        .append(sig0).append(" -> -1X // ").append(mapped).append("\n")
                }
                if (!(sig.name == INSTANCE_INIT && sig.className in initMayBeBrokenFor)) {
                    LOGGER.warn("$sig ($classId/$idx) is missing from dynIndex")
                    printUsed(sig, true)
                    LOGGER.warn("    $idx -> -1*")
                }
                return -1
            }
        }
    }

    private val initMayBeBrokenFor = listOf(
        // these are only instantiated by this compiler
        "java/lang/reflect/AccessibleObject", "java/lang/reflect/Field",
        "java/lang/reflect/Executable", "java/lang/reflect/Method", "java/lang/reflect/Constructor"
    )

    private var numOk = 0
    private var numBroken = 0
    private var numAbstract = 0
    private var numFixed = 0

    var resolveIndirectTablePtr = 0
    private var aidtCtr = 50 // disabled
    fun appendInvokeDynamicTable(printer: StringBuilder2, ptr: Int, numClasses: Int): Int {
        LOGGER.info("[appendInvokeDynamicTable]")
        val debugInfo = StringBuilder2()

        val startOfMethodTable = alignPointer(ptr)
        resolveIndirectTablePtr = startOfMethodTable

        val methodTable = ByteArrayOutputStream2(numClasses * 4)
        val dynamicIndexData = ByteArrayOutputStream2(numClasses * 4)
        val startOfDynamicIndexData = alignPointer(startOfMethodTable + numClasses * 4)
        var ptr = startOfDynamicIndexData

        for (classId in 0 until numClasses) {
            val dynamicMethods = getDynamicMethodsByClassIndex(classId)
            val clazz = gIndex.classNames[classId]

            val print = false
            if (print) println("  dynMethodIndex[$classId: $clazz]: $dynamicMethods")
            printDynamicIndex = print

            if (printDebug) debugInfo.append("[").append(classId).append("]: ").append(clazz)
            if (gIndex.classNames[classId] !in dIndex.constructableClasses) {
                if (printDebug) debugInfo.append(" not constructable\n")
                if (print) println("  writing $classId: $clazz to null, because not constructable")
                methodTable.writeLE32(0)
            } else {
                if (printDebug) debugInfo.append("\n")
                methodTable.writeLE32(ptr)
                dynamicIndexData.writeLE32(dynamicMethods.size * 4)
                val dynIndexToMethod = remapDynamicMethods(dynamicMethods)
                if (print || aidtCtr < 50) println("  writing $classId: $clazz to $ptr, ${dynIndexToMethod.toList()}")
                for (idx in dynIndexToMethod.indices) {
                    val sig0 = dynIndexToMethod[idx]!!
                    val sig1 = sig0.withClass(clazz)
                    val methodId = getDynamicIndex(classId, sig1, sig0, idx, debugInfo)
                    dynamicIndexData.writeLE32(methodId)
                }
                ptr += 4 + 4 * dynIndexToMethod.size
            }
        }
        LOGGER.info("Wrote dynamic table, ok: $numOk, abstract: $numAbstract, broken: $numBroken, fixed: $numFixed, index-size: ${dynIndex.size}")
        appendData(printer, startOfMethodTable, methodTable)
        appendData(printer, startOfDynamicIndexData, dynamicIndexData)
        if (printDebug) {
            debugFolder.getChild("inheritanceTable1.txt")
                .writeBytes(debugInfo.values, 0, debugInfo.size)
        }
        return ptr
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
    fun appendInheritanceTable(printer: StringBuilder2, classTableStart: Int, numClasses: Int): Int {
        LOGGER.info("[appendInheritanceTable]")
        val debugInfo = StringBuilder2(1024)
        // done append custom functions
        // append class instanceOf-table
        val classTableData = ByteArrayOutputStream2(numClasses * 4)
        val instanceTableData = ByteArrayOutputStream2()
        val instanceTableStart = alignPointer(classTableStart + numClasses * 4)
        var ptr = instanceTableStart
        val staticInitIdx = gIndex.getInterfaceIndex(InterfaceSig.c(STATIC_INIT, voidDescriptor))

        val emptyTableEntry = ptr
        instanceTableData.writeLE32(0) // standard super class
        instanceTableData.writeLE32(objectOverhead) // empty object, just in case somebody wants an instance of it
        instanceTableData.writeLE32(0) // no interfaces
        instanceTableData.writeLE32(0) // no interface signatures
        ptr += 16

        for (classId in 0 until numClasses) {
            val clazz = gIndex.classNames[classId]
            if (clazz in NativeTypes.nativeTypes) {
                // link empty node, shouldn't be ever instantiated
                // link empty instead of creating an exception for runtime speed
                classTableData.writeLE32(emptyTableEntry)
            } else {

                var superClass = hIndex.superClass[clazz]
                if (superClass == null) {
                    if (classId > 0) {
                        LOGGER.warn("Super class of $clazz ($classId) is unknown")
                        superClass = "java/lang/Object"
                    }
                }

                classTableData.writeLE32(ptr)

                // super class index
                // instance size
                // #interfaces
                // ... [classId]
                // #functions
                // ... [interfaceId, methodId]

                instanceTableData.writeLE32(if (superClass != null) gIndex.getClassId(superClass) else 0)
                val interfaces = getInterfaces(clazz, numClasses)
                val fieldOffsets = gIndex.getFieldOffsets(clazz, false)
                val clazzSize = gIndex.getInstanceSize(clazz)
                instanceTableData.writeLE32(clazzSize)
                instanceTableData.writeLE32(interfaces.size)
                for (j in interfaces) {
                    instanceTableData.writeLE32(gIndex.getClassId(j))
                }
                ptr += interfaces.size * 4 + 12

                if (printDebug) appendGeneralDebugInfo(
                    debugInfo, classId, clazz, superClass,
                    interfaces, clazzSize, fieldOffsets
                )

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

                    val implFunctions0 = findImplementedInterfaceMethods(clazz, interfaces)

                    if (hIndex.isEnumClass(clazz)) {
                        val superInit = MethodSig.staticInit(clazz)
                        val impl = resolveMethod(superInit, throwNotConstructable = true)
                        implFunctions0[staticInitIdx] = impl!!
                        addDynIndex(impl)
                    }

                    val implFunctions = implFunctions0
                        .entries.sortedBy { it.key } // sorted by id for faster lookup
                    instanceTableData.writeLE32(implFunctions.size)
                    for ((id, sig) in implFunctions) {
                        instanceTableData.writeLE32(id)
                        instanceTableData.writeLE32(getDynIndex(sig).index)
                    }
                    ptr += implFunctions.size * 8 + 4

                    if (printDebug) {
                        for ((id, sig) in implFunctions) {
                            debugInfo.append("  method[").append(id).append("]: ").append(sig).append("\n")
                        }
                    }
                } else {
                    instanceTableData.writeLE32(0)
                    ptr += 4
                }
            }
        }

        if (printDebug) {
            debugFolder.getChild("inheritanceTable.txt")
                .writeBytes(debugInfo.values, 0, debugInfo.size)
        }

        appendData(printer, classTableStart, classTableData)
        appendData(printer, instanceTableStart, instanceTableData)
        return ptr
    }

    private fun findImplementedInterfaceMethods(
        clazz: String, interfaces: Set<String>
    ): HashMap<Int, MethodSig> {
        val print = false
        val implFunctions0 = HashMap<Int, MethodSig>()
        for (sig in dIndex.usedInterfaceCalls) {
            // only if is actually instance of interface
            if (sig.className in interfaces) {
                val impl = resolveMethod(sig.withClass(clazz), true)
                    ?: continue
                if (hIndex.isAbstract(impl)) {
                    continue
                }
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
                addDynIndex(impl)
            }
        }
        if (print) {
            println("class: $clazz")
            println("interfaces: $interfaces")
            for ((id, impl) in implFunctions0) {
                println("  [$id]: ${impl.name}${impl.descriptor}")
            }
            val missingSig = MethodSig.c("kotlin/Function", "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;")
            println("has sig? ${missingSig in dIndex.usedInterfaceCalls}")
            println("used interface calls:")
            for (call in dIndex.usedInterfaceCalls
                .map { it.toString() }.sorted()) {
                println("  $call")
            }
        }
        return implFunctions0
    }

    private fun getInterfaces(clazz: String, numClasses: Int): Set<String> {
        val interfaces = HashSet<String>()
        Recursion.processRecursive(clazz) { item, remaining ->
            val classInterfaces = hIndex.interfaces[item] ?: emptyList()
            interfaces.addAll(classInterfaces)
            remaining.addAll(classInterfaces)
            val superClass = hIndex.superClass[item]
            if (superClass != null) remaining.add(superClass)
        }
        interfaces.removeIf { interfaceName ->
            if (gIndex.getClassId(interfaceName) >= numClasses) {
                LOGGER.warn("$interfaceName got index too late (interface)")
                true
            } else false
        }
        return interfaces
    }

    private fun appendGeneralDebugInfo(
        debugInfo: StringBuilder2,
        classId: Int, clazz: String, superClass: String?,
        interfaces: Set<String>, clazzSize: Int,
        fieldOffsets: GeneratorIndex.ClassOffsets
    ) {
        debugInfo.append("[").append(classId).append("]: ").append(clazz).append("\n")
        if (superClass != null) debugInfo.append("  extends ").append(superClass).append("\n")
        for (interface1 in interfaces) {
            debugInfo.append("  implements ").append(interface1).append("\n")
        }
        debugInfo.append("  fields[total: ").append(clazzSize).append("]:\n")

        fieldOffsets.allFields().entries.sortedBy { it.value.offset }.forEach { (name, data) ->
            debugInfo.append("    *").append(data.offset).append(": ").append(name)
                .append(": ").append(data.jvmType).append("\n")
        }
    }

}