package wasm2cpp

import wasm.parser.FunctionImpl
import wasm.parser.WATParser

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

fun defineFunctionImplementations(parser: WATParser) {
    writer.append("// implementations\n")
    writer.append("#include <cmath> // trunc, ...\n")
    val functions = parser.functions
    for (fi in functions.indices) {
        val function = functions[fi]
        val pos0 = writer.size
        try {
            FunctionWriter(function, parser)
        } catch (e: Throwable) {
            println(writer.toString(pos0, writer.size))
            throw RuntimeException("Failed writing ${function.funcName}", e)
        }
    }
    writer.append('\n')
}

val ignoredFuncNames = ("r8,r16,r32," +
        "s8ArrayLoad,u16ArrayLoad,i32ArrayLoad,i64ArrayLoad," +
        "s8ArrayStore,i16ArrayStore,i32ArrayStore,i64ArrayStore," +
        "f32ArrayStore,f64ArrayStore,f32ArrayLoad,f64ArrayLoad," +
        "s8ArrayLoadU,u16ArrayLoadU,i32ArrayLoadU,i64ArrayLoadU," +
        "s8ArrayStoreU,i16ArrayStoreU,i32ArrayStoreU,i64ArrayStoreU," +
        "f32ArrayStoreU,f64ArrayStoreU,f32ArrayLoadU,f64ArrayLoadU,").split(',')
