package utils

import api
import byteStrings
import canThrowError
import cannotThrow
import dIndex
import fieldsRWRequired
import gIndex
import hIndex
import hierarchy.DelayedLambdaUpdate
import hierarchy.DelayedLambdaUpdate.Companion.getSynthClassName
import hierarchy.FirstClassIndexer
import hierarchy.FirstClassIndexer.Companion.readType
import implementedMethods
import jvm.JVM32.*
import listEntryPoints
import listLibrary
import listSuperClasses
import me.anno.utils.Clock
import me.anno.utils.assertions.assertFail
import me.anno.utils.structures.Compare.ifSame
import me.anno.utils.structures.lists.Lists.any2
import me.anno.utils.structures.lists.Lists.firstOrNull2
import me.anno.utils.structures.lists.Lists.sortedByTopology
import me.anno.utils.types.Booleans.hasFlag
import me.anno.utils.types.Booleans.toInt
import org.apache.logging.log4j.LogManager
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.ACC_STATIC
import replaceClass
import resolvedMethods
import translator.ClassTranslator
import translator.FoundBetterReader
import utils.MethodResolver.resolveMethod
import wasm.instr.FuncType
import wasm.instr.Instruction
import wasm.parser.FunctionImpl
import wasm.parser.Import
import wasm.parser.WATParser
import java.io.IOException

private val LOGGER = LogManager.getLogger("Compiling")

fun <V> eq(a: V, b: V) {
    if (a != b) throw IllegalStateException("$a != $b")
}

fun eq(clazz: String, name: String, descriptor: String, offset: Int) {
    eq(gIndex.getFieldOffset(clazz, name, descriptor, false), objectOverhead + offset)
}

/**
 * checks and registers offsets to be what we expect:
 * they might be used elsewhere in the project hardcoded to certain addresses - we should clean that up in the future
 * */
fun registerDefaultOffsets() {

    eq(gIndex.getDynMethodIdx(MethodSig.c("java/lang/Object", INSTANCE_INIT, "()V", false)), 0)
    eq(gIndex.getType(true, "()V", true), FuncType(listOf(), listOf(ptrType)))

    // prepare String properties
    gIndex.stringClass = gIndex.getClassIndex(replaceClass("java/lang/String"))
    gIndex.stringArrayClass = gIndex.getClassIndex(if (byteStrings) "[B" else "[C")

    eq(gIndex.getFieldOffset("[]", "length", "I", false), objectOverhead)
    eq(gIndex.getFieldOffset(replaceClass("java/lang/String"), "value", "[C", false), objectOverhead)
    eq(gIndex.getFieldOffset(replaceClass("java/lang/String"), "hash", "I", false), objectOverhead + ptrSize)

    hIndex.registerSuperClass("java/lang/reflect/Field", "java/lang/reflect/AccessibleObject")
    hIndex.registerSuperClass("java/lang/reflect/Executable", "java/lang/reflect/AccessibleObject")
    hIndex.registerSuperClass("java/lang/reflect/Constructor", "java/lang/reflect/Executable")

    gIndex.getFieldOffset("java/lang/System", "in", "Ljava/io/InputStream;", true)
    gIndex.getFieldOffset("java/lang/System", "out", "Ljava/io/PrintStream;", true)
    gIndex.getFieldOffset("java/lang/System", "err", "Ljava/io/PrintStream;", true)
    eq("java/lang/Throwable", "detailMessage", "Ljava/lang/String;", 0)
    eq("java/lang/Throwable", "stackTrace", "[Ljava/lang/StackTraceElement;", ptrSize)

    gIndex.getFieldOffset("java/lang/StackTraceElement", "declaringClass", "Ljava/lang/String;", false)
    gIndex.getFieldOffset("java/lang/StackTraceElement", "methodName", "Ljava/lang/String;", false)
    gIndex.getFieldOffset("java/lang/StackTraceElement", "fileName", "Ljava/lang/String;", false)
    gIndex.getFieldOffset("java/lang/StackTraceElement", "lineNumber", "I", false)

    eq(gIndex.getFieldOffset("java/lang/Class", "name", "Ljava/lang/String", false), objectOverhead + 0)
    eq(gIndex.getFieldOffset("java/lang/Class", "fields", "[Ljava/lang/reflect/Field", false), objectOverhead + ptrSize)
    eq("java/lang/Class", "methods", "[Ljava/lang/reflect/Method", ptrSize * 2)
    eq("java/lang/Class", "index", "I", ptrSize * 3)

    gIndex.getFieldOffset("java/lang/reflect/AccessibleObject", "securityCheckCache", "Ljava/lang/Object", false) // 0
    gIndex.getFieldOffset("java/lang/reflect/AccessibleObject", "override", "Z", false) // 4
    gIndex.getFieldOffset("java/lang/reflect/Field", "securityCheckCache", "Ljava/lang/Object", false) // 0
    gIndex.getFieldOffset("java/lang/reflect/Field", "override", "Z", false) // 4
    eq("java/lang/reflect/Field", "name", "Ljava/lang/String", 1 + ptrSize)
    eq("java/lang/reflect/Field", "slot", "I", 1 + 2 * ptrSize)
    eq("java/lang/reflect/Field", "type", "Ljava/lang/Class", 1 + 2 * ptrSize + 4)
    eq("java/lang/reflect/Field", "modifiers", "I", 1 + 3 * ptrSize + 4)
    eq("java/lang/reflect/Field", "clazz", "Ljava/lang/Class", 1 + 3 * ptrSize + 2 * 4)

    // for sun/misc
    gIndex.getFieldOffset("java/lang/Thread", "threadLocalRandomSeed", "J", false)
    gIndex.getFieldOffset("java/lang/Thread", "threadLocalRandomSecondarySeed", "J", false)
    gIndex.getFieldOffset("java/lang/Thread", "threadLocalRandomProbe", "I", false)

    gIndex.getFieldOffset("java/lang/Class", "enumConstants", "[]", false)

    // reduce number of requests to <clinit> (was using 11% CPU time according to profiler)
    hIndex.finalFields[FieldSig("jvm/JVM32", "objectOverhead", "I", true)] = objectOverhead
    hIndex.finalFields[FieldSig("jvm/JVM32", "arrayOverhead", "I", true)] = arrayOverhead
    hIndex.finalFields[FieldSig("jvm/JVM32", "trackAllocations", "Z", true)] = trackAllocations

    eq(gIndex.getInterfaceIndex(InterfaceSig.c(STATIC_INIT, "()V")), 0)
    gIndex.getFieldOffset("java/lang/reflect/Constructor", "clazz", "Ljava/lang/Class", false)

}

fun indexHierarchyFromEntryPoints() {
    listEntryPoints { clazz ->
        if (hIndex.doneClasses.add(clazz)) {
            ClassReader(clazz)
                .accept(FirstClassIndexer(hIndex, clazz), 0)
        }
    }

    listLibrary { clazz ->
        if (hIndex.doneClasses.add(clazz)) {
            try {
                ClassReader(clazz)
                    .accept(FirstClassIndexer(hIndex, clazz), 0)
            } catch (e: Exception) {
                throw RuntimeException(clazz, e)
            }
        }
    }

    LOGGER.info("Empty functions: ${hIndex.emptyFunctions.size}")
}

fun resolveGenericTypes() {
    LOGGER.info("[resolveGenericTypes]")
    for ((clazz, superTypes) in hIndex.genericSuperTypes) {
        val baseMethods = hIndex.methodsByClass[clazz] ?: continue
        val ownGenerics = hIndex.generics[clazz]
        for (superType in superTypes) {

            // todo map all its generic methods
            val clearSuperType = single(descWithoutGenerics(superType))
            val generics = hIndex.generics[clearSuperType]

            if (generics == null) {
                LOGGER.warn("Missing generics of $clearSuperType by $superType")
                continue
            }

            var i = superType.indexOf('<') + 1
            val params = ArrayList<String>(generics.size)
            for (j in generics.indices) {
                // extract next param from generics
                val k = superType.readType(i)
                params.add(descWithoutGenerics(superType.substring(i, k)))
                i = if (superType[k] == '>') superType.indexOf('<', k + 1) + 1 else k
                if (i == 0) break
            }

            // to do find all abstract methods in superType to be mapped
            // to do all parent classes as well (?)
            val abstractMethods = hIndex.methodsByClass[clearSuperType]
                ?.filter { it in hIndex.abstractMethods && it in hIndex.genericMethodSigs }
                ?: continue

            for (method in abstractMethods) {

                // first test, that there is valid candidates?
                // same name, same number of params, not abstract
                val types = genericsTypes(method) // abstract method cannot be static
                val candidates = baseMethods.filter {
                    it.name == method.name &&
                            it.descriptor != method.descriptor &&
                            it !in hIndex.abstractMethods &&
                            // test for same number of arguments & same-typy return type
                            genericsTypes(it) == types
                }
                if (candidates.isNotEmpty()) {

                    // remove generics from call (for now)
                    val desc2 = hIndex.genericMethodSigs[method]!!
                    val desc2i = descWithoutGenerics(desc2)
                    // if so, no base-type generics are present -> nothing needs to be replaced or mapped
                    if (method.descriptor == desc2i) continue

                    /*if (!hasPrintedHeader) {
                        println("mapping task [$clazz]: $generics to $superType by $params")
                        hasPrintedHeader = true
                    }

                    println(" - ${method.name}, ${method.descriptor}, $desc2i")
                    println("   candidates: ${candidates.map { it.descriptor }}")
                    if (ownGenerics != null) println("   own generics: $ownGenerics")*/

                    if (params.size != generics.size) {
                        if (candidates.size == 1) {
                            // a guess 😅
                            val sig2 = method.withClass(clazz)
                            val sig3 = candidates.first()
                            if (sig2 == sig3) throw NotImplementedError()
                            hIndex.setAlias(sig2, sig3)
                            LOGGER.warn("  Arguments mismatch!, assumed only viable to match")
                        } else LOGGER.warn("  Arguments mismatch!")
                        continue
                    }

                    val mappedParams = params.mapNotNull { p ->
                        if (p[0] == 'T') {
                            (generics.firstOrNull { it.name == p }
                                ?: ownGenerics?.firstOrNull { it.name == p }
                                    // ?: throw NullPointerException("Didn't find mapping for $p, own generics: $ownGenerics")
                                    )?.superClass
                        } else p
                    }

                    if (mappedParams.size != params.size) {
                        if (candidates.size == 1) {
                            // map candidate
                            LOGGER.info("Using only candidate, bc didn't find all mappings for $clazz, $method")
                            val sig2 = method.withClass(clazz)
                            val sig3 = candidates.first()
                            if (sig2 == sig3) throw NotImplementedError()
                            hIndex.setAlias(sig2, sig3)
                        } else {
                            LOGGER.warn("Didn't find all mappings for $clazz, $method!")
                        }
                        continue
                    }

                    val desc2j = split2(desc2i, true)
                    val mappedDesc = desc2j.map { p ->
                        if (p[0] == 'T') {
                            mappedParams[generics.indexOfFirst { it.name == p }]
                        } else p
                    }

                    // generate a new signature
                    val newDescBuilder = StringBuilder2(mappedDesc.sumOf { it.length } + 2)
                    newDescBuilder.append('(')
                    for (j in 0 until mappedDesc.size - 1) {
                        newDescBuilder.append(mappedDesc[j])
                    }
                    newDescBuilder.append(')')
                    newDescBuilder.append(mappedDesc.last())
                    val newDesc = newDescBuilder.toString()

                    // check if this newly generated signature is implemented
                    val implMethod = candidates.firstOrNull { it.descriptor == newDesc }
                    if (implMethod != null) {
                        // define mapping
                        val sig2 = method.withClass(clazz)
                        if (sig2 == implMethod) throw NotImplementedError()
                        hIndex.setAlias(sig2, implMethod)
                    } else LOGGER.warn("Missing mapping for [$clazz]: $generics to $superType by $params, $candidates")

                }
            }
        }
    }
}

fun findNoThrowMethods() {
    LOGGER.info("[findNoThrowMethods]")
    for ((sig, annotations) in hIndex.annotations) {
        if (annotations.any2 { it.clazz == "annotations/NoThrow" }) {
            cannotThrow.add(methodName(sig))
        }
    }
}

fun findAliases() {
    LOGGER.info("[findAliases]")
    val nameToMethod0 = calculateNameToMethod()
    for ((sig, annotations) in hIndex.annotations) {
        val alias = annotations.firstOrNull { it.clazz == "annotations/Alias" }
        if (alias != null) {
            @Suppress("UNCHECKED_CAST")
            val aliasNames = alias.properties["names"] as List<String>
            for (aliasName in aliasNames) {
                if ('|' in aliasName) throw IllegalStateException(aliasName)
                // val aliasName = alias.properties["name"] as String
                if (aliasName.startsWith('$')) throw IllegalStateException("alias $aliasName must not start with dollar symbol")
                if (aliasName.contains('/')) throw IllegalStateException("alias $aliasName must not contain slashes, but underscores")
                if (aliasName.contains('(')) throw IllegalStateException("alias $aliasName must not contain slashes, but underscores")
                val previous = hIndex.getAlias(aliasName)
                if (previous != null && previous != sig) throw IllegalStateException("Cannot replace $aliasName -> $previous with -> $sig")
                hIndex.setAlias(aliasName, sig)
            }
        }
        val revAlias = annotations.firstOrNull { it.clazz == "annotations/RevAlias" }
        if (revAlias != null) {
            val aliasName = revAlias.properties["name"] as String
            if (aliasName.startsWith('$')) throw IllegalStateException("alias $aliasName must not start with dollar symbol")
            if (aliasName.contains('/')) throw IllegalStateException("alias $aliasName must not contain slashes, but underscores")
            if (aliasName.contains('(')) throw IllegalStateException("alias $aliasName must not contain slashes, but underscores")
            val sig1 = nameToMethod0[aliasName]
            if (sig1 != null) {
                hIndex.setAlias(sig, sig1)
            } else LOGGER.info("Skipped $sig -> $aliasName, because unknown")
        }
    }
}

fun collectEntryPoints(): Pair<Set<MethodSig>, Set<String>> {
    /**
     * collect all entry point methods
     * */
    val entryPoints = HashSet<MethodSig>()
    val entryClasses = HashSet<String>()
    fun entryClass(clazz: String) {
        entryClasses.add(clazz)
        val superClass = hIndex.superClass[clazz]
        if (superClass != null) entryClass(superClass)
        val interfaces = hIndex.interfaces[clazz]
        if (interfaces != null) for (interfaceI in interfaces)
            entryClass(interfaceI)
    }
    listEntryPoints({ clazz ->
        // all implemented methods
        entryPoints.addAll(hIndex.methodsByClass[clazz] ?: emptySet())
        entryClass(clazz)
    }) {
        if (it.name == INSTANCE_INIT) {
            entryClass(it.clazz)
        }
        entryPoints.add(it)
    }

    return entryPoints to entryClasses
}

fun findExportedMethods() {
    LOGGER.info("[findExportedMethods]")
    for ((sig, a) in hIndex.annotations) {
        if (a.any2 { it.clazz == "annotations/Export" }) {
            hIndex.exportedMethods.add(sig)
        }
    }
}

fun replaceRenamedDependencies() {
    LOGGER.info("[replaceRenamedDependencies]")
    // replace dependencies to get rid of things
    val methodNameToSig = HashMap<String, MethodSig>()
    for (sig in hIndex.methodsByClass.map { it.value }.flatten()) {
        methodNameToSig[methodName2(sig.clazz, sig.name, sig.descriptor)] = sig
    }
    for ((src, dst) in hIndex.methodAliases) {
        val src1 = methodNameToSig[src] ?: continue
        dIndex.methodDependencies[src1] = dIndex.methodDependencies[dst] ?: HashSet()
        dIndex.getterDependencies[src1] = dIndex.getterDependencies[dst] ?: HashSet()
        dIndex.setterDependencies[src1] = dIndex.setterDependencies[dst] ?: HashSet()
        dIndex.constructorDependencies[src1] = dIndex.constructorDependencies[dst] ?: HashSet()
        dIndex.interfaceDependencies[src1] = dIndex.interfaceDependencies[dst] ?: HashSet()
    }

    // resolve a lot of methods for better dependencies
    dIndex.methodDependencies = HashMap(
        dIndex.methodDependencies
            .mapValues { (_, dependencies) ->
                dependencies.map { sig ->
                    resolvedMethods.getOrPut(sig) {
                        val found = resolveMethod(sig, false) ?: sig
                        if (found != sig) hIndex.setAlias(sig, found)
                        if ((found in hIndex.staticMethods) == (sig in hIndex.staticMethods)) found else sig
                    }
                }.toHashSet()
            }
    )
}

fun checkMissingClasses() {
    LOGGER.info("[checkMissingClasses]")
    for (clazz in hIndex.methodsByClass.keys) {
        val superClass = hIndex.superClass[clazz] ?: continue
        if (clazz !in (hIndex.childClasses[superClass] ?: emptySet())) {
            assertFail("Missing $clazz in $superClass")
        }
    }
}

fun resolveAll(entryClasses: Set<String>, entryPoints: Set<MethodSig>) {
    LOGGER.info("[resolveAll]")
    val clock = Clock("ResolveAll")
    dIndex.resolve(entryClasses, entryPoints)
    clock.stop("dIndex.resolve()")
}

fun indexFieldsInSyntheticMethods() {
    LOGGER.info("[indexFieldsInSyntheticMethods]")
    for ((name, dlu) in DelayedLambdaUpdate.needingBridgeUpdate) {
        if (name in dIndex.constructableClasses) {
            dlu.indexFields()
        }
    }
}

fun calculateFieldOffsets() {
    LOGGER.info("[calculateFieldOffsets]")
    val usedFields = HashSet(dIndex.usedGetters)
    if (fieldsRWRequired) {
        usedFields.retainAll(dIndex.usedSetters)
    } else {
        usedFields.addAll(dIndex.usedSetters)
    }
    val nativeClasses = listOf("I", "F", "B", "S", "Z", "J", "C", "D")
    val fieldsByClass = usedFields.groupBy { it.clazz }
    for (clazz in fieldsByClass.keys.sortedByTopology {
        listSuperClasses(it, HashSet())
    }!!) {
        for (field in fieldsByClass[clazz]!!
            .sortedWith { a, b ->
                storageSize(b.descriptor).compareTo(storageSize(a.descriptor))
                    .ifSame { (a.clazz in nativeClasses).toInt().compareTo((b.clazz in nativeClasses).toInt()) }
                    .ifSame { a.name.compareTo(b.name) }
            }) { // sort by size && is-ptr | later we even could respect alignment and fill gaps where possible :)
            gIndex.getFieldOffset(field.clazz, field.name, field.descriptor, field.static)
        }
    }
    gIndex.lockFields = true
}

fun printInterfaceIndex() {
    if (printDebug) {
        LOGGER.info("[printInterfaceIndex]")
        val debugInfo = StringBuilder2()
        gIndex.interfaceIndex.entries.sortedBy { it.value }
            .forEach { (sig, index) ->
                debugInfo.append("[").append(index).append("]: ").append(sig).append("\n")
            }
        debugFolder.getChild("interfaceIndex.txt")
            .writeBytes(debugInfo.values, 0, debugInfo.size)
    }
}

fun assignNativeCode() {
    LOGGER.info("[assignNativeCode]")
    for ((method, code, noinline) in hIndex.annotations.entries
        .mapNotNull { m ->
            val wasm = m.value.firstOrNull { it.clazz == "annotations/WASM" }
            if (wasm != null)
                Triple(
                    m.key, wasm.properties["code"] as String,
                    m.value.any { it.clazz == "annotations/NoInline" })
            else null
        }) {
        if (noinline) {
            hIndex.wasmNative[method] = parseInlineWASM(code)
        } else {
            hIndex.inlined[method] = parseInlineWASM(code)
        }
    }
}

fun parseInlineWASM(code: String): List<Instruction> {
    return WATParser().parseExpression(code)
}

fun generateJavaScriptFile(missingMethods: HashSet<MethodSig>): Map<String, Pair<MethodSig, String>> {
    val jsImplemented = HashMap<String, Pair<MethodSig, String>>() // name -> sig, impl
    for (sig in dIndex.usedMethods) {
        val annot = hIndex.annotations[sig] ?: continue
        val js = annot.firstOrNull2 { it.clazz == "annotations/JavaScript" } ?: continue
        val code = js.properties["code"] as String
        missingMethods.remove(sig)
        for (name in methodNames(sig)) {
            jsImplemented[name] = Pair(sig, code)
            implementedMethods[name] = sig
        }
    }
    return jsImplemented
}

fun translateMethods(classesToLoad: List<String>, filterClass: (String) -> Boolean) {
    LOGGER.info("[translateMethods]")
    for (clazz in classesToLoad) {
        if (filterClass(clazz)) try {
            ClassReader(clazz).accept(
                ClassTranslator(clazz),
                ClassReader.EXPAND_FRAMES
            )
        } catch (e: FoundBetterReader) {
            try {
                e.reader.accept(
                    ClassTranslator(clazz),
                    ClassReader.EXPAND_FRAMES
                )
            } catch (e: IOException) {
                throw IOException(clazz, e)
            }
        } catch (e: IOException) {
            throw IOException(clazz, e)
        }
    }
}

fun buildSyntheticMethods() {
    LOGGER.info("[buildSyntheticMethods]")
    for ((name, dlu) in DelayedLambdaUpdate.needingBridgeUpdate) {
        if (name in dIndex.constructableClasses) {
            dlu.generateSyntheticMethod()
        }
    }
}

/**
 * going from parent classes to children, index all methods in gIndex
 * */
fun indexMethodsIntoGIndex(
    classesToLoad: List<String>,
    predefinedClasses: List<String>,
    filterClass: (String) -> Boolean
) {
    LOGGER.info("[indexMethodsIntoGIndex]")
    val dynIndex = createDynamicIndex(classesToLoad, filterClass)
    for (clazz in classesToLoad) {
        gIndex.getDynMethodIdx(clazz)
        val idx1 = dynIndex[clazz] ?: continue
        for (sig in idx1) {
            gIndex.getDynMethodIdx(clazz, sig.name, sig.descriptor)
        }
    }

    for (clazz in predefinedClasses) {
        if (!hIndex.isInterfaceClass(clazz)) {
            gIndex.getDynMethodIdx(clazz)
        }
    }

    gIndex.lockedDynIndex = true
}

fun ensureIndexForConstructableClasses() {
    LOGGER.info("[ensureIndexForConstructableClasses]")
    for (clazz in dIndex.constructableClasses) {
        val interfaces = hIndex.interfaces[clazz] ?: continue
        for (interfaceI in interfaces) {
            gIndex.getClassIndex(interfaceI)
        }
    }
}

/**
 * ensure all interfaces and super classes have their dedicated index
 * */
fun ensureIndexForInterfacesAndSuperClasses() {
    LOGGER.info("[ensureIndexForInterfacesAndSuperClasses]")
    var i = 0 // size could change
    while (i < gIndex.classNamesByIndex.size) {
        val clazzName = gIndex.classNamesByIndex[i++]
        val superClass = hIndex.superClass[clazzName]
        if (superClass != null) gIndex.getClassIndex(superClass)
        val interfaces = hIndex.interfaces[clazzName]
        if (interfaces != null) for (interfaceI in interfaces) {
            gIndex.getClassIndex(interfaceI)
        }
    }
}

fun findClassesToLoad(aliasedMethods: List<MethodSig>): List<String> {
    return (dIndex.usedMethods + aliasedMethods)
        .map { it.clazz }
        .filter { clazz ->
            clazz != INTERFACE_CALL_NAME &&
                    clazz !in hIndex.syntheticClasses &&
                    clazz !in hIndex.missingClasses
        }
        .toSet() // filter duplicates
        // super classes and interfaces first
        .sortedByTopology {
            listSuperClasses(it, HashSet())
        }!!
}

fun printAbstractMethods(bodyPrinter: StringBuilder2, missingMethods: HashSet<MethodSig>) {
    // todo we could (space-)optimize this and only create one method per call-signature
    LOGGER.info("[printAbstractMethods]")
    bodyPrinter.append(";; not implemented, abstract\n")
    for (func in dIndex.usedMethods
        .filter {
            it in hIndex.abstractMethods &&
                    !hIndex.isInterfaceClass(it.clazz) &&
                    it !in missingMethods
        }
        .sortedBy { methodName(it) }
    ) {
        val canThrow = canThrowError(func)
        val desc = func.descriptor
        val dx = desc.lastIndexOf(')')
        // export? no, nobody should call these
        bodyPrinter.append("(func $").append(methodName(func)).append(" (param")
        for (param in split1(desc.substring(1, dx))) {
            bodyPrinter.append(' ').append(jvm2wasm(param))
        }
        bodyPrinter.append(") (result")
        val hasReturnType = !desc.endsWith(")V")
        if (hasReturnType) bodyPrinter.append(' ').append(jvm2wasm1(desc[dx + 1]))
        if (canThrow) bodyPrinter.append(' ').append(ptrType)
        bodyPrinter.append(")")
        if (hasReturnType || canThrow) {
            if (hasReturnType) bodyPrinter.append(" ").append(jvm2wasm1(desc[dx + 1])).append(".const 0")
            if (canThrow) bodyPrinter.append(" call \$throwAME")
        }
        bodyPrinter.append(")\n")
    }
}

val imports = ArrayList<Import>()

fun printForbiddenMethods(importPrinter: StringBuilder2, missingMethods: HashSet<MethodSig>) {
    LOGGER.info("[printForbiddenMethods]")
    importPrinter.append(";; forbidden\n")
    loop@ for (sig in dIndex.methodsWithForbiddenDependencies
        .filter { it in dIndex.usedMethods }
        .sortedBy { methodName(it) }) {
        val name = methodName(sig)
        var sig2: MethodSig
        while (true) {
            sig2 = hIndex.methodAliases[name] ?: sig
            if (sig2 != sig) {
                if (sig2 !in dIndex.usedMethods) {
                    throw NotImplementedError("$sig was used, but $sig2 wasn't")
                }
                continue@loop
            } else break
        }
        importPrinter.import2(sig)
        if (!missingMethods.add(sig))
            throw IllegalStateException()
    }
}

fun printNotImplementedMethods(importPrinter: StringBuilder2, missingMethods: HashSet<MethodSig>) {
    LOGGER.info("[printNotImplementedMethods]")
    importPrinter.append(";; not implemented, not forbidden\n")
    for (sig in dIndex.usedMethods
        .filter {
            it !in hIndex.abstractMethods &&
                    // it in hIndex.hasSuperMaybeMethods && // ???
                    it !in dIndex.methodsWithForbiddenDependencies &&
                    it !in hIndex.jvmImplementedMethods
        }
        .sortedBy { methodName(it) }) {
        val superMethod = dIndex.findSuperMethod(sig)
        if (superMethod != null) {
            if (sig != superMethod) {
                hIndex.setAlias(sig, superMethod)
            }
        } else {
            if (hIndex.isInterfaceClass(sig.clazz)) {
                // println("Skipping $sig")
                continue
            }
            LOGGER.info("Importing $sig, because super is null")
            importPrinter.import2(sig)
            if (!missingMethods.add(sig))
                throw IllegalStateException()
        }
    }
}

fun printNativeMethods(importPrinter: StringBuilder2, missingMethods: HashSet<MethodSig>) {
    LOGGER.info("[printNativeMethods]")
    importPrinter.append(";; not implemented, native\n")
    for (sig in dIndex.usedMethods
        .filter {
            it in hIndex.nativeMethods &&
                    it !in missingMethods &&
                    it !in hIndex.jvmImplementedMethods
        }
        .sortedBy { methodName(it) }) {
        importPrinter.import2(sig)
        if (!missingMethods.add(sig))
            throw IllegalStateException()
    }
}

/**
 * To get stuff working quickly, just let all missing implementations return 0 until they have been implemented.
 * todo make this optional, we might wanna fail instead
 * */
fun createPseudoJSImplementations(
    jsImplemented: Map<String, *>,
    missingMethods: Set<MethodSig>
): Map<String, MethodSig> {
    val jsPseudoImplemented = HashMap<String, MethodSig>()
    for (sig in missingMethods) {
        // if there is a WASM implementation, we don't need a JS implementation
        if ((hIndex.annotations[sig] ?: emptyList()).any { it.clazz == "annotations/WASM" }) continue
        val name = methodName(sig)
        if (name in jsImplemented) continue
        jsPseudoImplemented[name] = sig
        implementedMethods[name] = sig
    }
    return jsPseudoImplemented
}

/**
 * create the dynamic index for the gIndex methods
 * */
fun createDynamicIndex(classesToLoad: List<String>, filterClass: (String) -> Boolean): Map<String, HashSet<MethodSig>> {
    val dynIndex = HashMap<String, HashSet<MethodSig>>()
    for (clazz in classesToLoad) {
        if (filterClass(clazz)) try {
            ClassReader(clazz).accept(
                object : ClassVisitor(api) {
                    override fun visitMethod(
                        access: Int,
                        name: String,
                        descriptor: String,
                        signature: String?,
                        exceptions: Array<out String>?
                    ): MethodVisitor? {
                        val isStatic = access.hasFlag(ACC_STATIC)
                        val sig1 = MethodSig.c(clazz, name, descriptor, isStatic)
                        val map = hIndex.getAlias(sig1)
                        return if (sig1 !in dIndex.methodsWithForbiddenDependencies &&
                            sig1 in dIndex.usedMethods &&
                            map == sig1
                        ) {
                            object : MethodVisitor(api) {

                                override fun visitInvokeDynamicInsn(
                                    name: String?,
                                    descriptor: String?,
                                    bootstrapMethodHandle: Handle?,
                                    vararg args: Any?
                                ) {
                                    val dst = args[1] as Handle
                                    val synthClassName = getSynthClassName(sig1, dst)
                                    // println("lambda: $sig1 -> $synthClassName")
                                    gIndex.getClassIndex(synthClassName) // add class to index
                                    val calledMethod = MethodSig.c(dst.owner, dst.name, dst.desc, false)
                                    dynIndex.getOrPut(calledMethod.clazz) { HashSet() }.add(calledMethod)
                                }

                                override fun visitTypeInsn(opcode: Int, type: String) {
                                    gIndex.getClassIndex(replaceClass(type)) // add class to index
                                }

                                override fun visitTryCatchBlock(
                                    start: Label,
                                    end: Label,
                                    handler: Label,
                                    type: String?
                                ) {
                                    if (type != null) {
                                        gIndex.getClassIndex(replaceClass(type)) // add class to index
                                    }
                                }

                                override fun visitLdcInsn(value: Any?) {
                                    if (value is Type) { // add class to index
                                        gIndex.getClassIndex(replaceClass(single(value.descriptor)))
                                    }
                                }

                                override fun visitMethodInsn(
                                    opcode: Int,
                                    owner0: String,
                                    name: String,
                                    descriptor: String,
                                    isInterface: Boolean
                                ) {
                                    if (opcode == 0xb6) { // invoke virtual
                                        val owner = replaceClass(owner0)
                                        val sig0 = MethodSig.c(owner, name, descriptor, false)
                                        // just for checking if abstract
                                        if (sig0 !in hIndex.finalMethods) {
                                            // check if method is defined in parent class
                                            fun add(clazz: String) {
                                                val superClass = hIndex.superClass[clazz]
                                                if (superClass != null &&
                                                    MethodSig.c(
                                                        superClass, name, descriptor, false
                                                    ) in hIndex.methodsByClass[superClass]!!
                                                ) {
                                                    add(superClass)
                                                } else {
                                                    dynIndex.getOrPut(clazz) { HashSet() }.add(sig0)
                                                }
                                            }
                                            add(owner)
                                        }
                                    }
                                }
                            }
                        } else null
                    }
                }, 0 // no stack-frames are needed
            )
        } catch (e: IOException) {
            throw IOException(clazz, e)
        }
    }
    return dynIndex
}

val helperFunctions = HashMap<String, FunctionImpl>()

fun printMethodImplementations(bodyPrinter: StringBuilder2, usedMethods: Set<String>) {
    LOGGER.info("[printMethodImplementations]")
    for ((sig, impl) in gIndex.translatedMethods
        .entries.sortedBy { it.value.funcName }) {
        val name = methodName(sig)
        // not truly used, even tho marked as such...
        if (name in usedMethods) {
            bodyPrinter.append(impl)
        } else if (!name.startsWith("new_") && !name.startsWith("static_") &&
            sig !in hIndex.getterMethods && sig !in hIndex.setterMethods
        ) {
            LOGGER.warn("Not actually used: $name")
        }// else we don't care we didn't use it
    }
    for (impl in helperFunctions
        .values.sortedBy { it.funcName }) {
        bodyPrinter.append(impl)
    }
}