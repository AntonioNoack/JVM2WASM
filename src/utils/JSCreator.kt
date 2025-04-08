package utils

import jvm.JVMFlags.is32Bits
import org.apache.logging.log4j.LogManager

private val LOGGER = LogManager.getLogger("JSCreator")

fun writeJavaScriptImportsFile(
    jsImplemented: Map<String, Pair<MethodSig, String>>,
    jsPseudoImplemented: Map<String, MethodSig>,
    numPages: Int
) {
    LOGGER.info("[createJSImports]")
    val printer = StringBuilder2(256)
    fun jsHeader(name: String, sig: MethodSig) {
        printer.append("  ").append(name).append("(")
        // append parameters
        val desc = sig.descriptor
        for (i in desc.params.indices) {
            if (i > 0) printer.append(",")
            printer.append("arg").append(i)
        }
    }

    printer.append("var lib = {\n")
    printer.append("  // Implemented using @JavaScript(code=\"...\"):\n")
    for ((name, codeSig) in jsImplemented.toSortedMap()) {
        val (sig, js) = codeSig
        jsHeader(name, sig)
        printer.append(") {").append(js).append("},\n")
    }

    printer.append("  \n  // Not properly implemented:\n")
    for ((name, sig) in jsPseudoImplemented.toSortedMap()) {
        jsHeader(name, sig)
        printer.append(") { throw ('").append(name).append(" not implemented'); },\n")
    }
    printer.append("  \n  // Initially required memory in 64 kiB pages:\n")
    printer.append("  initialMemorySize: ").append(numPages).append(",\n")
    printer.append("  is32Bits: ").append(is32Bits).append('\n')
    printer.append("}\nexport { lib as \"autoJS\" }")
    wasmFolder.getChild("index0.js")
        .writeBytes(printer.values, 0, printer.size)
}