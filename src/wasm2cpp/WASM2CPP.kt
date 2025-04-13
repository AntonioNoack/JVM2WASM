package wasm2cpp

import crashOnAllExceptions
import dIndex
import gIndex
import globals
import hIndex
import jvm.JVMFlags.is32Bits
import me.anno.io.files.FileReference
import me.anno.utils.Clock
import me.anno.utils.OS.documents
import me.anno.utils.assertions.assertEquals
import org.apache.logging.log4j.LogManager
import translator.GeneratorIndex
import utils.*
import utils.WASMTypes.*
import wasm.parser.*
import wasm2cpp.Clustering.Companion.splitFunctionsIntoClusters

private val LOGGER = LogManager.getLogger("WASM2CPP")

var generateHighLevelCpp = true

var enableCppTracing = true

/**
 * if you want to change this, you need to change the C++ file list in CMakeLists.txt;
 * or change "disableClustersForInspection"
 * */
val numTargetClusters = 20
var disableClustersForInspection = true

val writer = StringBuilder2(1 shl 16)
val cppFolder = documents.getChild("IdeaProjects/JVM2WASM/targets/cpp")

val clock = Clock(LOGGER)

// todo try using this on Android XD
// once everything here works, implementing a Zig or Rust implementation shouldn't be hard anymore

fun main() {
    if (generateHighLevelCpp) {
        throw IllegalStateException("Cannot generate high-level C++ from WASM (yet?)")
    }
    wasm2cpp()
}

fun validateWAT() {
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
    clock.stop("Clustering")
    val pureFunctions = PureFunctions(imports, functions, functionsByName).findPureFunctions()
    clock.stop("Finding Pure Functions")
    for (i in clusters.indices) {
        writeCluster(i, clusters[i], globals, functionsByName, pureFunctions)
        clock.stop("Writing Cluster [$i]")
    }
    writeStructs()
    writeFuncTable(functions, functionTable)
    writeNativeLogFunctions(imports)
    clock.stop("Writing FuncTable")
}

fun jvmNameToCppName(className: String): String {
    return className.escapeChars()
}

fun jvmNameToCppName2(className: String): String {
    return when (className) {
        "int" -> i32
        "long" -> i64
        "float" -> f32
        "double" -> f64
        "boolean", "byte" -> "uint8_t"
        "char" -> "uint16_t"
        "short" -> "int16_t"
        else -> ptrType
    }
}

fun writeStructField(name: String, field: GeneratorIndex.FieldData) {
    writer.append("  ")
    if (is32Bits || field.type in NativeTypes.nativeTypes) {
        writer.append(jvmNameToCppName2(field.type))
    } else if (!field.type.startsWith("[") || field.type in NativeTypes.nativeArrays) {
        val cppType = structNames[gIndex.getClassIdOrParents(field.type)]
        writer.append("struct ").append(cppType).append('*')
    } else {
        val cppType = structNames[StaticClassIndices.OBJECT_ARRAY]
        writer.append("struct ").append(cppType).append('*')
    }
    var nameI = name
    while (nameI in FunctionWriter.cppKeywords) nameI += "_"
    writer.append(' ').append(nameI).append("; /* @").append(field.offset)
        .append(", ").append(field.type).append(" */\n")
}

private val structNames = ArrayList<String>(512)
fun writeStruct(className: String, classId: Int) {
    // todo define inheritance??? cannot be supported for super-class-padding-used-for-fields
    // for each constructable type, create a struct
    // extends parentName = ": public parentName"
    writer.append("struct ").append(structNames[classId])
    val superClass = hIndex.superClass[className] ?: "java/lang/Object"
    val superClassId = gIndex.getClassId(superClass)
    if (className != "java/lang/Object") {
        writer.append(" : public ").append(structNames[superClassId])
    }
    writer.append(" {\n")
    val fields = gIndex.getFieldOffsets(className, static = false)
    val sortedFields =
        fields.fields.entries.sortedBy { it.value.offset }
    for ((name, field) in sortedFields) {
        writeStructField(name, field)
    }
    writer.append("};\n\n")
}

// todo static-assert all offsets
//  (or create an init-function, that overrides them and instance sizes at start?)

fun writeStaticStruct(className: String) {
    // for each constructable type, create a struct
    // extends parentName = ": public parentName"
    val classNameCpp = jvmNameToCppName(className)
    writer.append("struct static_").append(classNameCpp).append(" {\n")
    val fields = gIndex.getFieldOffsets(className, static = true)
    val sortedFields =
        fields.fields.entries.sortedBy { it.value.offset }
    for ((name, field) in sortedFields) {
        writeStructField(name, field)
    }
    writer.append("} Static_").append(classNameCpp).append(";\n\n")
}

fun writeStructs() {
    val structFile = cppFolder.getChild("jvm2wasm-structs.hpp")
    if (!generateHighLevelCpp) {
        structFile.writeText("// structs are disabled")
        return
    }

    writer.append("#include \"jvm2wasm-types.h\"\n\n")

    val classNames = gIndex.classNamesByIndex
    for (classId in classNames.indices) {
        val className = classNames[classId]
        if (className in dIndex.constructableClasses &&
            className !in NativeTypes.nativeTypes &&
            gIndex.getFieldOffsets(className, false).fields.isNotEmpty()
        ) {
            val cppName = jvmNameToCppName(className)
            writer.append("struct ").append(cppName).append(";\n")
            structNames.add(cppName)
        } else {
            val superClass = hIndex.superClass[className] ?: "java/lang/Object"
            val superClassId = gIndex.getClassId(superClass)
            structNames.add(structNames[superClassId])
        }
    }
    writer.append("\n")

    for (classId in classNames.indices) {
        val className = classNames[classId]
        if (className in dIndex.constructableClasses &&
            className !in NativeTypes.nativeTypes &&
            gIndex.getFieldOffsets(className, false).fields.isNotEmpty()
        ) {
            writeStruct(className, classId)
        }
        val staticFields = gIndex.getFieldOffsets(className, true)
        if (staticFields.fields.isNotEmpty()) {
            writeStaticStruct(className)
        }
    }
    // todo for each valid static field, create a static-struct-thingy?

    structFile.write()
}

fun FileReference.write() {
    writeBytes(writer.values, 0, writer.size)
    writer.clear()
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

fun createFunctionByNameMap(
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
    if (is32Bits) writer.append("#define IS32BITS\n")
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

    cppFolder.getChild("jvm2wasm-base.h").write()
}

fun writeCluster(
    i: Int, clustering: Clustering, globals: Map<String, GlobalVariable>,
    functionsByName: Map<String, FunctionImpl>, pureFunctions: Set<String>
) {
    writer.append("#include \"jvm2wasm-base.h\"\n\n")
    defineFunctionHeads(clustering.imports)
    defineFunctionImplementations(clustering.functions, globals, functionsByName, pureFunctions)
    getClusterFile(i).write()
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
    cppFolder.getChild("jvm2wasm-funcTable.cpp").write()
}

fun writeNativeLogFunctions(imports: List<Import>) {
    val prefix = "jvm_NativeLog_log_"
    val suffix = "V"

    writer.append("#include <string>\n")
    writer.append("#include <iostream>\n")
    writer.append("#include \"jvm2wasm-types.h\"\n\n")

    writer.append("std::string strToCpp(i64 addr);\n\n")

    for (i in imports.indices) {
        val func = imports[i]
        val name = func.funcName
        if (!name.startsWith(prefix) || !name.endsWith(suffix)) continue

        // extract params from name, so we can translate WASM directly
        val paramsFromName =
            name.substring(prefix.length, name.length - suffix.length)
                .replace("Ljava_lang_String", "$")
                .replace("Ljvm_Pointer64", "J")
                .replace("Ljvm_Pointer", "I")

        assertEquals(paramsFromName.length, func.params.size) {
            "Expected to parse ${func.params}, but only got $paramsFromName, '$name'"
        }

        writer.append("void ").append(name).append("(")
        for (j in paramsFromName.indices) {
            val param = func.params[j]
            if (j > 0) writer.append(", ")
            writer.append(param.wasmType).append(" arg").append(j)
        }
        writer.append(") {\n")
        writer.append("  std::cout")

        for (j in paramsFromName.indices) {
            writer.append(" << ")
            if (paramsFromName[j] == '$') {
                writer.append("strToCpp(arg").append(j).append(")")
            } else {
                writer.append("arg").append(j)
            }
        }

        writer.append(" << std::endl;\n")
        writer.append("}\n")
    }

    cppFolder.getChild("jvm2wasm-nativeLog.cpp").write()
}