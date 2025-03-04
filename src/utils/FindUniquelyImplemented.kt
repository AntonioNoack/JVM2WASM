package utils

import dIndex
import hIndex
import me.anno.utils.assertions.*
import me.anno.utils.structures.Recursion
import me.anno.utils.structures.maps.CountMap
import org.apache.logging.log4j.LogManager

private val LOGGER = LogManager.getLogger("FindUniquelyImplemented")

val childImplementationMap = HashMap<MethodSig, HashSet<MethodSig>>()

fun findConstructableChildImplementations(sig: MethodSig): Set<MethodSig> {
    return if (sig.clazz in dIndex.constructableClasses) {
        childImplementationMap.getOrPut(sig) {
            val variants = HashSet<MethodSig>()
            val mapped = hIndex.getAlias(sig)
            if (mapped in hIndex.jvmImplementedMethods || mapped in hIndex.customImplementedMethods) {
                variants.add(mapped)
            }
            val children = hIndex.childClasses[sig.clazz]
            if (children != null) {
                for (child in children) {
                    variants.addAll(findConstructableChildImplementations(sig.withClass(child)))
                }
            }
            variants
        }
    } else emptySet()
}

fun markChildMethodsFinal(sig: MethodSig) {
    Recursion.processRecursive(sig.clazz) { clazz, remaining ->
        val sigI = sig.withClass(clazz)
        if (sigI.clazz in dIndex.constructableClasses) {
            hIndex.finalMethods.add(sigI)
            val children = hIndex.childClasses[sigI.clazz]
            remaining.addAll(children ?: emptySet())
        }
    }
}

fun findMethodsWithoutChildClasses(): Int {
    val numAlreadyFinalMethods = hIndex.finalMethods.size
    val classes = dIndex.constructableClasses
    for (clazz in classes) {
        val print = false // clazz == "me/anno/utils/hpc/WorkSplitter" || clazz == "me/anno/utils/hpc/ProcessingQueue"
        if (print) println("$clazz, constructable? ${clazz in dIndex.constructableClasses}")
        val childClasses = hIndex.childClasses[clazz] ?: emptySet()
        if (childClasses.none { it in dIndex.constructableClasses }) {
            // no constructable child classes -> all its methods must be final
            val methods = hIndex.methodsByClass[clazz] ?: continue
            if (print) {
                println(methods)
                println("me/anno/utils/hpc/ProcessingQueue" in dIndex.constructableClasses)
                throw IllegalStateException()
            }
            hIndex.finalMethods.addAll(methods)
        } else if (clazz in dIndex.constructableClasses) {
            // if all children have the same implementation, it's final for all of them, too
            val methods = hIndex.methodsByClass[clazz] ?: continue
            for (method in methods) {
                if (method !in hIndex.finalMethods) {
                    val variants = findConstructableChildImplementations(method)
                    if (variants.size == 1) {
                        if (print) println("new final $method for variants: $variants")
                        // mark all child methods final
                        markChildMethodsFinal(method)
                    } else if (print) println("not-final $method, ${variants.size}x")
                } else if (print) println("already final: $method")
            }
        }
    }
    assertFalse(invalidFinal in hIndex.finalMethods)
    return hIndex.finalMethods.size - numAlreadyFinalMethods
}

// todo remove these conditions when it works
val invalidFinal = MethodSig.c(
    "me/anno/utils/hpc/WorkSplitter", "processUnbalanced",
    "(IIILme_anno_utils_hpc_WorkSplitterXTask1d;)V", false
)

val validFinal = MethodSig.c(
    "me/anno/utils/hpc/ProcessingQueue", "processUnbalanced",
    "(IIILme_anno_utils_hpc_WorkSplitterXTask1d;)V", false
)

/**
 * find functions with a single implementation only, and make it final
 * */
fun findUniquelyImplemented(usedMethods: Collection<MethodSig>, implementedMethods: Set<MethodSig>) {
    LOGGER.info("[findUniquelyImplemented]")

    // we only need this for classes, where multiple classes are constructable
    // otherwise, we can directly resolve the call :)

    // of all classes without constructable child classes,
    // all their methods are final
    var finalMethods = findMethodsWithoutChildClasses()

    val checkedSig = InterfaceSig(invalidFinal)
    val methodCounter = CountMap<InterfaceSig>(usedMethods.size)
    for (sig in usedMethods) {
        if (
            sig.name != INSTANCE_INIT && // cannot be invoked dynamically
            sig.clazz != INTERFACE_CALL_NAME &&
            hIndex.getAlias(sig) == sig && // just the same
            sig !in hIndex.staticMethods &&
            sig !in hIndex.abstractMethods && // we can ignore that one
            sig in implementedMethods
        ) {
            val interfaceSig = InterfaceSig(sig)
            val count = methodCounter.incAndGet(interfaceSig)
            if (interfaceSig == checkedSig) println("$interfaceSig++ by $sig")
            when (count) {
                1 -> finalMethods++
                2 -> finalMethods--
            }
        } else {
            assertNotSame(validFinal, sig, "Valid final ignored???")
        }
    }

    if (false) {
        assertTrue(validFinal.clazz in dIndex.constructableClasses)
        assertTrue(invalidFinal in usedMethods)
        assertEquals(validFinal, hIndex.getAlias(validFinal))
        assertTrue(validFinal in usedMethods, "Valid final is unused???")
    }

    val toBeMarkedAsFinal = HashSet<InterfaceSig>(finalMethods)
    for ((sig, counter) in methodCounter.values) {
        if (counter.value == 1) {
            toBeMarkedAsFinal.add(sig)
        }
    }

    // mark all those methods as final
    for (sig in usedMethods) {
        if (hIndex.getAlias(sig) == sig && // just the same
            sig !in hIndex.staticMethods &&
            sig !in hIndex.abstractMethods && // we can ignore that one
            InterfaceSig(sig) in toBeMarkedAsFinal
        ) {
            assertNotEquals(invalidFinal, sig)
            hIndex.finalMethods.add(sig)
        }
    }

    LOGGER.info("Found $finalMethods uniquely implemented methods and made them final")
}