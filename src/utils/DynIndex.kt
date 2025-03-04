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
import org.apache.logging.log4j.LogManager
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
            .sortedBy { it.value.name + "/" + it.value.descriptor }
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

    var methodTablePtr = 0
    private var aidtCtr = 50 // disabled
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
            val superClazz = hIndex.superClass[gIndex.classNamesByIndex[clazz]] ?: "java/lang/Object"
            return getDynMethodIdx(gIndex.getClassIndex(superClazz))
        }

        for (i in 0 until numClasses) {
            val dynMethods = getDynMethodIdx(i)
            val clazz = gIndex.classNamesByIndex[i]

            val print = i == 1929
            // could be written to a file for debugging
            if (print) println("  dynMethodIndex[$i: $clazz]: $dynMethods")

            if (gIndex.classNamesByIndex[i] !in dIndex.constructableClasses) {
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
                    val sig = MethodSig.c(clazz, sig0.name, sig0.descriptor, false)

                    fun methodIsAbstract(sig: MethodSig): Boolean {
                        if (sig in hIndex.abstractMethods) return true
                        if (sig.clazz == "java/lang/Object") return false
                        if (sig in hIndex.jvmImplementedMethods) return false
                        val superClass = hIndex.superClass[sig.clazz] ?: throw NullPointerException(sig.clazz)
                        return methodIsAbstract(sig.withClass(superClass))
                    }

                    val impl = resolveMethod(sig, true) ?: sig
                    // if method is missing, find replacement
                    val mapped = hIndex.getAlias(impl)
                    val name = methodName(mapped)
                    if (print) println("  $idx, $sig0 -> $sig, $impl, $mapped")
                    val dynIndexI = dynIndex[name]
                    if (dynIndexI != null) {
                        numOk++
                        table2.writeLE32(dynIndexI.index)
                        if (print || aidtCtr++ < 50) println("  $idx -> $dynIndexI")
                        if (printDebug) {
                            debugInfo.append("  ").append(idx).append(": ")
                                .append(dynIndexI.index).append(" // ").append(mapped).append("\n")
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
                            val dynIndexJ = addDynIndex(mapped, name)
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
        val staticInitIdx = gIndex.getInterfaceIndex(InterfaceSig.c(STATIC_INIT, "()V"))

        assertEquals(objectOverhead + 8, gIndex.getFieldOffsets("java/lang/String", false).offset)
        for (classId in 0 until numClasses) {
            if (classId == 0 || classId in 17 until 25) { // 0 is Object, 17 until 25 are the native types
                // write 0 :), no table space used
                classTableData.writeLE32(0)
            } else {

                val clazz = gIndex.classNamesByIndex[classId]
                var superClass = hIndex.superClass[clazz]
                if (superClass == null) {
                    LOGGER.warn("Super class of $clazz ($classId) is unknown")
                    superClass = "java/lang/Object"
                }

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

                    fieldOffsets.allFields().entries.sortedBy { it.value.offset }.forEach { (name, data) ->
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
                            val impl = resolveMethod(sig.withClass(clazz), true)
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
                            addDynIndex(impl)
                        }
                    }

                    if (hIndex.isEnumClass(clazz)) {
                        val superInit = MethodSig.c(clazz, STATIC_INIT, "()V", true)
                        val impl = resolveMethod(superInit, throwNotConstructable = true)
                        implFunctions0[staticInitIdx] = impl!!
                        addDynIndex(impl)
                    }

                    if (print) {
                        println("other functions:")
                        for (sig in hIndex.methodsByClass[clazz] ?: emptySet()) {
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
                        instTable.writeLE32(getDynIndex(sig).index)
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
                    // todo current me: WTF is that???
                    printUsed(
                        MethodSig.c(
                            "java_lang_System_getProperty_Ljava_lang_StringLjava_lang_String",
                            "apply", "(Ljava/lang/Object;)Ljava/lang/Object;", true
                        )
                    )
                }
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

}