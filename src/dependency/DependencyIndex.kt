package dependency

import fieldsRWRequired
import gIndex
import hIndex
import me.anno.utils.types.Booleans.hasFlag
import resolvedMethods
import utils.FieldSig
import utils.MethodSig
import utils.findMethod
import utils.printUsed

object DependencyIndex {

    val cap = 4096
    val cap2 = 256

    val fieldDependenciesR = HashMap<MethodSig, Set<FieldSig>>(cap)
    val fieldDependenciesW = HashMap<MethodSig, Set<FieldSig>>(cap)
    var methodDependencies = HashMap<MethodSig, HashSet<MethodSig>>(cap)
    val constructorDependencies = HashMap<MethodSig, Set<String>>(cap)
    var methodsWithForbiddenDependencies = HashSet<MethodSig>(cap2)
    val interfaceDependencies = HashMap<MethodSig, HashSet<MethodSig>>(cap2) // pseudo-signature
    val knownInterfaceDependencies = HashSet<MethodSig>(cap2)

    val usedMethods = HashSet<MethodSig>(cap)
    val usedFieldsR = HashSet<FieldSig>(cap)
    val usedFieldsW = HashSet<FieldSig>(cap)
    val usedInterfaceCalls = HashSet<MethodSig>(cap2)

    fun findSuperMethod(method: MethodSig): MethodSig? {
        return findSuperMethod0(method) ?: findSuperMethod1(method)
    }

    private fun findSuperMethod0(method: MethodSig): MethodSig? {
        // println("Looking for $method3")
        if (method in hIndex.jvmImplementedMethods || method in hIndex.customImplementedMethods) {
            return method
        }
        val aliased = hIndex.getAlias(method)
        if (aliased != method) {
            return aliased
        }

        // check super class
        val superClass = hIndex.superClass[method.clazz]
        val bySuper = if (superClass != null) findSuperMethod(method.withClass(superClass)) else null
        if (bySuper != null) return bySuper

        // check interfaces for default-implementations
        val interfaces = hIndex.interfaces[method.clazz]
        if (interfaces != null) for (interfaceI in interfaces) {
            for (method2 in hIndex.methods[interfaceI]!!) {
                if (method2.name == method.name && method2.descriptor == method.descriptor &&
                    method2 !in hIndex.notImplementedMethods
                ) return method2
            }
        }
        return null
    }

    private fun findSuperMethod1(method: MethodSig): MethodSig? {
        val superClass = hIndex.superClass[method.clazz] ?: return null
        val methodI = method.withClass(superClass)
        if (methodI in (hIndex.methods[superClass] ?: emptySet())) {
            // if present in parent, return parent method
            return findSuperMethod1(methodI) ?: methodI
        }
        // not present in parent -> we must map ours
        return null
    }

    lateinit var constructableClasses: HashSet<String>

    fun resolve(
        entryClasses: Set<String>,
        entryPoints: Set<MethodSig>,
        forbidden: (clazzName: String) -> Boolean
    ) {

        val size = methodDependencies.size

        val usedByClass = HashMap<String, HashSet<MethodSig>>(size / 8) // 23s -> 13s ❤

        constructableClasses = HashSet(size)
        for (i in constructableClasses.indices) {
            constructableClasses.add(gIndex.classNames[i])
        }
        constructableClasses.addAll(
            listOf(
                "java/lang/Number",
                "java/lang/Integer",
                "java/lang/Long",
                "java/lang/Float",
                "java/lang/Double",
                "java/lang/Short",
                "java/lang/Character",
                "java/lang/Byte",
                "java/lang/Boolean",
                "java/lang/Class",
                "java/lang/reflect/Field",
                "java/lang/reflect/AccessibleObject",// super class of java.lang.reflect.Field
                "java/lang/reflect/Constructor", // needed for Class.newInstance()
                "[]", "[I", "[F", "[Z", "[B", "[C", "[S", "[J", "[D",
                "int", "float", "boolean", "byte", "short", "char", "long", "double",
            )
        )
        constructableClasses.addAll(entryClasses)

        var remaining = HashSet<MethodSig>(size)
        var newRemaining = HashSet<MethodSig>(size)

        remaining.addAll(entryPoints)
        remaining.addAll(usedMethods)

        for ((ci, fo) in gIndex.fieldOffsets) {
            if (ci.hasFlag(1) && fo.hasFields()) { // static
                remaining.add(MethodSig.c(gIndex.classNames[ci shr 1], "<clinit>", "()V"))
            }
        }

        methodsWithForbiddenDependencies = HashSet(size)
        val depsIfConstructable = HashMap<String, HashSet<MethodSig>>(size)

        usedMethods.clear()

        val usedMethods = HashSet<MethodSig>(64)

        fun used(sig: MethodSig): Boolean {
            return sig in usedMethods || sig in newRemaining || sig in remaining || sig in this.usedMethods
        }

        fun handleBecomingConstructable(sig: MethodSig, clazz: String, newUsedMethods: MutableSet<MethodSig>) {
            if (constructableClasses.add(clazz)) {
                // println("$clazz becomes constructible")

                // val print = clazz == "kotlin/jvm/internal/PropertyReference1Impl"
                // if (print) println("$sig -> $clazz")

                // check for all interfaces, whether we should implement their functions
                fun handleInterfaces(clazz1: String) {
                    val interfaces2 = hIndex.interfaces[clazz1]
                    if (interfaces2 != null) for (interface1 in interfaces2) {
                        handleBecomingConstructable(sig, interface1, newUsedMethods)
                        handleInterfaces(interface1)
                        val methods = hIndex.methods[interface1] ?: continue
                        for (method2 in methods) {
                            if (used(method2)) {
                                newUsedMethods.add(MethodSig.c(clazz, method2.name, method2.descriptor))
                            }
                        }
                    }
                    val superClass1 = hIndex.superClass[clazz1]
                    if (superClass1 != null) handleInterfaces(superClass1)
                }
                handleInterfaces(clazz)

                val superClass = hIndex.superClass[clazz]
                if (superClass != null) handleBecomingConstructable(sig, superClass, newUsedMethods)

                val dependenciesByConstructable = depsIfConstructable.remove(clazz) ?: emptySet()
                // println("  dependencies by constructable[$clazz]: $dependenciesByConstructable")
                newRemaining.addAll(dependenciesByConstructable)

                // of all super classes, depend on all their relevant methods
                fun handleSuper(superClass: String) {
                    // if (clazz == "java/util/Collections\$SetFromMap") println("processing $superClass")
                    val superMethods = usedByClass[superClass]
                    if (superMethods != null) {
                        for (method2 in superMethods) {
                            val childMethod = MethodSig.c(clazz, method2.name, method2.descriptor)
                            if (method2 !in methodsWithForbiddenDependencies) {
                                // if (clazz == "java/util/Collections\$SetFromMap") println("  marked $childMethod for use")
                                newUsedMethods.add(childMethod)
                            }
                        }
                    }
                    handleSuper(hIndex.superClass[superClass] ?: return)
                }
                handleSuper(clazz)
            }
        }

        val dependencies = HashSet<MethodSig>(64)

        fun checkState() {
            /*val sig0 = MethodSig.c("java/lang/Object", "hashCode", "()I")
            val sig1 = MethodSig.c("jvm/JavaLang", "Object_hashCode", "(Ljava/lang/Object;)I")
            if (!used(sig0) && used(sig1)) {
                printUsed(sig0)
                printUsed(sig1)
                throw IllegalStateException()
            }
            val sig2 = MethodSig.c(
                "kotlin/reflect/KProperty1", "get",
                "(Ljava_lang_Object;)Ljava_lang_Object;"
            )
            val sig3 = MethodSig.c(
                "kotlin/jvm/internal/PropertyReference1", "get",
                "(Ljava_lang_Object;)Ljava_lang_Object;"
            )
            if (false) if (used(sig2) && !used(sig3) && sig3.clazz in constructableClasses) {
                printUsed(sig2)
                printUsed(sig3)
                throw IllegalStateException()
            }
            val sig4 = MethodSig.c(
                "kotlin/jvm/internal/PropertyReference1", "invoke",
                "(Ljava_lang_Object;)Ljava_lang_Object;"
            )
            if (!used(sig4) && sig4.clazz in constructableClasses && isUsedAsInterface(sig4) != null) {
                printUsed(sig4)
                throw IllegalStateException("If used as interface, must be used")
            }
            val sig5 = MethodSig.c(
                "kotlin/reflect/KProperty1", "invoke",
                "(Ljava_lang_Object;)Ljava_lang_Object;"
            )
            if (!used(sig5) && sig5.clazz in constructableClasses && isUsedAsInterface(sig5) != null) {
                printUsed(sig5)
                throw IllegalStateException("If used as interface, must be used")
            }
            val sig6 = MethodSig.c(
                "kotlin/jvm/functions/Function1", "invoke",
                "(Ljava_lang_Object;)Ljava_lang_Object;"
            )
            if (isUsedAsInterface(sig6) != null) {
                fun checkChildClass(clazz: String) {
                    if (clazz in constructableClasses) return
                    if (sig6.withClass(clazz) !in (depsIfConstructable[clazz] ?: emptySet())) {
                        printUsed(sig6)
                        printUsed(sig6.withClass(clazz))
                        throw IllegalStateException("If used as interface, depsIfConstrutable must be filled")
                    }
                    // check all child classes
                    for (child in hIndex.childClasses[clazz] ?: emptySet()) {
                        checkChildClass(child)
                    }
                }
                checkChildClass(sig6.clazz)
            }*/
        }

        fun checkState(dependencies: Set<MethodSig>, cud: Boolean = false) {
            checkState()
        }

        checkState(emptySet(), true)


        do {
            newRemaining.clear()
            for (sig in remaining) {
                if (this.usedMethods.add(sig)) {
                    if (
                    // sig.name == "openMenuComplex" ||
                    // sig.name == "openMenu" ||
                    // sig.name == "ask" ||
                    // sig.name == "openMenuByPanels"
                    // sig.name == "clone" && sig.clazz != "[]" ||
                    // sig.name == "copy" ||
                        false
                    ) {
                        printUsed(sig)
                        throw IllegalStateException()
                    }

                    // println("[+dep] $sig")

                    dependencies.clear()

                    checkState(dependencies)

                    if (sig !in hIndex.staticMethods) {
                        usedByClass.getOrPut(sig.clazz) { HashSet() }.add(sig)
                    }

                    checkState(dependencies)

                    fun handleChildImplementations(clazz: String, dependencies: HashSet<MethodSig>) {
                        for (cc in hIndex.childClasses[clazz] ?: return) {
                            val print = false // cc in constructableClasses || cc == "java/lang/reflect/Field"
                            if (print) println("  | child: $cc, constructable? ${cc in constructableClasses}")
                            handleChildImplementations(cc, dependencies)
                            if (cc in constructableClasses) {
                                val sig2 = sig.withClass(cc)
                                val sig3 = resolvedMethods[sig2] ?: sig2
                                if (print) {
                                    if (sig3 != sig2) println("    $sig2 -> $sig3")
                                    else println("    $sig2")
                                }
                                dependencies.add(sig2) // not really useful, just for correctness checking
                                dependencies.add(sig3)
                            }
                        }
                    }

                    checkState(dependencies)

                    fun processDependencies(dependencies: Set<MethodSig>) {
                        for (m in dependencies) {
                            if (m.name == "<init>" || m in hIndex.staticMethods || m.clazz in constructableClasses) {
                                if (m !in this.usedMethods) newRemaining.add(m)
                            } else {
                                depsIfConstructable.getOrPut(m.clazz) { HashSet() }.add(m)
                            }
                        }
                    }

                    checkState(dependencies)

                    val alias = hIndex.getAlias(sig)
                    // println("[alias] $sig -> $name -> $alias")
                    if (alias != sig) {
                        methodDependencies[sig] = methodDependencies[alias] ?: hashSetOf()
                        fieldDependenciesR[sig] = fieldDependenciesR[alias] ?: emptySet()
                        fieldDependenciesW[sig] = fieldDependenciesW[alias] ?: emptySet()
                        constructorDependencies[sig] = constructorDependencies[alias] ?: emptySet()
                        interfaceDependencies[sig] = interfaceDependencies[alias] ?: HashSet()
                        hIndex.setAlias(sig, alias)
                        newRemaining.add(alias)
                        // methodsWithForbiddenDependencies.add(sig) // why???
                        handleChildImplementations(sig.clazz, dependencies)
                        processDependencies(dependencies)
                        continue
                    }

                    checkState(dependencies)

                    if (sig.name != "<init>" && sig.name != "<clinit>") {

                        if (sig !in hIndex.staticMethods && sig.clazz !in constructableClasses)
                            throw IllegalStateException("$sig is being used without class being constructable")

                        val resolved = findMethod(sig.clazz, sig)
                        if (resolved != null && resolved != sig) {
                            if (resolved !in this.usedMethods) newRemaining.add(resolved)
                            continue
                        }
                    }

                    checkState(dependencies)

                    val md = methodDependencies[sig]
                    // println("[/dep] $sig -> $md")
                    when {
                        md != null -> {
                            dependencies.addAll(md)
                        }
                        sig.name == "<clinit>" -> {
                            // we don't really care
                            // println("warn: $method is missing (dIndex)")
                        }
                        sig.clazz == "?" -> {
                            // link all constructable implementations
                            for (m in hIndex.methods) {
                                if (m.key in constructableClasses) {
                                    val map =
                                        m.value.firstOrNull { it.name == sig.name && it.descriptor == sig.descriptor }
                                    if (map != null) dependencies.add(map)
                                }
                            }
                        }
                        else -> {

                            // check if method is defined in super class
                            fun searchClass(clazz: String): Set<MethodSig>? {
                                val method3 = MethodSig.c(clazz, sig.name, sig.descriptor)
                                val dep = methodDependencies[method3]
                                if (dep != null) return dep + method3
                                // check interfaces
                                val interfaces = hIndex.interfaces[clazz]
                                if (interfaces != null) for (interfaceI in interfaces) {
                                    for (method2 in hIndex.methods[interfaceI]!!) {
                                        if (method2.name == sig.name && method2.descriptor == sig.descriptor &&
                                            method2 !in hIndex.notImplementedMethods
                                        ) return setOf(method2)
                                    }
                                }
                                // check super class
                                val superClass = hIndex.superClass[clazz]
                                return if (superClass != null) searchClass(superClass) else null
                            }

                            val map = searchClass(sig.clazz)
                            if (map != null) dependencies.addAll(map)

                        }
                    }

                    usedMethods.clear()
                    usedMethods.addAll(dependencies)

                    checkState(dependencies)

                    val usedInterfaces = interfaceDependencies[sig]
                    if (usedInterfaces != null) {
                        for (usedInterface in usedInterfaces) {
                            // println("adding used-as-interface: $usedInterface")
                            if (usedInterfaceCalls.add(usedInterface)) {
                                handleBecomingConstructable(sig, usedInterface.clazz, dependencies)
                                val interfaces = hIndex.interfaces[usedInterface.clazz]
                                if (interfaces != null) {
                                    for (interfaceI in interfaces) {
                                        handleBecomingConstructable(sig, interfaceI, dependencies)
                                    }
                                }
                                // add default implementation for $usedInterface
                                val defaults = hIndex.interfaceDefaults[usedInterface]
                                if (defaults != null) {
                                    newRemaining.addAll(defaults)
                                    checkState(dependencies)
                                }
                                fun checkChildClass(clazz: String) {
                                    val newSig = usedInterface.withClass(clazz)
                                    processDependencies(setOf(newSig))
                                    val children = hIndex.childClasses[clazz] ?: emptySet()
                                    for (child in children) {
                                        checkChildClass(child)
                                    }
                                }
                                checkChildClass(usedInterface.clazz)
                                checkState(dependencies)
                            }
                        }
                    }

                    checkState(dependencies)

                    // for every method that's being indexed, index all child implementations as well for all classes, that can be constructed
                    // this can change at runtime -> we need to repeat this, whenever a new constructable class is being found

                    // println("  handling $sig")
                    if (sig.name != "<init>") {
                        handleChildImplementations(sig.clazz, dependencies)
                    }

                    checkState(dependencies)

                    val usedFieldsR = fieldDependenciesR[sig]
                    val usedFieldsW = fieldDependenciesW[sig]
                    // if a method touches sth forbidden, and it's no entry point, remove it
                    // because we have to implement it ourselves anyway
                    if (usedMethods.any { forbidden(it.clazz) }
                        || (usedFieldsR != null && usedFieldsR.any { forbidden(it.clazz) })
                        || (usedFieldsW != null && usedFieldsW.any { forbidden(it.clazz) })
                    ) {
                        if (sig.clazz == "java/lang/Object" && sig.name == "hashCode" && sig.descriptor == "hashCode()I")
                            throw IllegalStateException("$sig is native, not forbidden")
                        methodsWithForbiddenDependencies.add(sig)
                    } else {

                        // abstract classes cannot be constructed, even if they have constructors; interfaces neither
                        if (sig.clazz != "?" && sig.name == "<init>") {
                            handleBecomingConstructable(sig, sig.clazz, dependencies)
                            checkState(dependencies)
                        }

                        checkState(dependencies)

                        // println("--- Processing $sig ---")
                        val cc = constructorDependencies[sig]
                        if (cc != null) {
                            for (clazz in cc) {
                                handleBecomingConstructable(sig, clazz, dependencies)
                                checkState(dependencies, true)
                            }
                        }

                        processDependencies(dependencies)

                        if (usedFieldsR != null)
                            this.usedFieldsR.addAll(usedFieldsR)
                        if (usedFieldsW != null)
                            this.usedFieldsW.addAll(usedFieldsW)

                        checkState(dependencies, true)

                    }

                    checkState(dependencies, true)

                    // of all reached static fields, clinit must be called (when reading static properties)
                    // only if complement function is defined as well
                    val strict = fieldsRWRequired
                    if (usedFieldsR != null) for (fi in usedFieldsR) {
                        if (fi.static && (!strict || fi in this.usedFieldsW)) {
                            val sig1 = MethodSig.c(fi.clazz, "<clinit>", "()V")
                            if (sig1 !in this.usedMethods)
                                newRemaining.add(sig1)
                        }
                    }
                    if (usedFieldsW != null) for (fi in usedFieldsW) {
                        if (fi.static && (!strict || fi in this.usedFieldsR)) {
                            val sig1 = MethodSig.c(fi.clazz, "<clinit>", "()V")
                            if (sig1 !in this.usedMethods)
                                newRemaining.add(sig1)
                        }
                    }

                    checkState(dependencies, true)

                }
            }
            newRemaining.removeAll(this.usedMethods) // they were done
            val tmp = remaining
            remaining = newRemaining
            newRemaining = tmp
        } while (remaining.isNotEmpty())

        for (sig in usedMethods) {
            if (sig in methodsWithForbiddenDependencies) continue
            for (clazz in constructorDependencies[sig] ?: continue) {
                if (clazz !in constructableClasses && !forbidden(clazz)) {
                    printUsed(sig)
                    println(clazz)
                    throw java.lang.IllegalStateException("used $sig, but missed $clazz")
                }
            }
        }

    }
}