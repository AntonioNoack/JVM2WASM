package dependency

import fieldsRWRequired
import gIndex
import hIndex
import me.anno.maths.Maths.hasFlag
import resolvedMethods
import utils.*

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

    fun findSuperMethod(clazz: String, method: MethodSig): MethodSig? {
        val method3 = MethodSig.c(clazz, method.name, method.descriptor)
        val dep = methodDependencies[method3]
        if (dep != null) return method3
        // check interfaces for default-implementations
        val interfaces = hIndex.interfaces[clazz]
        if (interfaces != null) for (interfaceI in interfaces) {
            for (method2 in hIndex.methods[interfaceI]!!) {
                if (method2.name == method.name && method2.descriptor == method.descriptor &&
                    method2 !in hIndex.notImplementedMethods
                ) return method2
            }
        }
        // check super class
        val superClass = hIndex.superClass[clazz]
        return if (superClass != null) findSuperMethod(superClass, method) else null
    }

    var constructableClasses = HashSet<String>()

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
                "[]", "[I", "[F", "[Z", "[B", "[C", "[S", "[J", "[D",
                "int", "float", "boolean", "byte", "short", "char", "long", "double"
            )
        )
        constructableClasses.addAll(entryClasses)

        var remaining = HashSet<MethodSig>(size)
        var newRemaining = HashSet<MethodSig>(size)


        remaining.addAll(entryPoints)
        remaining.addAll(usedMethods)

        for ((ci, fo) in gIndex.fieldOffsets) {
            if (ci.hasFlag(1) && fo.fields.isNotEmpty()) { // static
                remaining.add(MethodSig.c(gIndex.classNames[ci shr 1], "<clinit>", "()V"))
            }
        }

        methodsWithForbiddenDependencies = HashSet(size)
        val depsIfConstructable = HashMap<String, HashSet<MethodSig>>(size)

        usedMethods.clear()

        fun handleBecomingConstructable(sig: MethodSig, clazz: String, usedMethods1: MutableSet<MethodSig>) {
            /*if (clazz == "me/anno/ui/input/TextInput") {
                printUsed(sig)
                throw IllegalStateException(clazz)
            }*/
            /*if (clazz == "me/anno/ecs/Entity\$Companion") {
                printUsed(sig)
                throw IllegalStateException(clazz)
            }*/
            if (constructableClasses.add(clazz)) {
                // println("$sig -> $clazz")

                // check for all interfaces, whether we should implement their functions
                fun handleInterfaces(clazz1: String) {
                    val interfaces2 = hIndex.interfaces[clazz1]
                    if (interfaces2 != null) for (interface1 in interfaces2) {
                        handleBecomingConstructable(sig, interface1, usedMethods1)
                        handleInterfaces(interface1)
                        val methods = hIndex.methods[interface1] ?: continue
                        for (method2 in methods) {
                            if (method2 in usedInterfaceCalls) {
                                usedMethods1.add(MethodSig.c(clazz, method2.name, method2.descriptor))
                            }
                        }
                    }
                    val superClass1 = hIndex.superClass[clazz1]
                    if (superClass1 != null) handleInterfaces(superClass1)
                }
                handleInterfaces(clazz)

                val superClass = hIndex.superClass[clazz]
                if (superClass != null) handleBecomingConstructable(sig, superClass, usedMethods1)

                val deps = depsIfConstructable.remove(clazz)
                if (deps != null) newRemaining.addAll(deps)

                // of all super classes, depend on all their relevant methods
                fun handleSuper(superClass: String) {
                    // if (clazz == "java/util/Collections\$SetFromMap") println("processing $superClass")
                    val superMethods = usedByClass[superClass]
                    if (superMethods != null) {
                        for (method2 in superMethods) {
                            val childMethod = MethodSig.c(clazz, method2.name, method2.descriptor)
                            if (method2 !in methodsWithForbiddenDependencies) {
                                // if (clazz == "java/util/Collections\$SetFromMap") println("  marked $childMethod for use")
                                usedMethods1.add(childMethod)
                            }
                        }
                    }
                    handleSuper(hIndex.superClass[superClass] ?: return)
                }
                handleSuper(clazz)

            }
        }

        val dependencies = HashSet<MethodSig>(64)
        val usedMethods = HashSet<MethodSig>(64)

        fun used(sig: MethodSig): Boolean {
            return sig in usedMethods || sig in newRemaining || sig in remaining || sig in this.usedMethods
        }

        fun checkState() {
            val sig0 = MethodSig.c("java/lang/Object", "hashCode", "()I")
            val sig1 = MethodSig.c("jvm/JavaLang", "Object_hashCode", "(Ljava/lang/Object;)I")
            if (!used(sig0) && used(sig1)) {
                printUsed(sig0)
                printUsed(sig1)
                throw IllegalStateException()
            }
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

                    if (sig !in hIndex.staticMethods)
                        usedByClass.getOrPut(sig.clazz) { HashSet() }.add(sig)

                    checkState(dependencies)

                    fun handleChildImplementations(clazz: String, dependencies: HashSet<MethodSig>) {
                        for (cc in hIndex.childClasses[clazz] ?: return) {
                            val print = false // cc in constructableClasses || cc == "java/lang/reflect/Field"
                            if (print) println("  | child: $cc, constructable? ${cc in constructableClasses}")
                            handleChildImplementations(cc, dependencies)
                            if (cc in constructableClasses) {
                                val sig2 = MethodSig.c(cc, sig.name, sig.descriptor)
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

                    val name = methodName(sig)
                    val alias = hIndex.methodAliases[name]
                    // println("[alias] $sig -> $name -> $alias")
                    if (alias != null && alias != sig) {
                        methodDependencies[sig] = methodDependencies[alias] ?: hashSetOf()
                        fieldDependenciesR[sig] = fieldDependenciesR[alias] ?: emptySet()
                        fieldDependenciesW[sig] = fieldDependenciesW[alias] ?: emptySet()
                        constructorDependencies[sig] = constructorDependencies[alias] ?: emptySet()
                        interfaceDependencies[sig] = interfaceDependencies[alias] ?: HashSet()
                        hIndex.methodAliases[name] = alias
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
                                // println("indexing $usedInterface in ${constructableClasses.size} existing constructable classes")
                                for (clazz in constructableClasses) {
                                    // when is this method available?
                                    // this class or super class have an interface with this method
                                    // or more general (less correct, but maybe faster :)): this class or super class know this method
                                    // interfaces must be searches as well for $usedInterface ... for default implementation
                                    fun hasInterface(clazz: String): Boolean {
                                        val interfaces2 = hIndex.interfaces[clazz]
                                        if (interfaces2 != null) {
                                            for (interfaceI in interfaces2) {
                                                val itsMethods = hIndex.methods[interfaceI]!!
                                                val sig1 = MethodSig.c(
                                                    interfaceI,
                                                    usedInterface.name,
                                                    usedInterface.descriptor
                                                )
                                                if (sig1 in itsMethods && sig1 !in hIndex.staticMethods) return true
                                                if (hasInterface(interfaceI)) return true // super interfaces
                                            }
                                        }
                                        val superClass = hIndex.superClass[clazz]
                                        return superClass != null && hasInterface(superClass)
                                    }
                                    if (hasInterface(clazz)) {
                                        val sig2 = MethodSig.c(
                                            clazz,
                                            usedInterface.name,
                                            usedInterface.descriptor
                                        )
                                        if (sig2 !in this.usedMethods) newRemaining.add(sig2)
                                    }
                                }
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

                        val cc = constructorDependencies[sig]
                        if (cc != null) for (clazz in cc) {
                            handleBecomingConstructable(sig, clazz, dependencies)
                        }

                        checkState(dependencies, true)

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
                            if (/*hIndex.methods[fi.clazz]?.contains(sig1) == true && */sig1 !in this.usedMethods)
                                newRemaining.add(sig1)
                        }
                    }
                    if (usedFieldsW != null) for (fi in usedFieldsW) {
                        if (fi.static && (!strict || fi in this.usedFieldsR)) {
                            val sig1 = MethodSig.c(fi.clazz, "<clinit>", "()V")
                            if (/*hIndex.methods[fi.clazz]?.contains(sig1) == true && */sig1 !in this.usedMethods)
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