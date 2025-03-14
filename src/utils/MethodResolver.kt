package utils

import dIndex
import hIndex
import org.apache.logging.log4j.LogManager
import utils.PrintUsed.printUsed

/**
 * given a class hierarchy, finds which implementation should be used for a specific called class
 * */
object MethodResolver {

    private val LOGGER = LogManager.getLogger(MethodResolver::class)

    private var throwNotConstructable = false
    private var debugFindMethod = false

    fun resolveMethod(methodSig: MethodSig, throwNotConstructable: Boolean): MethodSig? {
        this.throwNotConstructable = throwNotConstructable
        return resolveMethod(methodSig)
    }

    private fun checkNotConstructable(methodSig: MethodSig) {
        val clazz = methodSig.clazz
        val clazz2 = if (NativeTypes.isObjectArray(clazz)) "[]" else clazz
        val isStatic = methodSig in hIndex.staticMethods
        if (!(clazz2 in dIndex.constructableClasses || isStatic) &&
            methodSig in hIndex.methodsByClass[clazz2]!!
        ) {
            printUsed(methodSig)
            println("  child classes: ${hIndex.superClass.entries.filter { it.value == clazz2 }.map { it.key }}")
            LOGGER.warn("Non-constructable classes are irrelevant to be resolved ($clazz2)")
        }
    }

    /**
     * check if there is an implemented alias
     * */
    private fun getImplementedMethod(methodSig: MethodSig): MethodSig? {
        val mapped = hIndex.getAlias(methodSig)
        if (mapped in hIndex.jvmImplementedMethods ||
            mapped in hIndex.nativeMethods ||
            mapped in hIndex.customImplementedMethods
        ) {
            if (debugFindMethod) println("found impl: $mapped")
            return mapped
        } else return null
    }

    /**
     * checking whether method maybe is abstract
     * */
    private fun getAbstractMethod(methodSig: MethodSig, superClass: String?): MethodSig? {
        val clazz = methodSig.clazz
        if (hIndex.isAbstractClass(clazz) && methodSig in hIndex.abstractMethods) {
            if (debugFindMethod) println("method & clazz are abstract -> returning $methodSig")
            val superMethodSig = if (superClass != null) {
                resolveMethod(methodSig.withClass(superClass))
            } else null
            return superMethodSig ?: methodSig
        } else return null
    }

    /**
     * check super class
     * */
    private fun getSuperMethod(methodSig: MethodSig, superClass: String?): MethodSig? {
        if (superClass != null) {
            val bySuper = resolveMethod(methodSig.withClass(superClass))
            if (bySuper != null) return bySuper
        }
        return null
    }

    /**
     * check interfaces for default methods
     * */
    private fun getInterfaceMethod(methodSig: MethodSig, superClass: String?): MethodSig? {
        val clazz = methodSig.clazz
        val interfaces1 = hIndex.interfaces[clazz] ?: emptyList()
        val superInterfaces = hIndex.interfaces[superClass] ?: emptyList()
        for (interfaceI in interfaces1) {
            if (interfaceI !in superInterfaces) {
                val byInterface = resolveMethod(methodSig.withClass(interfaceI))
                if (byInterface != null) return byInterface
            }
        }
        return null
    }

    private fun resolveMethod(methodSig: MethodSig): MethodSig? {
        val clazz = methodSig.clazz
        val superClass = hIndex.superClass[clazz]
        if (debugFindMethod) println("searching $clazz")

        if (throwNotConstructable) {
            checkNotConstructable(methodSig)
        }

        // this order is very important!!!
        return getImplementedMethod(methodSig)
            ?: getAbstractMethod(methodSig, superClass)
            ?: getSuperMethod(methodSig, superClass)
            ?: getInterfaceMethod(methodSig, superClass)
    }
}