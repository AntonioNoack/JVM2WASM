package utils

import dIndex
import hIndex
import me.anno.maths.Maths.hasFlag
import org.apache.logging.log4j.LogManager
import org.objectweb.asm.Opcodes

private val LOGGER = LogManager.getLogger("FindMethod")

fun findMethod(clazz: String, sig: MethodSig): MethodSig? {
    return findMethod(clazz, sig.name, sig.descriptor)
}

var printFM = false
fun findMethod(clazz: String, name: String, desc: String, throwNotConstructable: Boolean = true): MethodSig? {
    if (printFM) println("searching $clazz")
    val method3 = MethodSig.c(clazz, name, desc)
    val isStatic = method3 in hIndex.staticMethods
    val isConstructable = clazz in dIndex.constructableClasses
    if (throwNotConstructable && !(isConstructable || isStatic) && method3 in hIndex.methods[clazz]!!) {
        printUsed(method3)
        println("child classes: ${hIndex.superClass.entries.filter { it.value == clazz }.map { it.key }}")
        LOGGER.warn("Non-constructable classes are irrelevant to be resolved ($clazz)") // : ${dIndex.constructableClasses}
    }
    val superClass = hIndex.superClass[clazz]
    if ((hIndex.classFlags[clazz] ?: 0).hasFlag(Opcodes.ACC_ABSTRACT) && method3 in hIndex.abstractMethods) {
        if (printFM) println("method & clazz are abstract -> returning $method3")
        val method3i = if (superClass != null) findMethod(superClass, name, desc, throwNotConstructable) else null
        return method3i ?: method3
    }
    // println("  testing $method3")
    // if (method3 in hIndex.notImplementedMethods) return method3 // there is no implementation
    val dep = method3 in hIndex.jvmImplementedMethods
    // println("  dep? $dep")
    if (dep) {
        if (printFM) println("found impl: $method3")
        return method3
    }
    val mapped = hIndex.methodAliases[methodName(method3)]
    // println("  map? $mapped")
    if (mapped != null && mapped != method3) {
        if (printFM) println("looking up map $mapped")
        return findMethod(mapped.clazz, mapped.name, mapped.descriptor, throwNotConstructable)
    }
    // check interfaces
    val interfaces1 = hIndex.interfaces[clazz]
    val superInterfaces = hIndex.interfaces[superClass]
    // println("interfaces: [${interfaces1?.joinToString()}]")
    var maybe: MethodSig? = null
    if (interfaces1 != null) {
        for (interfaceI in interfaces1) {
            if (superInterfaces == null || interfaceI !in superInterfaces) {
                val m = findMethod(interfaceI, name, desc, throwNotConstructable)
                if (m != null) {
                    if (m !in hIndex.abstractMethods)
                        return m
                    maybe = m
                }
            }
        }
    }
    // check super class
    val bySuper = if (superClass != null) findMethod(superClass, name, desc, throwNotConstructable) else null
    return bySuper ?: maybe
}
