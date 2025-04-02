package wasm2cpp

import utils.Param
import wasm.parser.FunctionImpl
import wasm.parser.GlobalVariable

fun defineFunctionHead(function: FunctionImpl, parameterNames: Boolean) {
    defineFunctionHead(function.funcName, function.params, function.results, parameterNames)
}

fun defineFunctionHead(funcName: String, params: List<Param>, results: List<String>, parameterNames: Boolean) {
    if (results.isEmpty()) {
        writer.append("void")
    } else {
        for (ri in results) {
            writer.append(ri)
        }
    }
    writer.append(' ').append(funcName).append('(')
    for (i in params.indices) {
        val param = params[i]
        if (i > 0) writer.append(", ")
        writer.append(param.wasmType)
        if (parameterNames) {
            writer.append(' ').append(param.name)
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
    val stackToDeclarative = StackToDeclarative(globals, functionsByName)
    val functionWriter = FunctionWriter(globals)
    for (fi in functions.indices) {
        val function = functions[fi]
        val pos0 = writer.size
        try {
            val declarative = stackToDeclarative.write(function)
            functionWriter.write(function.withBody(declarative))
        } catch (e: Throwable) {
            println(writer.toString(pos0, writer.size))
            throw RuntimeException("Failed writing ${function.funcName}", e)
        }
    }
    writer.append('\n')
}
