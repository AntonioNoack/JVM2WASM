package utils

fun createJSImports(
    jsImplemented: Map<String, Pair<MethodSig, String>>,
    jsPseudoImplemented: Map<String, MethodSig>,
    numPages: Int
) {
    val jsFileText = StringBuilder2(256)
    fun jsHeader(name: String, sig: MethodSig) {
        jsFileText.append("  ").append(name).append("(")
        // append parameters
        val desc = sig.descriptor
        val di = desc.lastIndexOf(')')
        for (i in split1(desc.substring(1, di)).indices) {
            if (i > 0) jsFileText.append(",")
            jsFileText.append("arg").append(i)
        }
    }

    jsFileText.append("var lib = {\n")
    jsFileText.append("  // Implemented using @JavaScript(code=\"...\"):\n")
    for ((name, codeSig) in jsImplemented.toSortedMap()) {
        val (sig, js) = codeSig
        jsHeader(name, sig)
        jsFileText.append(") {").append(js).append("},\n")
    }

    jsFileText.append("  \n  // Not properly implemented:\n")
    for ((name, sig) in jsPseudoImplemented.toSortedMap()) {
        jsHeader(name, sig)
        jsFileText.append(") { throw ('").append(name).append(" not implemented'); },\n")
    }
    jsFileText.append("  \n  // Initially required memory in 64 kiB pages:\n")
    jsFileText.append("  initialMemorySize: ").append(numPages).append('\n')
    jsFileText.append("}\nexport { lib as \"autoJS\" }")
    dstFolder.getChild("index0.js")
        .writeBytes(jsFileText.values, 0, jsFileText.size)
}