package wasm2cpp

import crashOnAllExceptions
import globals
import me.anno.io.files.FileReference
import me.anno.utils.Clock
import me.anno.utils.OS.documents
import org.apache.logging.log4j.LogManager
import utils.*
import wasm.parser.*
import wasm2cpp.Clustering.Companion.splitFunctionsIntoClusters

private val LOGGER = LogManager.getLogger("WASM2CPP")

var enableCppTracing = true

/**
 * if you want to change this, you need to change the C++ file list in CMakeLists.txt;
 * or change "disableClustersForInspection"
 * */
val numTargetClusters = 20
var disableClustersForInspection = true

val writer = StringBuilder2(1 shl 16)
val cppFolder = documents.getChild("IdeaProjects/JVM2WASM/cpp")

val clock = Clock(LOGGER)

// todo try using this on Android XD
// once everything here works, implementing a Zig or Rust implementation shouldn't be hard anymore

fun main() {
    wasm2cpp()
}

fun validate() {
    clock.start()
    val text = wasmTextFile.readTextSync()
    clock.stop("Loading WAT")
    val module = parseWAT(text)
    clock.stop("Parsing")
    ParserValidation.validate(module)
    clock.stop("Validating")
}

fun wasm2cpp() {

    // load wasm.wat file
    clock.start()
    val text = wasmTextFile.readTextSync()
    clock.stop("Loading WAT")
    val module = parseWAT(text)
    clock.stop("Parsing")
    ParserValidation.validate(module)
    clock.stop("Validating")

    compactBinaryData(module.dataSections)
    clock.stop("Compacting Binary Data")
    wasm2cpp(module.functions, module.functionTable, module.imports, module.globals)
    clock.stop("Transpiling")

    clock.total("WASM2CPP")
}

fun wasm2cppFromMemory() {
    val clock = Clock("WASM2CPP-FromMemory")
    compactBinaryData(dataSections)
    val functions = collectAllMethods(clock)
    clock.stop("Compacting Binary Data")
    wasm2cpp(functions, functionTable, imports, globals)
    clock.stop("WASM2CPP")
}

fun wasm2cpp(
    functions: ArrayList<FunctionImpl>, functionTable: List<String>,
    imports: List<Import>, globals: Map<String, GlobalVariable>
) {
    val functionsByName = createFunctionByNameMap(functions, imports)
    functions.removeIf { it.funcName.startsWith("getNth_") }
    writeHeader(functions, functionTable, imports, globals)
    clock.stop("Writing Header")
    val clusters = splitFunctionsIntoClusters(functions, numTargetClusters)
    for (i in clusters.indices) {
        writeCluster(i, clusters[i], globals, functionsByName)
        clock.stop("Writing Cluster [$i]")
    }
    writeFuncTable(functions, functionTable)
    clock.stop("Writing FuncTable")
}

fun defineReturnStructs(functions: Collection<FunctionImpl>) {
    writer.append("// return-structs\n")
    for (typeList in functions
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

fun defineFunctionHeads(functions: Collection<FunctionImpl>) {
    writer.append("// function heads\n")
    for (function in functions.sortedWith(FunctionOrder)) {
        defineFunctionHead(function, false)
        writer.append(";\n")
    }
    writer.append('\n')
}

fun defineImports(imports: List<Import>) {
    writer.append("// imports\n")
    for (import in imports.sortedWith(FunctionOrder)) {
        defineFunctionHead(import, false)
        writer.append(";\n")
    }
    writer.append('\n')
}

fun defineGlobals(globals: Map<String, GlobalVariable>) {
    writer.append("// globals\n")
    val sortedGlobals = globals.entries
        .sortedBy { it.key }.map { it.value }
    for (global in sortedGlobals) {
        if (!global.isMutable) {
            writer.append("constexpr ").append(global.wasmType).append(' ').append(global.fullName)
                .append(" = ").append(global.initialValue).append(";\n")
        }
    }
    writer.append("#ifdef MAIN_CPP\n")
    for (global in sortedGlobals) {
        if (global.isMutable) {
            writer.append(global.wasmType).append(' ').append(global.fullName)
                .append(" = ").append(global.initialValue).append(";\n")
        }
    }
    writer.append("#else\n")
    for (global in sortedGlobals) {
        if (global.isMutable) {
            writer.append("extern ").append(global.wasmType).append(' ').append(global.fullName).append(";\n")
        }
    }
    writer.append("#endif\n")
    writer.append("\n")
}

fun fillInFunctionTable(functionTable: List<String>) {
    writer.append("// function table data\n")
    writer.append("void initFunctionTable() {\n")
    for (i in functionTable.indices) {
        writer.append("  indirect[").append(i).append("] = (void*) ")
            .append(functionTable[i]).append(";\n")
    }
    writer.append("}\n")
}

fun compactBinaryData(dataSections: List<DataSection>) {
    // todo can we pack this data into the .exe somehow???
    val dataSize = dataSections.maxOfOrNull { it.startIndex + it.content.size } ?: 0
    val data = ByteArray(dataSize)
    for (section in dataSections) {
        section.content.copyInto(data, section.startIndex)
    }
    cppFolder.getChild("runtime-data.bin")
        .writeBytes(data)
}

fun parseWAT(text: String): Module {
    val parser = WATParser()
    parser.parse(text)
    return parser
}

private fun createFunctionByNameMap(
    functions: ArrayList<FunctionImpl>, imports: List<Import>
): Map<String, FunctionImpl> {
    val size = functions.size + imports.size
    val functionsByName = HashMap<String, FunctionImpl>(size)
    for (i in functions.indices) {
        val func = functions[i]
        functionsByName[func.funcName] = func
    }
    for (i in imports.indices) {
        val import = imports[i]
        functionsByName[import.funcName] = import
    }
    return functionsByName
}

fun writeHeader(
    functions: Collection<FunctionImpl>,
    functionTable: List<String>,
    imports: List<Import>,
    globals: Map<String, GlobalVariable>
) {

    writer.append("// imports\n")
    writer.append("#include <string>\n") // for debugging
    writer.append("#include \"jvm2wasm-types.h\"\n") // for debugging
    if (crashOnAllExceptions) writer.append("#define NO_ERRORS\n")
    writer.append('\n')

    writer.append("// header\n")
    writer.append("#ifdef MAIN_CPP\n")
    writer.append("  void* memory = nullptr;\n")
    writer.append("  void* indirect[").append(functionTable.size).append("];\n")
    writer.append("#else\n")
    writer.append("  extern void* memory;\n")
    writer.append("  extern void* indirect[").append(functionTable.size).append("];\n")
    writer.append("#endif\n")
    writer.append("[[noreturn]] void unreachable(std::string);\n")
    writer.append('\n')

    defineGlobals(globals)
    defineReturnStructs(functions)
    defineImports(imports)

    cppFolder.getChild("jvm2wasm-base.h")
        .writeBytes(writer.values, 0, writer.size)
    writer.clear()

}

fun writeCluster(
    i: Int, clustering: Clustering, globals: Map<String, GlobalVariable>,
    functionsByName: Map<String, FunctionImpl>
) {
    writer.append("#include \"jvm2wasm-base.h\"\n\n")
    defineFunctionHeads(clustering.imports)
    defineFunctionImplementations(clustering.functions, globals, functionsByName)
    getClusterFile(i).writeBytes(writer.values, 0, writer.size)
    writer.clear()
}

fun getClusterFile(i: Int): FileReference {
    return cppFolder.getChild("jvm2wasm-part$i.cpp")
}

fun writeFuncTable(functions: Collection<FunctionImpl>, functionTable: List<String>) {
    writer.append("#include \"jvm2wasm-base.h\"\n\n")
    val functionTableNames = functionTable.toHashSet()
    defineFunctionHeads(functions.filter { it.funcName in functionTableNames })
    try {
        fillInFunctionTable(functionTable)
    } catch (e: Throwable) {
        e.printStackTrace()
    }
    cppFolder.getChild("jvm2wasm-funcTable.cpp")
        .writeBytes(writer.values, 0, writer.size)
    writer.clear()
}