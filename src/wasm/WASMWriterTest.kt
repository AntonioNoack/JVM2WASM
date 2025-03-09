package wasm

import me.anno.utils.structures.arrays.ByteArrayList
import utils.*
import utils.WASMTypes.*
import wasm.parser.WATParser
import wasm.writer.*
import wasm.writer.Function

// this isn't working yet, and not fully implemented
fun main() {
    // load wasm.wat file
    val text = wasmTextFile.readTextSync()
    // tokenize it
    val parser = WATParser()
    parser.parse(text)

    val typesByFunctions = parser.functions
        .map { func -> wasm.instr.FuncType(func.params.map { it.wasmType }, func.results) }
    val typesByImports = parser.imports
        .map { func -> wasm.instr.FuncType(func.params.map { it.wasmType }, func.results) }
    val functionTypes = (typesByFunctions + typesByImports)
        .toHashSet().toList()

    val typeToType = mapOf(
        i32 to Type(TypeKind.I32),
        i64 to Type(TypeKind.I64),
        f32 to Type(TypeKind.F32),
        f64 to Type(TypeKind.F64),
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
            Global(listOf(), typeToType[i32]!!, it.isMutable)
        },
        parser.functions.withIndex()
            .filter { it.value.isExported }
            .map {
                // what is var_? function index
                Export(ExternalKind.FUNC, it.index)
            },
        emptyList() // starts???
    )

    val stream = ByteArrayList(1024)
    val writer = BinaryWriter(stream, module)
    writer.write()
    wasmFolder.getChild("jvm2wasm.test.wasm")
        .writeBytes(stream.values, 0, stream.size)

}