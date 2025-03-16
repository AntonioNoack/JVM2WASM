package dependency

import cannotUseClass
import fieldsRWRequired
import gIndex
import hIndex
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.Recursion
import me.anno.utils.types.Booleans.hasFlag
import org.apache.logging.log4j.LogManager
import resolvedMethods
import utils.*
import utils.MethodResolver.resolveMethod
import utils.PrintUsed.printUsed

object DependencyIndex {

    private const val cap = 4096
    private const val cap2 = 256

    val getterDependencies = HashMap<MethodSig, Set<FieldSig>>(cap)
    val setterDependencies = HashMap<MethodSig, Set<FieldSig>>(cap)
    var methodDependencies = HashMap<MethodSig, HashSet<MethodSig>>(cap)
    val constructorDependencies = HashMap<MethodSig, Set<String>>(cap)
    val methodsWithForbiddenDependencies = HashSet<MethodSig>(cap)
    val interfaceDependencies = HashMap<MethodSig, HashSet<MethodSig>>(cap2) // pseudo-signature
    val knownInterfaceDependencies = HashSet<MethodSig>(cap2)

    val usedMethods = HashSet<MethodSig>(cap)
    val usedGetters = HashSet<FieldSig>(cap)
    val usedSetters = HashSet<FieldSig>(cap)
    val usedInterfaceCalls = HashSet<MethodSig>(cap2)

    lateinit var constructableClasses: HashSet<String>

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
        val interfaces = hIndex.interfaces[method.clazz] ?: emptyList()
        for (interfaceI in interfaces) {
            for (method2 in hIndex.methodsByClass[interfaceI] ?: emptySet()) {
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
        if (methodI in (hIndex.methodsByClass[superClass] ?: emptySet())) {
            // if present in parent, return parent method
            return findSuperMethod1(methodI) ?: methodI
        }
        // not present in parent -> we must map ours
        return null
    }

    private fun addAlwaysConstructableClasses() {
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
                "java/lang/reflect/Method", // for getters and setters only at the moment
                "java/lang/reflect/Executable", // super class of java.lang.reflect.Method
                "java/lang/reflect/AccessibleObject",// super class of java.lang.reflect.Field
                "java/lang/reflect/Constructor", // needed for Class.newInstance()
                "[]", "[I", "[F", "[Z", "[B", "[C", "[S", "[J", "[D",
                "int", "float", "boolean", "byte", "short", "char", "long", "double",
            )
        )
    }

    /**
     * find which methods are used, and which classes are constructable
     *
     * method -> unlock called methods, classes from new instances, methods from child classes;
     * classes -> unlock by-parent-classes-used-methods and by-used-interfaces-used-methods
     *
     * interfaces?
     * */
    fun resolve(entryClasses: Set<String>, entryPoints: Set<MethodSig>) {

        val size = methodDependencies.size
        val usedByClass = HashMap<String, HashSet<MethodSig>>(size / 8) // 23s -> 13s ❤

        constructableClasses = HashSet(size)
        addAlwaysConstructableClasses()
        constructableClasses.addAll(entryClasses)

        val remaining = HashSet<MethodSig>(size)

        remaining.addAll(entryPoints)
        remaining.addAll(usedMethods)

        for ((index, offsets) in gIndex.fieldOffsets) {
            if (index.hasFlag(1) && offsets.hasFields()) { // static
                remaining.add(MethodSig.c(gIndex.classNamesByIndex[index shr 1], STATIC_INIT, "()V"))
            }
        }

        val depsIfConstructable = HashMap<String, HashSet<MethodSig>>(size)

        usedMethods.clear()

        val usedMethods1 = HashSet<MethodSig>(64)

        fun used(sig: MethodSig): Boolean {
            return sig in usedMethods1 || sig in remaining || sig in usedMethods
        }

        fun addRemaining(method: MethodSig) {
            if (method !in this.usedMethods) {
                remaining.add(method)
            }
        }

        fun addRemaining(methods: Collection<MethodSig>) {
            for (sig in methods) addRemaining(sig)
        }

        fun handleBecomingConstructable(sig: MethodSig, clazz: String, newUsedMethods: MutableSet<MethodSig>) {
            if (!constructableClasses.add(clazz)) return

            // println("$clazz becomes constructible")

            // val print = clazz == "kotlin/jvm/internal/PropertyReference1Impl"
            // if (print) println("$sig -> $clazz")

            // check for all interfaces, whether we should implement their functions
            fun handleInterfaces(clazz1: String) {
                val interfaces2 = hIndex.interfaces[clazz1]
                if (interfaces2 != null) {
                    for (interface1 in interfaces2) {
                        handleBecomingConstructable(sig, interface1, newUsedMethods)
                        handleInterfaces(interface1)
                        val methods = hIndex.methodsByClass[interface1] ?: continue
                        for (method2 in methods) {
                            if (used(method2)) {
                                newUsedMethods.add(MethodSig.c(clazz, method2.name, method2.descriptor))
                            }
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
            addRemaining(dependenciesByConstructable)

            // of all super classes, depend on all their relevant methods
            fun handleSuper(superClass: String) {
                // if (clazz == "java/util/Collections\$SetFromMap") println("processing $superClass")
                val superMethods = usedByClass[superClass]
                if (superMethods != null) {
                    for (method2 in superMethods) {
                        val childMethod = method2.withClass(clazz)
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

        val dependencies = HashSet<MethodSig>(64)

        fun checkState(i: Int) {
            // can be filled to check the validity of the current solution
        }

        fun processDependency(method: MethodSig) {
            if (method.name == INSTANCE_INIT || hIndex.isStatic(method) || method.clazz in constructableClasses) {
                addRemaining(method)
            } else {
                depsIfConstructable.getOrPut(method.clazz, ::HashSet).add(method)
            }
        }

        fun processDependencies(dependencies: Set<MethodSig>) {
            for (method in dependencies) {
                processDependency(method)
            }
        }

        fun handleInterfaceBecomesUsed(sig: MethodSig, usedInterface: MethodSig) {
            // println("adding used-as-interface: $usedInterface")
            if (!usedInterfaceCalls.add(usedInterface)) return

            Recursion.processRecursive(usedInterface.clazz) { interfaceI, remaining1 ->
                handleBecomingConstructable(sig, interfaceI, dependencies)
                remaining1.addAll(hIndex.interfaces[interfaceI] ?: emptyList())
            }

            // add default implementation for $usedInterface
            addRemaining(hIndex.interfaceDefaults[usedInterface] ?: emptySet())
            checkState(6)

            Recursion.processRecursive(usedInterface.clazz) { clazz, remaining1 ->
                processDependency(usedInterface.withClass(clazz))
                remaining1.addAll(hIndex.childClasses[clazz] ?: emptySet())
            }
            checkState(7)
        }

        fun handleInterfaceDependencies(sig: MethodSig) {
            val usedInterfaces = interfaceDependencies[sig] ?: return
            for (usedInterface in usedInterfaces) {
                handleInterfaceBecomesUsed(sig, usedInterface)
            }
        }

        checkState(0)

        while (remaining.isNotEmpty()) {
            val sig = remaining.first()
            assertTrue(remaining.remove(sig))
            assertTrue(usedMethods.add(sig))

            // println("[+dep] $sig")

            dependencies.clear()

            checkState(1)

            val isStatic = hIndex.isStatic(sig)
            val resolved = resolveMethod(sig, true)
            if (!isStatic) {
                usedByClass.getOrPut(sig.clazz, ::HashSet).add(sig)
            }

            checkState(2)

            fun handleChildImplementations(clazz: String, dstChildImplementations: HashSet<MethodSig>) {
                for (childClass in hIndex.childClasses[clazz] ?: return) {
                    val print = childClass == "me/anno/utils/hpc/ProcessingQueue"
                    if (print) println("  | child: $childClass, constructable? ${childClass in constructableClasses}")
                    handleChildImplementations(childClass, dstChildImplementations)
                    if (childClass in constructableClasses) {
                        val sig2 = sig.withClass(childClass)
                        val sig3 = resolvedMethods[sig2] ?: sig2
                        if (print) {
                            if (sig3 != sig2) println("    $sig2 -> $sig3")
                            else println("    $sig2")
                        }
                        dstChildImplementations.add(sig2) // not really useful, just for correctness checking
                        dstChildImplementations.add(sig3)
                    }
                }
            }

            val alias = hIndex.getAlias(sig)
            // println("[alias] $sig -> $name -> $alias")
            if (alias != sig) {
                methodDependencies[sig] = methodDependencies[alias] ?: hashSetOf()
                getterDependencies[sig] = getterDependencies[alias] ?: emptySet()
                setterDependencies[sig] = setterDependencies[alias] ?: emptySet()
                constructorDependencies[sig] = constructorDependencies[alias] ?: emptySet()
                interfaceDependencies[sig] = interfaceDependencies[alias] ?: HashSet()
                hIndex.setAlias(sig, alias)
                addRemaining(alias)
                // methodsWithForbiddenDependencies.add(sig) // why???
                handleChildImplementations(sig.clazz, dependencies)
                processDependencies(dependencies)
                continue
            }

            checkState(3)

            if (sig.name != INSTANCE_INIT && sig.name != STATIC_INIT) {

                if (!isStatic && sig.clazz !in constructableClasses)
                    throw IllegalStateException("$sig is being used without class being constructable")

                if (resolved != null && resolved != sig) {
                    addRemaining(resolved)
                    continue
                }
            }

            checkState(4)

            dependencies.addAll(methodDependencies[resolved] ?: emptyList())

            usedMethods1.clear()
            usedMethods1.addAll(dependencies)

            checkState(5)

            handleInterfaceDependencies(sig)

            checkState(8)

            // for every method that's being indexed, index all child implementations as well for all classes, that can be constructed
            // this can change at runtime -> we need to repeat this, whenever a new constructable class is being found

            // println("  handling $sig")
            if (sig.name != INSTANCE_INIT && !isStatic) {
                handleChildImplementations(sig.clazz, dependencies)
            }

            checkState(9)

            val usedGetters1 = getterDependencies[sig] ?: emptySet()
            val usedSetters1 = setterDependencies[sig] ?: emptySet()
            // if a method touches sth forbidden, and it's no entry point, remove it
            // because we have to implement it ourselves anyway
            if (usedMethods1.any { isForbiddenClass(it.clazz) }
                || usedGetters1.any { isForbiddenClass(it.clazz) }
                || usedSetters1.any { isForbiddenClass(it.clazz) }
            ) {
                if (sig.clazz == "java/lang/Object" && sig.name == "hashCode" && sig.descriptor.raw == "hashCode()I")
                    throw IllegalStateException("$sig is native, not forbidden")
                methodsWithForbiddenDependencies.add(sig)
                checkState(10)
            } else {

                // abstract classes cannot be constructed, even if they have constructors; interfaces neither
                if (sig.clazz != INTERFACE_CALL_NAME && sig.name == INSTANCE_INIT) {
                    handleBecomingConstructable(sig, sig.clazz, dependencies)
                    checkState(11)
                }

                checkState(12)

                // println("--- Processing $sig ---")
                val newClasses = constructorDependencies[sig]
                if (newClasses != null) {
                    for (newClass in newClasses) {
                        handleBecomingConstructable(sig, newClass, dependencies)
                        checkState(13)
                    }
                }

                processDependencies(dependencies)

                usedGetters.addAll(usedGetters1)
                usedSetters.addAll(usedSetters1)

                checkState(14)

            }

            // of all reached static fields, clinit must be called (when reading static properties)
            // only if complement function is defined as well
            val strict = fieldsRWRequired
            for (field in usedGetters1) {
                if (field.isStatic && (!strict || field in usedSetters)) {
                    addRemaining(MethodSig.staticInit(field.clazz))
                }
            }
            for (field in usedSetters1) {
                if (field.isStatic && (!strict || field in usedGetters)) {
                    addRemaining(MethodSig.staticInit(field.clazz))
                }
            }

            checkState(15)

        }

        checkConstructorDependencies(usedMethods1)

    }

    private fun checkConstructorDependencies(usedMethods1: Set<MethodSig>) {
        for (sig in usedMethods1) {
            if (sig in methodsWithForbiddenDependencies) continue
            for (clazz in constructorDependencies[sig] ?: continue) {
                if (clazz !in constructableClasses && !isForbiddenClass(clazz)) {
                    printUsed(sig)
                    println(clazz)
                    throw IllegalStateException("used $sig, but missed $clazz")
                }
            }
        }
    }

    private fun isForbiddenClass(clazz: String): Boolean {
        return cannotUseClass(clazz)
    }
}