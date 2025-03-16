package utils

import dIndex
import gIndex
import hIndex
import hierarchy.DelayedLambdaUpdate
import utils.PrintUsed.printUsed

object MissingFunctions {

    fun findUsedButNotImplemented(
        jsImplemented: Map<String, *>,
        jsPseudoImplemented: Map<String, *>
    ): HashSet<String> {

        val usedButNotImplemented = HashSet<String>(gIndex.actuallyUsed.usedBy.keys)

        for (func in jsImplemented) usedButNotImplemented.remove(func.key)
        for (func in jsPseudoImplemented) usedButNotImplemented.remove(func.key)
        for (func in gIndex.translatedMethods) usedButNotImplemented.remove(methodName(func.key))
        for ((name, dlu) in DelayedLambdaUpdate.needingBridgeUpdate) {
            if (name in dIndex.constructableClasses) {
                usedButNotImplemented.remove(methodName(dlu.calledMethod))
                usedButNotImplemented.remove(methodName(dlu.bridgeMethod))
            }
        }

        return usedButNotImplemented
    }

    fun checkUsedButNotImplemented(
        usedMethods: HashSet<String>,
        usedButNotImplemented: HashSet<String>
    ) {

        usedButNotImplemented.retainAll(usedMethods)

        val nameToMethod = calculateNameToMethod()
        val usedBotNotImplementedMethods =
            usedButNotImplemented.mapNotNull { nameToMethod[it] }

        for (sig in usedBotNotImplementedMethods) {
            if (sig.clazz in hIndex.interfaceClasses &&
                sig !in hIndex.jvmImplementedMethods &&
                sig !in hIndex.customImplementedMethods
            ) {
                usedButNotImplemented.remove(methodName(sig))
            }
            if (hIndex.isAbstract(sig)) {
                usedButNotImplemented.remove(methodName(sig))
            }
        }

        if (usedButNotImplemented.isNotEmpty()) {
            printMissingFunctions(usedButNotImplemented, usedMethods)
        }
    }

    fun printMissingFunctions(usedButNotImplemented: Set<String>, resolved: Set<String>) {
        println("\nMissing functions:")
        val nameToMethod = calculateNameToMethod()
        for (name in usedButNotImplemented) {
            println("  $name")
            println("    resolved: ${name in resolved}")
            val sig = hIndex.getAlias(name) ?: nameToMethod[name]
            if (sig != null) {
                println("    alias: " + hIndex.getAlias(name))
                println("    name2method: " + nameToMethod[name])
                println("    translated: " + (sig in gIndex.translatedMethods))
                print("    ")
                printUsed(sig)
            }
        }
        println()
        println("additional info:")
        printUsed(MethodSig.c("java/lang/reflect/Constructor", "getDeclaredAnnotations", "()[Ljava/lang/Object;"))
        printUsed(MethodSig.c("java/lang/reflect/Executable", "getDeclaredAnnotations", "()[Ljava/lang/Object;"))
        throw IllegalStateException("Missing ${usedButNotImplemented.size} functions")
    }

}