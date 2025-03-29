package dependency

import hIndex
import utils.FieldSig
import utils.MethodSig

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

    val constructableClasses = HashSet<String>(cap2)
    val constructableAnnotations = HashSet<String>()

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

    fun resolve(entryClasses: Set<String>, entryPoints: Set<MethodSig>) {
        DependencyResolver().resolve(entryClasses, entryPoints)
    }
}