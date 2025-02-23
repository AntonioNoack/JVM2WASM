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
        "s8ArrayLoad,u16ArrayLoad,s16ArrayLoad,i32ArrayLoad,i64ArrayLoad," +
        "s8ArrayStore,i16ArrayStore,i32ArrayStore,i64ArrayStore," +
        "f32ArrayStore,f64ArrayStore,f32ArrayLoad,f64ArrayLoad," +
        "s8ArrayLoadU,u16ArrayLoadU,s16ArrayLoadU,i32ArrayLoadU,i64ArrayLoadU," +
        "i8ArrayStoreU,i16ArrayStoreU,i32ArrayStoreU,i64ArrayStoreU," +
        "f32ArrayStoreU,f64ArrayStoreU,f32ArrayLoadU,f64ArrayLoadU," +
        "getStackDepth,stackPush,stackPop," +
        "getStaticFieldS8,getFieldS8,setStaticFieldI8,setFieldI8," +
        "getStaticFieldS16,getFieldS16,setStaticFieldI16,setFieldI16," +
        "getStaticFieldU16,getFieldU16," +
        "getStaticFieldI32,getFieldI32,setStaticFieldI32,setFieldI32," +
        "getStaticFieldI64,getFieldI64,setStaticFieldI64,setFieldI64," +
        "getStaticFieldF32,getFieldF32,setStaticFieldF32,setFieldF32," +
        "getStaticFieldF64,getFieldF64,setStaticFieldF64,setFieldF64," +
        "resolveIndirect,panic,dup_x1i32i32,arrayLengthU,").split(',').toHashSet()
