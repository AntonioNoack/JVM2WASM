package utils

import dIndex
import dependency.ActuallyUsedIndex
import gIndex
import hIndex
import jvm.JVM32.objectOverhead
import me.anno.io.Streams.writeLE32
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertFalse
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.Compare.ifSame
import me.anno.utils.structures.Recursion
import org.apache.logging.log4j.LogManager
import translator.GeneratorIndex
import utils.MethodResolver.resolveMethod

object DynIndex {

    private data class DynIndexEntry(val sampleSig: MethodSig, val name: String, val index: Int)

    private val LOGGER = LogManager.getLogger(DynIndex::class)

    private val dynIndex = HashMap<String, DynIndexEntry>(4096)
    private val dynIndexSorted = ArrayList<DynIndexEntry>(4096)
    val dynIndexSig = MethodSig.c("", "dynIndex", "()V", false)

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

    data class SortingKey(val sig: MethodSig) : Comparable<SortingKey> {
        override fun compareTo(other: SortingKey): Int {
            return sig.name.compareTo(other.sig.name).ifSame {
                sig.descriptor.raw.compareTo(other.sig.descriptor.raw)
            }
        }
    }

    fun appendDynamicFunctionTable(printer: StringBuilder2, implementedMethods: Map<String, MethodSig>) {
        val nameToMethod = calculateNameToMethod()
        val dynamicFunctions = implementedMethods.entries
            .filter { (_, sig) -> // saving space by remove functions that cannot be invoked dynamically
                sig.clazz != INTERFACE_CALL_NAME &&
                        sig.name != INSTANCE_INIT &&
                        sig.name != STATIC_INIT &&
                        sig.name !in dynIndex &&
                        sig !in hIndex.staticMethods &&
                        sig !in hIndex.finalMethods &&
                        sig !in hIndex.abstractMethods &&
                        hIndex.getAlias(sig) == sig
            }
            .sortedBy { (_, sig) -> SortingKey(sig) }
        for ((name, sig) in dynamicFunctions) {
            if (nameToMethod[name] in hIndex.abstractMethods)
                throw IllegalStateException("$name is abstract, but also listed")
            addDynIndex(sig, name)
        }
        printer.append("(table ${dynIndex.size} funcref)\n")
        printer.append("(elem (i32.const 0)\n")
        functionTable.ensureCapacity(dynIndex.size)
        for ((sig0, name, _) in dynIndexSorted) {
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

            assertFalse(nameToMethod[name2] in hIndex.abstractMethods) { "$name is abstract, but also listed" }

            val sig = nameToMethod[name2] ?: sig0
            assertFalse(sig in hIndex.abstractMethods) { "$name2 is abstract, but also listed" }

            printer.append("  $").append(name2).append('\n')
            functionTable.add(name2)
            ActuallyUsedIndex.add(dynIndexSig, sig)
        }
        printer.append(")\n")
        LOGGER.info("Filtered ${dynamicFunctions.size} dynamic functions from ${implementedMethods.size} methods")
    }

    /**
     * create method table (for inheritance), #resolveIndirect
     * */
    private fun getDynamicMethodsByClassIndex(clazz: Int): Map<InterfaceSig, Int> {
        val pic = gIndex.dynMethodIndices[clazz]
        if (pic != null) return pic
        if (clazz == 0) throw IllegalStateException("java/lang/Object must have dynamic function table!")
        val superClazz = hIndex.superClass[gIndex.classNamesByIndex[clazz]] ?: "java/lang/Object"
        return getDynamicMethodsByClassIndex(gIndex.getClassIndex(superClazz))
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
        if (sig in hIndex.abstractMethods) return true
        if (sig.clazz == "java/lang/Object") return false
        if (sig in hIndex.jvmImplementedMethods) return false
        val superClass = hIndex.superClass[sig.clazz] ?: throw NullPointerException(sig.clazz)
        return methodIsAbstract(sig.withClass(superClass))
    }

    private var printDynamicIndex = false

    fun getDynamicIndex(classId: Int, sig: MethodSig, sig0: InterfaceSig, idx: Int, debugInfo: StringBuilder2): Int {
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
            if (printDebug) {
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
            if (printDebug) {
                debugInfo.append("  ").append(idx).append(": ")
                    .append(sig0).append(" -> -1 // ").append(mapped).append("\n")
            }
            return -1
        } else {
            if (mapped in dIndex.usedMethods) {
                numFixed++
                if (mapped in hIndex.abstractMethods) {
                    printUsed(mapped)
                    throw IllegalStateException("$name, $mapped is abstract, but also listed")
                }
                val dynIndexJ = addDynIndex(mapped, name)
                if (print || aidtCtr++ < 50) println("    $idx -> $dynIndexJ*")
                if (printDebug) {
                    debugInfo.append("  ").append(idx).append(": ")
                        .append(dynIndexJ).append("* // ").append(mapped).append("\n")
                }
                return dynIndexJ
            } else {
                numBroken++
                if (printDebug) {
                    debugInfo.append("  ").append(idx).append(": ")
                        .append(sig0).append(" -> -1X // ").append(mapped).append("\n")
                }
                if (true) {

                    LOGGER.warn("$sig ($classId/$idx) is missing from dynIndex")
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
                return -1
            }
        }
    }

    private var numOk = 0
    private var numBroken = 0
    private var numAbstract = 0
    private var numFixed = 0

    var resolveIndirectTablePtr = 0
    private var aidtCtr = 50 // disabled
    fun appendInvokeDynamicTable(printer: StringBuilder2, startOfMethodTable: Int, numClasses: Int): Int {
        LOGGER.info("[appendInvokeDynamicTable]")
        val debugInfo = StringBuilder2()

        resolveIndirectTablePtr = startOfMethodTable

        val methodTable = ByteArrayOutputStream2(numClasses * 4)
        val dynamicIndexData = ByteArrayOutputStream2(numClasses * 4)
        val startOfDynamicIndexData = startOfMethodTable + numClasses * 4
        var ptr = startOfDynamicIndexData

        for (classId in 0 until numClasses) {
            val dynamicMethods = getDynamicMethodsByClassIndex(classId)
            val clazz = gIndex.classNamesByIndex[classId]

            val print = false
            if (print) println("  dynMethodIndex[$classId: $clazz]: $dynamicMethods")
            printDynamicIndex = print

            if (printDebug) debugInfo.append("[").append(classId).append("]: ").append(clazz)
            if (gIndex.classNamesByIndex[classId] !in dIndex.constructableClasses) {
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
        val instanceTableStart = classTableStart + numClasses * 4
        var ptr = instanceTableStart
        val staticInitIdx = gIndex.getInterfaceIndex(InterfaceSig.c(STATIC_INIT, "()V"))

        val emptyTableEntry = ptr
        instanceTableData.writeLE32(0) // standard super class
        instanceTableData.writeLE32(objectOverhead) // empty object, just in case somebody wants an instance of it
        instanceTableData.writeLE32(0) // no interfaces
        instanceTableData.writeLE32(0) // no interface signatures
        ptr += 16

        assertEquals(objectOverhead + 8, gIndex.getFieldOffsets("java/lang/String", false).offset)
        for (classId in 0 until numClasses) {
            val clazz = gIndex.classNamesByIndex[classId]
            if (clazz in NativeTypes.nativeTypes) {
                // link empty node, shouldn't be ever instantiated
                // link empty instead of creating an exception for runtime speed
                classTableData.writeLE32(emptyTableEntry)
            } else {

                var superClass = hIndex.superClass[clazz]
                if (superClass == null) {
                    LOGGER.warn("Super class of $clazz ($classId) is unknown")
                    if (classId > 0) superClass = "java/lang/Object"
                }

                classTableData.writeLE32(ptr)

                // super class index
                // instance size
                // #interfaces
                // ... [classId]
                // #functions
                // ... [interfaceId, methodId]

                instanceTableData.writeLE32(if (superClass != null) gIndex.getClassIndex(superClass) else 0)
                val interfaces = getInterfaces(clazz, numClasses)
                val fieldOffsets = gIndex.getFieldOffsets(clazz, false)
                val clazzSize = fieldOffsets.offset
                instanceTableData.writeLE32(clazzSize)
                instanceTableData.writeLE32(interfaces.size)
                for (j in interfaces) {
                    instanceTableData.writeLE32(gIndex.getClassIndex(j))
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

                    val implFunctions0 = findImplementedMethods(clazz, interfaces)

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

    private fun findImplementedMethods(
        clazz: String, interfaces: Set<String>
    ): HashMap<Int, MethodSig> {
        val implFunctions0 = HashMap<Int, MethodSig>()
        for (sig in dIndex.usedInterfaceCalls) {
            // only if is actually instance of interface
            if (sig.clazz in interfaces) {
                val impl = resolveMethod(sig.withClass(clazz), true)
                    ?: continue
                if (impl in hIndex.abstractMethods) {
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
            if (gIndex.getClassIndex(interfaceName) >= numClasses) {
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
                .append(": ").append(data.type).append("\n")
        }
    }

}