package wasm

import me.anno.utils.structures.arrays.ByteArrayList
import wasm.parser.FunctionType
import wasm.parser.WATParser
import wasm.writer.*
import wasm.writer.Function
import wasm2cpp.tmp

fun main() {
    // load wasm.wat file
    val text = tmp.getChild("jvm2wasm.wat").readTextSync()
    // tokenize it
    val parser = WATParser()
    parser.parse(text)

    val typesByFunctions = parser.functions
        .map { FunctionType(it.params, it.results) }
    val typesByImports = parser.imports
        .map { FunctionType(it.params, it.results) }
    val functionTypes = (typesByFunctions + typesByImports)
        .toHashSet().toList()

    val typeToType = mapOf(
        "i32" to Type(TypeKind.I32),
        "i64" to Type(TypeKind.I64),
        "f32" to Type(TypeKind.F32),
        "f64" to Type(TypeKind.F64),
    )

    val module = Module(
        functionTypes.map { func ->
            FuncType(Sig(func.params.map {
                typeToType[it]!!
            }, func.results.map {
                typeToType[it]!!
            }))
        },
        parser.imports.map {
            // todo what is declIndex???
            // (import "jvm" "java_io_BufferedInputStream_close_V" (func $java_io_BufferedInputStream_close_V (param i32) (result i32)))
            FuncImport("jvm", it.funcName, 0)
        },
        parser.functions.map {
            Function(0)
        },
        // listOf(Table(FuncT))
        // listOf(Table(Type(TypeKind.FUNC_REF, 0), parser.functionTable), ), // tables
        emptyList(), // tables...
        parser.dataSections.map {
            // todo how do we set the data???
            Memory(Limits(false, false, false, 0L, 0L))
        },
        emptyList(), // tags???
        parser.globals.values.map {
            // todo set expression to i32.const <initial-value>
            Global(listOf(), typeToType["i32"]!!, it.isMutable)
        },
        parser.functions.map {
            // todo what is var_???
            Export(ExternalKind.FUNC, 0)
        },
        emptyList() // starts???
    )

    val stream = ByteArrayList(1024)
    val writer = BinaryWriter(stream, module)
    writer.write()
    tmp.getChild("jvm2wasm.test.wasm")
        .writeBytes(stream.values, 0, stream.size)

}