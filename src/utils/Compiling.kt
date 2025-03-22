package utils

import api
import canThrowError
import cannotThrow
import dIndex
import fieldsRWRequired
import gIndex
import hIndex
import hierarchy.Annota
import hierarchy.DelayedLambdaUpdate
import hierarchy.DelayedLambdaUpdate.Companion.getSynthClassName
import hierarchy.FirstClassIndexer
import hierarchy.FirstClassIndexer.Companion.readType
import hierarchy.HierarchyIndex.getAlias
import implementedMethods
import listEntryPoints
import listLibrary
import listSuperClasses
import me.anno.utils.Clock
import me.anno.utils.assertions.assertFail
import me.anno.utils.assertions.assertNotEquals
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.Compare.ifSame
import me.anno.utils.structures.lists.Lists.any2
import me.anno.utils.structures.lists.Lists.sortedByTopology
import me.anno.utils.types.Booleans.toInt
import org.apache.logging.log4j.LogManager
import org.objectweb.asm.*
import replaceClass
import resolvedMethods
import translator.ClassTranslator
import translator.FoundBetterReader
import translator.MethodTranslator
import utils.CommonInstructions.INVOKE_VIRTUAL
import utils.MethodResolver.resolveMethod
import wasm.instr.Instruction
import wasm.parser.FunctionImpl
import wasm.parser.Import
import wasm.parser.WATParser
import java.io.IOException

private val LOGGER = LogManager.getLogger("Compiling")


fun indexHierarchyFromEntryPoints() {
    listEntryPoints { clazz ->
        if (hIndex.doneClasses.add(clazz)) {
            ClassReader(clazz)
                .accept(FirstClassIndexer(clazz), 0)
        }
    }

    listLibrary { clazz ->
        if (hIndex.doneClasses.add(clazz)) {
            try {
                ClassReader(clazz)
                    .accept(FirstClassIndexer(clazz), 0)
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
        val ownGenerics = hIndex.genericsByClass[clazz]
        for (superType in superTypes) {

            // todo map all its generic methods
            val clearSuperType = Descriptor.parseType(descWithoutGenerics(superType))
            val generics = hIndex.genericsByClass[clearSuperType]

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
                ?.filter { hIndex.isAbstract(it) && it in hIndex.genericMethodSignatures }
                ?: continue

            for (method in abstractMethods) {

                // first test, that there is valid candidates?
                // same name, same number of params, not abstract
                val types = genericsTypes(method) // abstract method cannot be static
                val candidates = baseMethods.filter {
                    it.name == method.name &&
                            it.descriptor != method.descriptor &&
                            !hIndex.isAbstract(it) &&
                            // test for same number of arguments & same-typy return type
                            genericsTypes(it) == types
                }
                if (candidates.isNotEmpty()) {

                    // remove generics from call (for now)
                    val desc2 = hIndex.genericMethodSignatures[method]!!
                    val desc2i = descWithoutGenerics(desc2)
                    // if so, no base-type generics are present -> nothing needs to be replaced or mapped
                    if (method.descriptor.raw == desc2i) continue

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

                    val mappedParams = params.mapNotNull { rawType ->
                        val rawType1 = if (rawType[0] == 'T') {
                            (generics.firstOrNull { it.name == rawType }
                                ?: ownGenerics?.firstOrNull { it.name == rawType }
                                    // ?: throw NullPointerException("Didn't find mapping for $p, own generics: $ownGenerics")
                                    )?.superClass
                        } else rawType
                        // todo support and propagate generic parameters (?)
                        //  we need to convert our classes to actual types then
                        val rawType2 = if (rawType1 != null && '<' in rawType1) {
                            rawType1.substring(0, rawType1.indexOf('<')) + ";"
                        } else rawType1
                        if (rawType2 != null) Descriptor.parseType(rawType2) else null
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

                    val genericsMap = generics.indices
                        .associate { idx ->
                            val name = generics[idx].name
                            assertTrue(name.startsWith('T'))
                            assertTrue(name.endsWith(';'))
                            val name1 = name.substring(1, name.lastIndex) // strip T and ;
                            name1 to mappedParams[idx]
                        }

                    val newDesc = Descriptor.parseDescriptor(desc2i, genericsMap)

                    fun matchesDescriptor0(candidate: String, expected: String): Boolean {
                        if (candidate == expected) return true
                        if (candidate.startsWith("[") != expected.startsWith("[")) return false
                        if (candidate.startsWith("["))
                            return matchesDescriptor0(candidate.substring(1), expected.substring(1))
                        // candidate is allowed to be a child of expected
                        return hIndex.isChildClassOf(candidate, expected)
                    }

                    fun matchesDescriptor(candidate: String?, expected: String?): Boolean {
                        if (candidate == expected) return true
                        if ((candidate == null) != (expected == null)) return false
                        return matchesDescriptor0(candidate!!, expected!!)
                    }

                    fun matchesDescriptor(candidate: Descriptor, expected: Descriptor): Boolean {
                        if (candidate.params.size != newDesc.params.size) return false
                        for (k in candidate.params.indices) {
                            if (!matchesDescriptor(candidate.params[k], expected.params[k])) {
                                return false
                            }
                        }
                        return matchesDescriptor(candidate.returnType, expected.returnType)
                    }

                    // check if this newly generated signature is implemented
                    val implMethod =
                        candidates.firstOrNull { it.descriptor == newDesc }
                            ?: candidates.firstOrNull { matchesDescriptor(it.descriptor, newDesc) }

                    if (implMethod != null) {
                        // define mapping
                        val sig2 = method.withClass(clazz)
                        assertNotEquals(sig2, implMethod)
                        hIndex.setAlias(sig2, implMethod)
                    } else {
                        LOGGER.warn(
                            "Missing mapping for $method\n" +
                                    "  class: $clazz\n" +
                                    "  generics: $genericsMap\n" +
                                    "  superType: $superType\n" +
                                    "  params: $params\n" +
                                    "  candidates: $candidates\n" +
                                    "  newDesc: $newDesc"
                        )
                    }
                }
            }
        }
    }
}

fun findNoThrowMethods() {
    LOGGER.info("[findNoThrowMethods]")
    for ((sig, annotations) in hIndex.annotations) {
        if (annotations.any2 { it.clazz == Annotations.NO_THROW }) {
            cannotThrow.add(methodName(sig))
        }
    }
}

fun findAliases() {
    LOGGER.info("[findAliases]")
    for ((sig, annotations) in hIndex.annotations) {
        val alias = annotations.firstOrNull { it.clazz == Annotations.ALIAS }
        if (alias != null) findAlias(sig, alias)
        val revAlias = annotations.firstOrNull { it.clazz == Annotations.REV_ALIAS }
        if (revAlias != null) findRevAlias(sig, revAlias)
    }
}

private fun findAlias(sig: MethodSig, alias: Annota) {
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

private fun findRevAlias(sig: MethodSig, revAlias: Annota) {
    val aliasName = revAlias.properties["name"] as String
    if (aliasName.startsWith('$')) throw IllegalStateException("alias $aliasName must not start with dollar symbol")
    if (aliasName.contains('/')) throw IllegalStateException("alias $aliasName must not contain slashes, but underscores")
    if (aliasName.contains('(')) throw IllegalStateException("alias $aliasName must not contain slashes, but underscores")
    val sig1 = hIndex.methodByName[aliasName]
    if (sig1 != null) {
        hIndex.setAlias(sig, sig1)
    } else LOGGER.info("Skipped $sig -> $aliasName, because unknown")
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
        if (a.any2 { it.clazz == Annotations.EXPORT_CLASS }) {
            hIndex.exportedMethods.add(sig)
        }
    }
}

fun replaceRenamedDependencies(clock: Clock) {
    LOGGER.info("[replaceRenamedDependencies]")
    fillInDependenciesForAliases()
    clock.stop("fillInDependenciesForAliases")
    optimizeDependenciesUsingResolvedMethods()
    clock.stop("optimizeDependenciesUsingResolvedMethods")
}

fun fillInDependenciesForAliases() {
    // replace dependencies to get rid of things
    val methodNameToSig = hIndex.methodsByClass
        .map { it.value }.flatten()
        .associateBy(::methodName2)
    for ((methodName, newImpl) in hIndex.methodAliases) {
        val originalImpl = methodNameToSig[methodName] ?: continue
        copyProperty(originalImpl, newImpl, dIndex.methodDependencies)
        copyProperty(originalImpl, newImpl, dIndex.getterDependencies)
        copyProperty(originalImpl, newImpl, dIndex.setterDependencies)
        copyProperty(originalImpl, newImpl, dIndex.constructorDependencies)
        copyProperty(originalImpl, newImpl, dIndex.interfaceDependencies)
    }
}

private fun <V> copyProperty(dst: MethodSig, src: MethodSig, map: HashMap<MethodSig, V>) {
    map[dst] = map[src] ?: return
}

fun optimizeDependenciesUsingResolvedMethods() {
    // resolve a lot of methods for better dependencies
    for (dependencies in dIndex.methodDependencies.values) {
        val newDependencies = dependencies.map { sig ->
            resolvedMethods.getOrPut(sig) {
                val found = resolveMethod(sig, false) ?: sig
                if (found != sig) hIndex.setAlias(sig, found)
                if (hIndex.isStatic(found) == hIndex.isStatic(sig)) found else sig
            }
        }
        dependencies.clear()
        dependencies.addAll(newDependencies)
    }
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
    dIndex.resolve(entryClasses, entryPoints)
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
            gIndex.getFieldOffset(field.clazz, field.name, field.descriptor, field.isStatic)
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

fun parseInlineWASM() {
    LOGGER.info("[assignNativeCode]")
    for ((method, code, noinline) in hIndex.annotations.entries
        .mapNotNull { (method, annotations) ->
            val wasm = annotations.firstOrNull { it.clazz == Annotations.WASM }
            if (wasm != null)
                Triple(
                    method, wasm.properties["code"] as String,
                    annotations.any { it.clazz == Annotations.NO_INLINE })
            else null
        }) {
        if (noinline) {
            hIndex.wasmNative[method] = parseInlineWASM(code)
        } else {
            hIndex.inlined[method] = parseInlineWASM(code)
        }
    }
}

private fun parseInlineWASM(code: String): List<Instruction> {
    return WATParser().parseExpression(code)
}

fun generateJavaScriptFile(missingMethods: HashSet<MethodSig>): Map<String, Pair<MethodSig, String>> {
    val jsImplemented = HashMap<String, Pair<MethodSig, String>>() // name -> sig, impl
    for (sig in dIndex.usedMethods) {
        val js = hIndex.getAnnotation(sig, Annotations.JAVASCRIPT) ?: continue
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
    if (MethodTranslator.comments) bodyPrinter.append(";; not implemented, abstract\n")
    for (func in dIndex.usedMethods
        .filter {
            hIndex.isAbstract(it) &&
                    !hIndex.isInterfaceClass(it.clazz) &&
                    it !in missingMethods
        }
        .sortedBy { methodName(it) }
    ) {
        val canThrow = canThrowError(func)
        val desc = func.descriptor
        // export? no, nobody should call these
        bodyPrinter.append("(func $").append(methodName(func)).append(" (param")
        for (param in desc.wasmParams) {
            bodyPrinter.append(' ').append(param)
        }
        bodyPrinter.append(") (result")
        val returnType = desc.returnType
        val hasReturnType = returnType != null
        if (hasReturnType) bodyPrinter.append(' ').append(jvm2wasmTyped(returnType!!))
        if (canThrow) bodyPrinter.append(' ').append(ptrType)
        bodyPrinter.append(")")
        if (hasReturnType || canThrow) {
            if (hasReturnType) bodyPrinter.append(" ").append(jvm2wasmTyped(returnType!!)).append(".const 0")
            if (canThrow) bodyPrinter.append(" call \$throwAME")
        }
        bodyPrinter.append(")\n")
    }
}

val imports = ArrayList<Import>()

fun printForbiddenMethods(importPrinter: StringBuilder2, missingMethods: HashSet<MethodSig>) {
    LOGGER.info("[printForbiddenMethods]")
    if (MethodTranslator.comments) importPrinter.append(";; forbidden\n")
    val forbidden = dIndex.methodsWithForbiddenDependencies
        .filter { it in dIndex.usedMethods && getAlias(it) == it }
        .sortedBy { methodName(it) }
    for (sig in forbidden) {
        importPrinter.import2(sig)
        if (!missingMethods.add(sig))
            throw IllegalStateException()
    }
}

fun printNotImplementedMethods(importPrinter: StringBuilder2, missingMethods: HashSet<MethodSig>) {
    LOGGER.info("[printNotImplementedMethods]")
    if (MethodTranslator.comments) importPrinter.append(";; not implemented, not forbidden\n")
    for (sig in dIndex.usedMethods
        .filter {
            !hIndex.isAbstract(it) &&
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
    if (MethodTranslator.comments) importPrinter.append(";; not implemented, native\n")
    for (sig in dIndex.usedMethods
        .filter {
            hIndex.isNative(it) &&
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
        if (hIndex.hasAnnotation(sig, Annotations.WASM)) continue
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
                        val sig1 = MethodSig.c(clazz, name, descriptor)
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
                                    val calledMethod = MethodSig.c(dst.owner, dst.name, dst.desc)
                                    dynIndex.getOrPut(calledMethod.clazz) { HashSet() }.add(calledMethod)
                                }

                                override fun visitTypeInsn(opcode: Int, type: String) {
                                    gIndex.getClassIndex(Descriptor.parseTypeMixed(type)) // add class to index
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
                                        gIndex.getClassIndex(Descriptor.parseType(value.descriptor))
                                    }
                                }

                                override fun visitMethodInsn(
                                    opcode: Int,
                                    owner0: String,
                                    name: String,
                                    descriptor: String,
                                    isInterface: Boolean
                                ) {
                                    if (opcode == INVOKE_VIRTUAL) { // invoke virtual
                                        val owner = Descriptor.parseTypeMixed(owner0)
                                        val sig0 = MethodSig.c(owner, name, descriptor)
                                        // just for checking if abstract
                                        if (!hIndex.isFinal(sig0)) {
                                            // check if method is defined in parent class
                                            fun add(clazz: String) {
                                                val superClass = hIndex.superClass[clazz]
                                                if (superClass != null &&
                                                    MethodSig.c(
                                                        superClass, name, descriptor
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
    val notActuallyUsed = ArrayList<String>()
    for ((sig, impl) in gIndex.translatedMethods
        .entries.sortedBy { it.value.funcName }) {
        val name = methodName(sig)
        // not truly used, even tho marked as such...
        if (name in usedMethods) {
            bodyPrinter.append(impl)
        } else if (!name.startsWith("new_") && !name.startsWith("static_") &&
            sig !in hIndex.getterMethods && sig !in hIndex.setterMethods
        ) {
            notActuallyUsed.add(name)
        }// else we don't care we didn't use it
    }
    if (notActuallyUsed.isNotEmpty()) {
        LOGGER.warn("Not actually used, #${notActuallyUsed.size}: ${notActuallyUsed.joinToString(", ")}")
    }
    for (impl in helperFunctions
        .values.sortedBy { it.funcName }) {
        bodyPrinter.append(impl)
    }
}