package utils

import dIndex
import gIndex
import hIndex
import me.anno.utils.OS
import me.anno.utils.types.Floats.f3
import me.anno.utils.types.Strings.distance
import t0
import java.io.InputStream
import kotlin.concurrent.thread
import kotlin.math.min

val builder = Builder(512)
fun printUsed(sig: MethodSig) {
    builder.append(sig).append(":")
    if (sig in (hIndex.methods[sig.clazz] ?: emptySet())) builder.append(" known")
    if (sig in dIndex.usedMethods) builder.append(" used")
    else {
        // check all super classes
        var parent = hIndex.superClass[sig.clazz]
        while (parent != null) {
            val sig2 = MethodSig.c(parent, sig.name, sig.descriptor)
            if (sig2 in dIndex.usedMethods) {
                builder.append(" used-by-$parent")
                break
            }
            parent = hIndex.superClass[parent]
        }
    }

    if (sig in hIndex.jvmImplementedMethods) builder.append(" jvm-implemented")
    if (sig in hIndex.notImplementedMethods) builder.append(" not-implemented")
    if (sig in hIndex.staticMethods) builder.append(" static")
    if (sig in hIndex.nativeMethods) builder.append(" native")
    if (sig in hIndex.abstractMethods) builder.append(" abstract")
    if (sig in hIndex.finalMethods) builder.append(" final")
    if (sig in dIndex.methodsWithForbiddenDependencies) builder.append(" forbidden")

    fun handleInterfaces(clazz: String): Boolean {
        val interfaces = hIndex.interfaces[clazz]
        if (interfaces != null && interfaces.any {
                MethodSig.c(it, sig.name, sig.descriptor) in dIndex.usedInterfaceCalls
            }) {
            builder.append(" used-as-interface")
            if (clazz != sig.clazz) builder.append("[$clazz]")
            return true
        }

        val superClass = hIndex.superClass[clazz]
        if (superClass != null && handleInterfaces(superClass))
            return true

        if (interfaces != null) {
            for (interfaceI in interfaces) {
                if (handleInterfaces(interfaceI))
                    return true
            }
        }

        return false
    }

    if (!handleInterfaces(sig.clazz))
        if (sig in dIndex.knownInterfaceDependencies) builder.append(" interface")

    if (sig.clazz in dIndex.constructableClasses) builder.append(" constructable")

    val name = methodName(sig)
    if (name in gIndex.actuallyUsed.resolved) builder.append(" actually-used(${gIndex.actuallyUsed.usedBy[name]})")
    val mapsTo = hIndex.methodAliases[name]
    if (mapsTo != null && mapsTo != sig) builder.append(" maps-to: ").append(mapsTo)
    val mappedBy = hIndex.methodAliases.entries.filter { /*it.key != name &&*/ it.value == sig }.map { it.key }
    if (mappedBy.isNotEmpty()) builder.append(" mapped-by: ").append(mappedBy)
    var usedBy = dIndex.methodDependencies.entries.filter { sig != it.key && sig in it.value }.map { it.key }
    if (sig in dIndex.usedMethods) usedBy = usedBy.filter { it in dIndex.usedMethods }
    if (usedBy.isNotEmpty()) builder.append(" used-by: ").append(usedBy.subList(0, min(usedBy.size, 10)))
    val uses = dIndex.methodDependencies[sig]
    if (!uses.isNullOrEmpty()) builder.append(" uses: ").append(uses)
    if (builder.endsWith(":")) {
        val str = sig.toString()
        val closestMatch = hIndex.methods.values.flatten().minByOrNull { str.distance(it.toString()) }
        builder.append(" closest-match: $closestMatch")
    }
    println(builder.toString())
    builder.clear()
    if (mapsTo != null && mapsTo != sig)
        printUsed(mapsTo)
}

fun compileToWASM(printer: StringBuilder2) {

    val tmp = OS.documents.getChild("IdeaProjects/JVM2WASM/src/tmp/jvm2wasm.wat")
    tmp.outputStream().use {
        it.write(printer.array, 0, printer.size)
    }

    println("#strings: ${gIndex.stringSet.size}, size: ${gIndex.totalStringSize}")

    val t1 = System.nanoTime()
    println("total Kotlin time: ${((t1 - t0) * 1e-9).f3()}s")

    val process = Runtime.getRuntime().exec("wsl")
    printAsync(process.inputStream, false)
    printAsync(process.errorStream, true)
    val stream = process.outputStream
    stream.write("cd ~\n".toByteArray())
    stream.write("./comp.sh\n".toByteArray())
    stream.close()
    process.waitFor()

    val t2 = System.nanoTime()
    println("total time: ${((t2 - t0) * 1e-9).f3()}s")

}

private fun printAsync(input: InputStream, err: Boolean) {
    thread {
        val reader = input.bufferedReader()
        while (true) {
            var line = reader.readLine() ?: break
            line = " - $line"
            if (err) {
                System.err.println(line)
            } else {
                println(line)
            }
        }
    }
}