package wasm2cpp

import translator.JavaTypes.convertTypeToWASM
import utils.Param
import wasm.parser.FunctionImpl
import wasm.parser.GlobalVariable
import wasm2cpp.language.LowLevelCpp

fun defineFunctionHead(function: FunctionImpl, parameterNames: Boolean) {
    defineFunctionHead(function.funcName, function.params, function.results, parameterNames)
}

fun defineFunctionHead(funcName: String, params: List<Param>, results: List<String>, needsParameterNames: Boolean) {
    if (results.isEmpty()) {
        writer.append("void")
    } else {
        for (jvmType in results) {
            writer.append(convertTypeToWASM(jvmType))
        }
    }
    writer.append(' ').append(funcName).append('(')
    for (i in params.indices) {
        val param = params[i]
        if (i > 0) writer.append(", ")
        writer.append(param.wasmType)
        if (needsParameterNames) {
            writer.append(' ').append(param.name)
        }
    }
    writer.append(")")
}

fun defineFunctionImplementations(
    functions: List<FunctionImpl>, globals: Map<String, GlobalVariable>,
    functionsByName: Map<String, FunctionImpl>, pureFunctions: Set<String>
) {
    writer.append("// implementations\n")
    writer.append("#include <cmath> // trunc, ...\n")
    val stackToDeclarative = StackToDeclarative(globals, functionsByName, pureFunctions)
    val optimizer = DeclarativeOptimizer(globals)
    val functionWriter = FunctionWriter(globals, LowLevelCpp(writer))
    for (fi in functions.indices) {
        val function = functions[fi]
        // if (function.funcName != "org_joml_Vector4f_hashCode_I") continue
        val pos0 = writer.size
        try {
            val declarative = stackToDeclarative.write(function)
            val optimized = optimizer.write(function.withBody(declarative))
            functionWriter.write(function.withBody(optimized), true)
        } catch (e: Throwable) {
            println(writer.toString(pos0, writer.size))
            throw RuntimeException("Failed writing ${function.funcName}", e)
        }
    }
    writer.append('\n')
}
