package utils

import dIndex
import dependency.ActuallyUsedIndex
import gIndex
import hIndex
import me.anno.utils.structures.lists.Lists.any2
import me.anno.utils.types.Strings.distance
import kotlin.math.min

object PrintUsed {

    private fun isUsedAsInterface(sig: MethodSig, clazz: String = sig.clazz): String? {
        val interfaces = hIndex.interfaces[clazz] ?: emptyList()
        if (interfaces.any2 { sig.withClass(it) in dIndex.usedInterfaceCalls }) {
            return clazz
        }
        val superClass = hIndex.superClass[clazz]
        val bySuper = if (superClass != null) isUsedAsInterface(sig, superClass) else null
        if (bySuper != null) return bySuper
        return interfaces.firstNotNullOfOrNull { isUsedAsInterface(sig, it) }
    }

    private val builder = StringBuilder2(512)
    fun printUsed(sig: MethodSig) {
        builder.append("  ").append(sig).append(":")
        if (sig in (hIndex.methodsByClass[sig.clazz] ?: emptySet())) builder.append(" known")
        if (sig in dIndex.usedMethods) builder.append(" used")
        else {
            // check all super classes
            var parent = hIndex.superClass[sig.clazz]
            while (parent != null) {
                val sig2 = sig.withClass(parent)
                if (sig2 in dIndex.usedMethods) {
                    builder.append(" used-by-parent:$parent")
                    break
                }
                parent = hIndex.superClass[parent]
            }
        }

        if (sig in hIndex.jvmImplementedMethods) builder.append(" jvm-implemented")
        if (sig in hIndex.notImplementedMethods) builder.append(" not-implemented")
        if (sig in hIndex.customImplementedMethods) builder.append(" custom-implemented")
        if (sig in gIndex.translatedMethods) builder.append(" translated")
        if (hIndex.isStatic(sig)) builder.append(" static")
        if (hIndex.isNative(sig)) builder.append(" native")
        if (hIndex.isAbstract(sig)) builder.append(" abstract")
        if (hIndex.isFinal(sig)) builder.append(" final")
        if (sig in dIndex.methodsWithForbiddenDependencies) builder.append(" forbidden")

        val uaiClass = isUsedAsInterface(sig)
        if (uaiClass != null) {
            builder.append(" used-as-interface")
            if (uaiClass != sig.clazz) builder.append("[$uaiClass]")
        } else if (sig in dIndex.knownInterfaceDependencies) {
            builder.append(" interface")
        }

        if (sig.clazz in dIndex.constructableClasses) builder.append(" constructable")

        val name = methodName(sig)
        if (name in ActuallyUsedIndex.resolved) builder.append(" actually-used(${ActuallyUsedIndex.usedBy[name]})")
        val mapsTo = hIndex.getAlias(sig)
        if (mapsTo != sig) builder.append(" maps-to: ").append(mapsTo)
        val mappedBy = hIndex.methodAliases.entries.filter { /*it.key != name &&*/ it.value == sig }.map { it.key }
        if (mappedBy.isNotEmpty()) builder.append(" mapped-by: ").append(mappedBy)
        var usedBy = dIndex.methodDependencies.entries.filter { sig != it.key && sig in it.value }.map { it.key }
        if (sig in dIndex.usedMethods) usedBy = usedBy.filter { it in dIndex.usedMethods }
        if (usedBy.isNotEmpty()) builder.append(" used-by: ").append(usedBy.subList(0, min(usedBy.size, 10)))
        val uses = dIndex.methodDependencies[sig]
        if (!uses.isNullOrEmpty()) builder.append(" uses: ").append(uses)
        if (builder.endsWith(":")) {
            val str = sig.toString()
            val closestMatch = hIndex.methodsByClass.values.flatten().minByOrNull { str.distance(it.toString()) }
            builder.append(" closest-match: $closestMatch")
        }
        println(builder.toString())
        builder.clear()
        if (mapsTo != sig) {
            printUsed(mapsTo)
        }
    }

}