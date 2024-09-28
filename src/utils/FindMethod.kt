package utils

import dIndex
import hIndex
import me.anno.utils.types.Booleans.hasFlag
import org.apache.logging.log4j.LogManager
import org.objectweb.asm.Opcodes

private val LOGGER = LogManager.getLogger("FindMethod")

fun findMethod(clazz: String, sig: MethodSig): MethodSig? {
    return findMethod(clazz, sig.name, sig.descriptor)
}

var debugFindMethod = false
fun findMethod(clazz: String, name: String, desc: String, throwNotConstructable: Boolean = true): MethodSig? {
    if (debugFindMethod) println("searching $clazz")
    val methodSig = MethodSig.c(clazz, name, desc)
    val isStatic = methodSig in hIndex.staticMethods
    fun isConstructable() = clazz in dIndex.constructableClasses
    if (throwNotConstructable && !(isConstructable() || isStatic) && methodSig in hIndex.methods[clazz]!!) {
        printUsed(methodSig)
        println("  child classes: ${hIndex.superClass.entries.filter { it.value == clazz }.map { it.key }}")
        LOGGER.warn("Non-constructable classes are irrelevant to be resolved ($clazz)")
    }

    // checking whether method maybe is abstract...
    val superClass = hIndex.superClass[clazz]
    if (hIndex.isAbstractClass(clazz) && methodSig in hIndex.abstractMethods) {
        if (debugFindMethod) println("method & clazz are abstract -> returning $methodSig")
        val superMethodSig = if (superClass != null) findMethod(superClass, name, desc, throwNotConstructable) else null
        return superMethodSig ?: methodSig
    }

    // check if method is actually implemented
    if (methodSig in hIndex.jvmImplementedMethods) {
        if (debugFindMethod) println("found impl: $methodSig")
        return methodSig
    }

    // check if there is aliases
    val mapped = hIndex.getAlias(methodSig)
    if (mapped != methodSig) {
        if (debugFindMethod) println("looking up map $mapped")
        return findMethod(mapped.clazz, mapped.name, mapped.descriptor, throwNotConstructable)
    }

    // check super class
    if (superClass != null) {
        val bySuper = findMethod(superClass, name, desc, throwNotConstructable)
        if (bySuper != null) return bySuper
    }

    // check interfaces
    val interfaces1 = hIndex.interfaces[clazz] ?: emptyList()
    val superInterfaces = hIndex.interfaces[superClass] ?: emptyList()
    for (interfaceI in interfaces1) {
        if (interfaceI !in superInterfaces) {
            val interfaceMethodSig = findMethod(interfaceI, name, desc, throwNotConstructable)
            if (interfaceMethodSig != null) {
                return interfaceMethodSig
            }
        }
    }

    return null
}
