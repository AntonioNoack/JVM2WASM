package wasm2js

import dIndex
import gIndex
import globals
import hIndex
import jvm2wasm
import me.anno.utils.Clock
import me.anno.utils.OS.documents
import utils.*
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
    val functionByName = createFunctionByNameMap(functions, imports)
    functions.removeIf { it.funcName.startsWith("getNth_") || it.funcName.startsWith("tree_") }
    writeHeader(functions, functionTable, imports, globals)
    clock.stop("Writing Header")
    val pureFunctions = PureFunctions(imports, functions, functionByName).findPureFunctions()
    clock.stop("Finding Pure Functions")
    writer.append("\"use strict\";\n\n")

    val methodsByClass =
        dIndex.usedMethods.filter { sig ->
            hasBeenImplemented(sig) &&
                    isConstructableOrStatic(sig) &&
                    isCallable(sig)
        }.groupBy { it.clazz }

    // todo group them by package...
    val classNames = gIndex.classNamesByIndex
    for (classId in classNames.indices) {
        val className = classNames[classId]
        if (className.startsWith("[") || className in NativeTypes.nativeTypes) continue
        writer.append("class ").append(classNameToJS(className))
        val superClass = hIndex.superClass[className]
        if (superClass != null) writer.append(" extends ").append(classNameToJS(superClass))
        writer.append(" {\n")

        // todo declare static fields, so we can use them

        // write functions/function links here
        val methods = methodsByClass[className] ?: emptyList()
        for (method in methods.sortedBy { it.name }) {
            val func = functionByName[methodName(method)] ?: continue
            if (func is Import) continue // todo link to import/write imported implementation
            val shortName = shortName(method.name, method.descriptor.raw)
            val isStatic = hIndex.isStatic(method)
            defineFunctionImplementation(func, globals, functionByName, pureFunctions, isStatic, shortName)
        }

        // todo if a function is already defined in the parent class, skip it
        // todo if a function is a default-interface-function, link to it via a bridge method somehow...
        // todo we need to handle super-calls properly, too, with super.(), which we removed...

        // todo replace tree-calls with actual call
        // todo replace instanceof-function-call with proper instance-of check

        writer.size-- // delete last \n
        writer.append("}\n\n")
    }

    // todo instead of structs and func-table, need to define the class and method resolution structure
    // todo instead of createArray, create the corresponding arrays
    // todo replace array length with .length
    // todo replace strings with JavaScript strings
    // todo replace field getters and setters with field-access
    // todo write class instances as pre-defined JavaScript objects, maybe a .json?

    // defineFunctionImplementations(functions, globals, functionByName, pureFunctions)
    jsFolder.getChild("jvm2js.js").write()
    clock.stop("Writing Functions")
}

fun shortName(name: String, args: String): String {
    return when (name) {
        STATIC_INIT -> "static|$args"
        INSTANCE_INIT -> "new|$args"
        else -> "$name|$args"
    }.escapeChars()
}

fun defineFunctionImplementation(
    function: FunctionImpl, globals: Map<String, GlobalVariable>,
    functionsByName: Map<String, FunctionImpl>, pureFunctions: Set<String>,
    isStatic: Boolean, shortName: String
) {
    val stackToDeclarative = StackToDeclarative(globals, functionsByName, pureFunctions)
    val optimizer = DeclarativeOptimizer(globals)
    val functionWriter = FunctionWriter(globals, LowLevelJavaScript(writer))
    functionWriter.depth++ // inside a class -> one more
    val pos0 = writer.size
    try {
        val declarative = stackToDeclarative.write(function)
        val optimized = optimizer.write(function.withBody(declarative))
        val renamed = FunctionImpl(
            shortName, function.params, function.results,
            function.locals, optimized, function.isExported
        )
        functionWriter.write(renamed, isStatic)
    } catch (e: Throwable) {
        println(writer.toString(pos0, writer.size))
        throw RuntimeException("Failed writing ${function.funcName}", e)
    }
    writer.append('\n')
}
