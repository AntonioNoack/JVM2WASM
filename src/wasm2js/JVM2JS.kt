package wasm2js

import dIndex
import gIndex
import globals
import hIndex
import jvm2wasm
import me.anno.utils.Clock
import me.anno.utils.OS.documents
import utils.*
import utils.MethodResolver.resolveMethod
import wasm.parser.FunctionImpl
import wasm.parser.GlobalVariable
import wasm.parser.Import
import wasm2cpp.*
import wasm2cpp.language.HighLevelJavaScript

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
    return jvmType.escapeChars()
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
    writer.append("const DO_NOTHING = () => {};\n")

    fun appendValue(value: Any?, type: String) {
        when (value) {
            null -> {
                if (type in NativeTypes.nativeTypes) {
                    writer.append("0")
                } else {
                    writer.append("null")
                }
            }
            is Number -> writer.append(value)
            else -> throw NotImplementedError("${value.javaClass}, $type")
        }
    }

    // todo group them by package...
    val classNames = gIndex.classNamesByIndex
    for (classId in classNames.indices) {

        val className = classNames[classId]
        if (className in NativeTypes.nativeTypes) continue

        val jsClassName = classNameToJS(className)
        writer.append("class ").append(jsClassName)
        val superClass = hIndex.superClass[className]
        if (superClass != null) writer.append(" extends ").append(classNameToJS(superClass))
        writer.append(" {\n")

        // todo declare static fields, so we can use them
        val staticFields0 = gIndex.getFieldOffsets(className, true)
        val instanceFields0 = gIndex.getFieldOffsets(className, false)
        val staticFields = staticFields0.fields
        val instanceFields = instanceFields0.fields
        val methods = hIndex.methodsByClass[className] ?: emptyList()

        val isConstructable = className in dIndex.constructableClasses
        val isAbstract = hIndex.isAbstractClass(className)
        if ("INSTANCE" !in staticFields &&
            !instanceFields0.hasFields() &&
            isConstructable && !isAbstract &&
            className in hIndex.syntheticClasses
        ) {
            // used to replace getClassIdPtr
            writer.append("  static LAMBDA_INSTANCE = new ").append(jsClassName).append(";\n")
        }

        if (methods.any { it.name == STATIC_INIT }) {
            writer.append("  static STATIC_INITED = false;\n")
        }

        if (staticFields.isNotEmpty()) {
            for ((name, data) in staticFields) {
                val value = hIndex.finalFields[FieldSig(className, name, data.jvmType, true)]
                writer.append("  static ").append(name).append(" = ")
                appendValue(value, data.jvmType)
                writer.append(";\n")
            }
            writer.append("\n")
        }

        if (isConstructable && instanceFields.isNotEmpty()) {
            for ((name, data) in instanceFields) {
                val value = hIndex.finalFields[FieldSig(className, name, data.jvmType, false)]
                writer.append("  ").append(name).append(" = ")
                appendValue(value, data.jvmType)
                writer.append(";\n")
            }
            writer.append("\n")
        }

        // println(className)

        // write functions/function links here
        for (method in methods.sortedBy { it.name }) {
            val isStatic = hIndex.isStatic(method)
            if (!isStatic && !isConstructable) continue
            val resolved =
                if (isStatic) method
                else resolveMethod(method, false)
            // println("  $method -> $resolved")
            resolved ?: continue
            val func = functionByName[methodName(resolved)]
            // println("  -> ${methodName(resolved)} -> ${func?.javaClass}")
            func ?: continue
            if (func is Import) continue // todo link to import/write imported implementation
            val shortName = shortName(method.name, method.descriptor.raw)
            // println("  -> '$shortName'")
            defineFunctionImplementation(
                func, globals, functionByName, pureFunctions,
                isStatic, className, shortName
            )
        }

        // todo if a function is already defined in the parent class, skip it
        // todo if a function is a default-interface-function, link to it via a bridge method somehow...
        // todo we need to handle super-calls properly, too, with super.(), which we removed...

        // todo replace tree-calls with actual call
        // todo replace instanceof-function-call with proper instance-of check

        writer.size-- // delete last \n
        writer.append("}\n\n")
    }

    // todo instead of createArray, create the corresponding arrays
    // todo replace array length with .length
    // todo replace strings with JavaScript strings
    // todo write class instances as pre-defined JavaScript objects, maybe a .json?

    // define all well-named alias functions as links to the actual methods
    writer.append("\n")
    writer.append("// aliases\n")
    for ((name, method) in hIndex.methodAliases) {
        if ('_' !in name && hIndex.isStatic(method)) {
            writer.append("window.").append(name).append(" = ")
                .append(classNameToJS(method.className)).append('.').append(shortName(method))
                .append(";\n")
        }
    }
    writer.append("\n")

    // defineFunctionImplementations(functions, globals, functionByName, pureFunctions)
    jsFolder.getChild("jvm2js.js").write()
    clock.stop("Writing Functions")
}

fun shortName(sig: MethodSig): String {
    return shortName(sig.name, sig.descriptor.raw)
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
    isStatic: Boolean, className: String, shortName: String,
) {
    val stackToDeclarative = StackToDeclarative(
        globals, functionsByName, pureFunctions,
        useHighLevelMemoryAccess = true,
        useHighLevelMethodResolution = true
    )
    val optimizer = DeclarativeOptimizer(globals)
    val functionWriter = FunctionWriter(globals, HighLevelJavaScript(writer))
    functionWriter.depth++ // inside a class -> one more
    val pos0 = writer.size
    try {
        val declarative = stackToDeclarative.write(function)
        val optimized = optimizer.write(function.withBody(declarative))
        val renamed = FunctionImpl(
            shortName, function.params, function.results,
            function.locals, optimized, function.isExported
        )
        functionWriter.write(renamed, className, isStatic)
    } catch (e: Throwable) {
        println(writer.toString(pos0, writer.size))
        throw RuntimeException("Failed writing ${function.funcName}", e)
    }
    writer.append('\n')
}
