package wasm2cpp

import wasm.parser.FunctionImpl
import wasm.parser.GlobalVariable

fun defineFunctionHead(function: FunctionImpl, parameterNames: Boolean) {
    defineFunctionHead(function.funcName, function.params, function.results, parameterNames)
}

fun defineFunctionHead(funcName: String, params: List<String>, results: List<String>, parameterNames: Boolean) {
    if (results.isEmpty()) {
        writer.append("void")
    } else {
        for (ri in results) {
            writer.append(ri)
        }
    }
    writer.append(' ').append(funcName).append('(')
    for (i in params.indices) {
        val pi = params[i]
        if (i > 0) writer.append(", ")
        writer.append(pi)
        if (parameterNames) {
            writer.append(" p").append(i)
        }
    }
    writer.append(")")
}

fun defineFunctionImplementations(
    functions: List<FunctionImpl>, globals: Map<String, GlobalVariable>,
    functionsByName: Map<String, FunctionImpl>
) {
    writer.append("// implementations\n")
    writer.append("#include <cmath> // trunc, ...\n")
    val tested =
        "org_apache_logging_log4j_LoggerImpl_interleaveImpl_Ljava_lang_StringALjava_lang_ObjectLjava_lang_String"
    val functions1 = functions// .filter { it.funcName == tested }
    for (fi in functions1.indices) {
        val function = functions1[fi]
        val pos0 = writer.size
        try {
            FunctionWriter(function, globals, functionsByName).write()
            // throw IllegalStateException("case5 is/was broken")
        } catch (e: Throwable) {
            println(writer.toString(pos0, writer.size))
            throw RuntimeException("Failed writing ${function.funcName}", e)
        }
    }
    writer.append('\n')
}
