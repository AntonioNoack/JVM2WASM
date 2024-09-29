package wasm2cpp

import me.anno.utils.Clock
import me.anno.utils.OS.documents
import utils.StringBuilder2
import wasm.parser.FunctionImpl
import wasm.parser.WATParser

var enableCppTracing = false

// todo try using this on Android XD
// once everything here works, implementing a Zig or Rust implementation shouldn't be hard anymore

val functionsByName = HashMap<String, FunctionImpl>()

fun main() {
    wasm2cpp()
}

fun makeFloat(str: String): String {
    return if ('.' !in str) "$str.0" else str
}

val writer = StringBuilder2(1024)
val tmp = documents.getChild("IdeaProjects/JVM2WASM/tmp")

fun defineTypes() {
    writer.append("// types\n")
    writer.append("#include <cstdint>\n") // for number types
    writer.append("#include <bit>\n") // bitcast from C++20
    writer.append("#include <string>\n") // for debugging
    val map = listOf(
        "i32" to "int32_t",
        "i64" to "int64_t",
        "u32" to "uint32_t",
        "u64" to "uint64_t",
        "f32" to "float",
        "f64" to "double"
    )
    for ((wasm, cpp) in map) {
        writer.append("typedef ").append(cpp).append(' ').append(wasm).append(";\n")
    }
    writer.append("\n")
}

fun defineReturnStructs(parser: WATParser) {
    writer.append("// return-structs\n")
    for (typeList in parser.functions
        .map { it.results }.filter { it.size > 1 }
        .toHashSet().sortedBy { it.size }) {
        // define return struct
        val name = typeList.joinToString("")
        writer.append("struct ").append(name).append(" {")
        for (ki in typeList.indices) {
            val ni = typeList[ki]
            writer.append(" ").append(ni)
                .append(' ').append("v").append(ki).append(";")
        }
        writer.append(" };\n")
    }
    writer.append("\n")
}

fun defineFunctionHeads(parser: WATParser) {
    writer.append("// function heads\n")
    for (function in parser.functions.sortedBy {
        it.results.size.toString() + it.funcName
    }) {
        defineFunctionHead(function, false)
        writer.append(";\n")
    }
    writer.append('\n')
}

fun defineImports(parser: WATParser) {
    writer.append("// imports\n")
    for (import in parser.imports.sortedBy {
        it.results.size.toString() + it.funcName
    }) {
        val func = FunctionImpl(import.funcName, import.params, import.results, emptyList(), emptyList())
        defineFunctionHead(func, false)
        writer.append(";\n")
        functionsByName[import.funcName] = func
    }
    writer.append('\n')
}

fun defineGlobals(parser: WATParser) {
    writer.append("// globals\n")
    for ((_, global) in parser.globals.entries.sortedBy { it.key }) {
        writer.append(global.type).append(' ').append(global.name)
            .append(" = ").append(global.initialValue).append(";\n")
    }
    writer.append("\n")
}

fun fillInFunctionTable(parser: WATParser) {
    writer.append("// function table data\n")
    writer.append("void initFunctionTable() {\n")
    val functionTable = parser.functionTable
    for (i in functionTable.indices) {
        writer.append("  indirect[").append(i).append("] = (void*) ")
            .append(functionTable[i]).append(";\n")
    }
    writer.append("}\n")
}

fun wasm2cpp() {

    val clock = Clock("WASM2CPP")

    // load wasm.wat file
    val text = tmp.getChild("jvm2wasm.wat").readTextSync()
    clock.stop("Loading WAT")

    // tokenize it
    val parser = WATParser()
    parser.parse(text)
    clock.stop("Parsing")

    for (func in parser.functions) {
        functionsByName[func.funcName] = func
    }
    parser.functions.removeIf { it.funcName.startsWith("getNth_") }

    tmp.getChild("data").delete()
    tmp.getChild("data").mkdirs()
    for (section in parser.dataSections) {
        tmp.getChild("data/jvm2wasm-data-${section.startIndex}-${section.startIndex + section.content.size}.bin")
            .writeBytes(section.content)
    }

    // produce a compilable .cpp from it
    writer.append("// header\n")
    writer.append("void* memory = nullptr;\n")
    writer.append("void* indirect[").append(parser.functionTable.size).append("];\n")
    defineTypes()
    defineGlobals(parser)
    defineReturnStructs(parser)
    val pos = writer.size
    defineImports(parser)
    tmp.getChild("jvm2wasm-base.h")
        .writeBytes(writer.values, pos, writer.size - pos)
    defineFunctionHeads(parser)

    writer.append("void unreachable(std::string);\n")
    writer.append("void notifySampler(std::string funcName);\n")

    try {
        /* parser.functions.removeIf {
             it.funcName != "me_anno_gpu_deferred_DeferredSettings_appendLayerWriters_Ljava_lang_StringBuilderLme_anno_utils_structures_arrays_BooleanArrayListZV"
         }*/
        defineFunctionImplementations(parser)
        fillInFunctionTable(parser)
    } catch (e: Throwable) {
        e.printStackTrace()
    }

    clock.stop("Transpiling")

    tmp.getChild("jvm2wasm.cpp")
        .writeBytes(writer.values, 0, writer.size)

    clock.total("WASM2CPP")
}