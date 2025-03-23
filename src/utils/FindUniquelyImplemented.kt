package utils

import dIndex
import hIndex
import me.anno.utils.Clock
import me.anno.utils.algorithms.Recursion
import me.anno.utils.structures.maps.CountMap
import org.apache.logging.log4j.LogManager

private val LOGGER = LogManager.getLogger("FindUniquelyImplemented")

private val childImplementationMap = HashMap<MethodSig, HashSet<MethodSig>>(1 shl 12)
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
        if (clazz in dIndex.constructableClasses) {
            if (hIndex.finalMethods.add(sig.withClass(clazz))) {
                val children = hIndex.childClasses[clazz]
                if (children != null) remaining.addAll(children)
            }
        }
    }
}

fun findMethodsWithoutChildClasses(): Int {
    val numAlreadyFinalMethods = hIndex.finalMethods.size
    val constructableClasses = dIndex.constructableClasses
    val childClasses = hIndex.childClasses
    val finalMethods = hIndex.finalMethods
    for (clazz in constructableClasses) {
        val print = false // clazz == "me/anno/utils/hpc/WorkSplitter" || clazz == "me/anno/utils/hpc/ProcessingQueue"
        if (print) println("$clazz, constructable? ${clazz in constructableClasses}")
        val methods = hIndex.methodsByClass[clazz] ?: continue
        val childClassesI = childClasses[clazz] ?: emptySet()
        if (childClassesI.none { it in constructableClasses }) {
            // no constructable child classes -> all its methods must be final
            if (print) {
                println(methods)
                println("me/anno/utils/hpc/ProcessingQueue" in constructableClasses)
                throw IllegalStateException()
            }
            finalMethods.addAll(methods)
        } else if (clazz in constructableClasses) {
            // if all children have the same implementation, it's final for all of them, too
            for (method in methods) {
                if (!hIndex.isFinal(method)) {
                    val variants = findConstructableChildImplementations(method)
                    if (variants.size == 1) {
                        if (print) println("new final $method for variants: $variants")
                        // mark all child methods final
                        // hIndex.finalMethods.add(method)
                        markChildMethodsFinal(method)
                    } else if (print) println("not-final $method, ${variants.size}x")
                } else if (print) println("already final: $method")
            }
        }
    }
    return finalMethods.size - numAlreadyFinalMethods
}

/**
 * find functions with a single implementation only, and make it final
 * */
fun findUniquelyImplemented(usedMethods: Collection<MethodSig>, implementedMethods: Set<MethodSig>, clock: Clock) {
    clock.start()
    LOGGER.info("[findUniquelyImplemented]")

    // we only need this for classes, where multiple classes are constructable
    // otherwise, we can directly resolve the call :)

    // of all classes without constructable child classes,
    // all their methods are final
    var finalMethods = findMethodsWithoutChildClasses()
    clock.stop("findMethodsWithoutChildClasses [$finalMethods]")

    val methodCounter = CountMap<InterfaceSig>(usedMethods.size)
    for (sig in usedMethods) {
        if (
            sig.name != INSTANCE_INIT && // cannot be invoked dynamically
            sig.clazz != INTERFACE_CALL_NAME &&
            hIndex.getAlias(sig) == sig && // just the same
            !hIndex.isStatic(sig) &&
            !hIndex.isAbstract(sig) && // we can ignore that one
            sig in implementedMethods
        ) {
            val interfaceSig = InterfaceSig(sig)
            val count = methodCounter.incAndGet(interfaceSig)
            when (count) {
                1 -> finalMethods++
                2 -> finalMethods--
            }
        }
    }
    clock.stop("Method Counter [${methodCounter.values.size}]")

    val toBeMarkedAsFinal = HashSet<InterfaceSig>(finalMethods)
    for ((sig, counter) in methodCounter.values) {
        if (counter.value == 1) {
            toBeMarkedAsFinal.add(sig)
        }
    }
    clock.stop("Prepare MarkAsFinal")

    // mark all those methods as final
    for (sig in usedMethods) {
        if (hIndex.getAlias(sig) == sig && // just the same
            !hIndex.isStatic(sig) &&
            !hIndex.isAbstract(sig) && // we can ignore that one
            InterfaceSig(sig) in toBeMarkedAsFinal
        ) {
            hIndex.finalMethods.add(sig)
        }
    }
    clock.stop("Execute MarkAsFinal")

    LOGGER.info("Used methods: ${usedMethods.size}")
    LOGGER.info("Implemented methods: ${implementedMethods.size}")
    LOGGER.info("Found $finalMethods uniquely implemented methods and made them final")
}