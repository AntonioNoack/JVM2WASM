package wasm2js

import gIndex
import globals
import hIndex
import jvm2wasm
import me.anno.utils.Clock
import me.anno.utils.OS.documents
import utils.NativeTypes
import utils.collectAllMethods
import utils.functionTable
import utils.imports
import wasm.parser.FunctionImpl
import wasm.parser.GlobalVariable
import wasm.parser.Import
import wasm2cpp.*
import wasm2cpp.language.LowLevelJavaScript

val jsFolder = documents.getChild("IdeaProjects/JVM2WASM/targets/javascript")

fun main() {
    val clock = Clock("JVM2JS")
    jvm2wasm()
    val testWATParser = true
    if (testWATParser) {
        validateWAT()
    }
    wasm2jsFromMemory()
    clock.total("JVM2JS")
}

fun wasm2jsFromMemory() {
    // todo implement high-level JavaScript generation
    val functions = collectAllMethods(clock)
    wasm2js(functions, functionTable, imports, globals)
    clock.stop("WASM2JS")
}

fun classNameToJS(jvmType: String): String {
    return jvmType
        .replace("/", "_")
        .replace("-", "_")
}

fun wasm2js(
    functions: ArrayList<FunctionImpl>, functionTable: List<String>,
    imports: List<Import>, globals: Map<String, GlobalVariable>
) {
    val functionsByName = createFunctionByNameMap(functions, imports)
    functions.removeIf { it.funcName.startsWith("getNth_") }
    writeHeader(functions, functionTable, imports, globals)
    clock.stop("Writing Header")
    val pureFunctions = PureFunctions(imports, functions, functionsByName).findPureFunctions()
    clock.stop("Finding Pure Functions")
    writer.append("\"use strict\";\n\n")

    // todo group them by package...
    val classNames = gIndex.classNamesByIndex
    for (classId in classNames.indices) {
        val className = classNames[classId]
        if (className.startsWith("[") || className in NativeTypes.nativeTypes) continue
        writer.append("class ").append(classNameToJS(className))
        val superClass = hIndex.superClass[className]
        if (superClass != null) writer.append(" extends ").append(classNameToJS(superClass))
        writer.append(" {\n")

        // todo write functions/function links here
        // todo if a function is already defined in the parent class, skip it
        // todo if a function is a default-interface-function, link to it via a bridge method somehow...
        // todo we need to handle super-calls properly, too, with super.(), which we removed...

        writer.append("}\n\n")
    }

    // todo instead of structs and func-table, need to define the class and method resolution structure
    // todo instead of createArray, create the corresponding arrays
    // todo replace array length with .length
    // todo replace strings with JavaScript strings
    // todo replace field getters and setters with field-access
    // todo write class instances as pre-defined JavaScript objects, maybe a .json?

    clock.stop("Append FunctionTable")
    defineFunctionImplementations(functions, globals, functionsByName, pureFunctions)
    jsFolder.getChild("jvm2js.js").write()
    clock.stop("Writing Functions")
}

fun defineFunctionImplementations(
    functions: List<FunctionImpl>, globals: Map<String, GlobalVariable>,
    functionsByName: Map<String, FunctionImpl>, pureFunctions: Set<String>
) {
    val stackToDeclarative = StackToDeclarative(globals, functionsByName, pureFunctions)
    val optimizer = DeclarativeOptimizer(globals)
    val functionWriter = FunctionWriter(globals, LowLevelJavaScript(writer))
    for (fi in functions.indices) {
        val function = functions[fi]
        val pos0 = writer.size
        try {
            val declarative = stackToDeclarative.write(function)
            val optimized = optimizer.write(function.withBody(declarative))
            functionWriter.write(function.withBody(optimized))
        } catch (e: Throwable) {
            println(writer.toString(pos0, writer.size))
            throw RuntimeException("Failed writing ${function.funcName}", e)
        }
    }
    writer.append('\n')
}
