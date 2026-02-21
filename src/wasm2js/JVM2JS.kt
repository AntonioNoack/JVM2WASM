package wasm2js

import dIndex
import dependency.ActuallyUsedIndex
import gIndex
import globals
import hIndex
import hierarchy.Annota
import jvm2wasm
import listInterfaces
import me.anno.utils.Clock
import me.anno.utils.types.Booleans.toInt
import org.objectweb.asm.Opcodes.ACC_STATIC
import targetsFolder
import utils.*
import utils.DefaultClassLayouts.GC_FIELD_NAME
import utils.MethodResolver.resolveMethod
import wasm.parser.FunctionImpl
import wasm.parser.GlobalVariable
import wasm.parser.Import
import wasm2cpp.*
import wasm2cpp.language.HighLevelJavaScript
import wasm2cpp.language.HighLevelJavaScript.Companion.fieldName
import wasm2cpp.language.HighLevelJavaScript.Companion.jsKeywords

val jsFolder = targetsFolder.getChild("javascript")

// todo minify JavaScript
//  - inline local variables, where possible
//  - rename fields to a-z (except when serialized)

// done minimizing JavaScript
//  - done local variables to a-z
//  - done classes to unique, short names -> which symbols are allowed at the start, which after?
//  - remove all comments

// todo bug: minify is broken :/, producing different results than usual
var minifyJavaScript = false

val CLASS_INSTANCE_NAME = if (minifyJavaScript) "\$C" else "CLASS_INSTANCE"
val LAMBDA_INSTANCE_NAME = if (minifyJavaScript) "\$L" else "LAMBDA_INSTANCE"
val DO_NOTHING_NAME = if (minifyJavaScript) "$0" else "DO_NOTHING"

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
    val functions = collectAllMethods(clock)
    wasm2js(functions, functionTable, imports, globals)
    clock.stop("WASM2JS")
}

data class Method(val jsName: String, val sig: MethodSig)
data class Field(val sig: FieldSig, val annotations: List<Int>)

data class ClassInfo(
    val interfaces: Set<String>,
    val fields: List<Field>,
    val methods: List<Method>
)

fun wasm2js(
    functions: ArrayList<FunctionImpl>, functionTable: List<String>,
    imports: List<Import>, globals: Map<String, GlobalVariable>
) {

    val functionByName = createFunctionByNameMap(functions, imports)
    functions.removeIf { it.funcName.startsWith("getNth_") || it.funcName.startsWith("tree_") }

    val annotationInstances = HashMap<Annota, Int>()
    fun getAnnotationId(annota: Annota): Int {
        return annotationInstances.getOrPut(annota) { annotationInstances.size }
    }

    writeHeader(functions, functionTable, imports, globals)

    clock.stop("Writing Header")
    val pureFunctions = PureFunctions(imports, functions, functionByName).findPureFunctions()
    clock.stop("Finding Pure Functions")
    writer.append("\"use strict\";\n\n")
    writer.append("const ").append(DO_NOTHING_NAME).append(" = () => {};\n")
    writer.append("window.minimizeJS = ").append(minifyJavaScript).append(";\n")

    fun appendValue(value: Any?, type: String) {
        when (value) {
            null -> {
                val nullValue = when (type) {
                    "long" -> "0n"
                    "float", "double" -> "0.0"
                    in NativeTypes.nativeTypes -> "0"
                    else -> "null"
                }
                writer.append(nullValue)
            }
            is Number -> writer.append(value)
            else -> throw NotImplementedError("${value.javaClass}, $type")
        }
    }

    val classInfos = ArrayList<ClassInfo>(gIndex.classNames.size)
    // todo group them by package...
    val classNames = gIndex.classNames
    for (classId in classNames.indices) {

        val className = classNames[classId]
        val jsClassName = classNameToJS(className)
        writer.append("class ").append(jsClassName)
        val superClass = hIndex.superClass[className]
        if (superClass != null) writer.append(" extends ").append(classNameToJS(superClass))
        writer.append(if (minifyJavaScript) "{" else " {\n")

        // todo declare static fields, so we can use them
        val staticFields0 = gIndex.getFieldOffsets(className, true)
        val instanceFields0 = gIndex.getFieldOffsets(className, false)
        val staticFields = staticFields0.fields
        val instanceFields = instanceFields0.fields
        val methods = hIndex.methodsByClass[className] ?: emptyList()

        if (!minifyJavaScript) {
            writer.append("  static CLASS_INSTANCE = null;\n")
        } // else doesn't need to be declared

        val isConstructable = className in dIndex.constructableClasses
        val isAbstract = hIndex.isAbstractClass(className)
        if ("INSTANCE" !in staticFields &&
            !instanceFields0.hasFields() &&
            isConstructable && !isAbstract &&
            className in hIndex.syntheticClasses
        ) {
            // used to replace getClassIdPtr
            if (minifyJavaScript) {
                writer.append("static ").append(LAMBDA_INSTANCE_NAME).append("=new ").append(jsClassName).append(";")
            } else {
                writer.append("  static ").append(LAMBDA_INSTANCE_NAME).append(" = new ").append(jsClassName)
                    .append(";\n")
            }
        }

        val fields1 = ArrayList<Field>(staticFields.size + instanceFields.size)
        val methods1 = ArrayList<Method>(methods.size)

        fun appendField(fieldSig: FieldSig) {
            val value = hIndex.finalFields[fieldSig]
            if (!minifyJavaScript) writer.append("  ")
            if (fieldSig.isStatic) writer.append("static ")
            writer.append(fieldName(fieldSig))
            writer.append(if (minifyJavaScript) "=" else " = ")
            appendValue(value, fieldSig.jvmType)
            writer.append(if (minifyJavaScript) ";" else ";\n")
            val annotations = hIndex.fieldAnnotations[fieldSig] ?: emptyList()
            fields1.add(
                Field(
                    fieldSig, annotations
                        .filter { it.properties.isEmpty() } // todo support annotations with properties, too
                        .map { getAnnotationId(it) })
            )
        }

        for ((name, data) in staticFields) {
            appendField(FieldSig(className, name, data.jvmType, true))
        }
        if (!minifyJavaScript) writer.append("\n") // there is always static fields

        if (isConstructable && instanceFields.isNotEmpty()) {
            for ((name, data) in instanceFields) {
                if (name == GC_FIELD_NAME) continue // implicit field
                if (name == "length" && className.startsWith("[")) continue // implicit field
                appendField(FieldSig(className, name, data.jvmType, false))
            }
            if (!minifyJavaScript) writer.append("\n")
        }

        // println(className)

        // write functions/function links here
        for (method in methods.sortedBy { it.name }) {
            val isStatic = hIndex.isStatic(method)
            if (!isStatic && !isConstructable) continue
            val resolved =
                if (isStatic) hIndex.getAlias(method)
                else resolveMethod(method, false)
            if (!isStatic && superClass != null && resolved != null &&
                resolved.className != className &&
                resolveMethod(method.withClass(superClass), false) == resolved
            ) {
                // redundant override
                continue
            }
            // println("  $method -> $resolved")
            resolved ?: continue
            val pureJS = hIndex.getAnnotation(resolved, Annotations.PURE_JAVASCRIPT)
            val jsImplementation = pureJS ?: hIndex.getAnnotation(resolved, Annotations.JAVASCRIPT)
            val func = functionByName[methodName(resolved)]
            if (jsImplementation != null) {
                val shortName = shortName(method)
                val numParams = method.descriptor.params.size + (!isStatic).toInt()
                defineFunctionImplementationPureJS(
                    numParams, shortName, isStatic,
                    jsImplementation.properties["code"] as String
                )
                methods1.add(Method(shortName, method))
                continue
            }
            // println("  -> ${methodName(resolved)} -> ${func?.javaClass}")
            if (func == null || func is Import) continue
            val shortName = shortName(method)
            // println("  -> '$shortName'")
            defineFunctionImplementation(
                func, globals, functionByName, pureFunctions,
                isStatic, className, shortName
            )
            if (method.name != STATIC_INIT) {
                methods1.add(Method(shortName, method))
            }
        }

        val interfaces = listInterfaces(className, HashSet())
        // todo link all interface methods/default implementations...

        // todo if a function is already defined in the parent class, skip it
        // todo if a function is a default-interface-function, link to it via a bridge method somehow...
        // todo we need to handle super-calls properly, too, with super.(), which we removed...

        // todo replace tree-calls with actual call
        // todo replace instanceof-function-call with proper instance-of check

        classInfos.add(ClassInfo(interfaces, fields1, methods1))

        writer.append(if (minifyJavaScript) "}" else "}\n")
    }

    if (minifyJavaScript) {
        writer.append("const $ = window.wrapString;\n")
    }

    // what do we need? name, shortName, fields, methods
    if (!minifyJavaScript) writer.append("// class instances\n")
    val globalNumClasses = if (minifyJavaScript) {
        shortName(Triple("local", "", "global_numClasses"))
    } else "global_numClasses"
    writer.append("const ").append(globalNumClasses).append(" = ").append(gIndex.classNames.size).append(";\n")
    writer.append("const CLASS_INSTANCES = new Array(").append(globalNumClasses).append(");\n") // only named classes
    writer.append("for(let i=0;i<").append(globalNumClasses).append(";i++){\n")
    writer.append("   CLASS_INSTANCES[i] = new java_lang_Class();\n")
    writer.append(if (minifyJavaScript) "}" else "}\n\n")

    for (classId in classNames.indices) {
        val className = classNames[classId]
        val jsClassName = classNameToJS(className)
        val jvmClassName = className.replace('/', '.')
        writer.append("link(\"").append(jvmClassName).append("\",").append(jsClassName).append(")")
            .append(if (minifyJavaScript) ";" else ";\n")
    }

    if (!minifyJavaScript) {
        writer.append("\n// annotation instances\n")
    }
    writer.append("const ANNOTATION_INSTANCES = new Array(").append(annotationInstances.size).append(");\n")
    for ((annota) in annotationInstances.entries.sortedBy { it.value }) {
        writer.append("annota(").append(gIndex.getClassId(annota.implClass))
            .append(if (minifyJavaScript) ");" else ");\n")
    }
    if (!minifyJavaScript) writer.append("\n")

    for (classId in classNames.indices) {
        val className = classNames[classId]
        val classInfo = classInfos[classId]
        val superClassName = hIndex.superClass[className] ?: "java/lang/Object"
        writer.append("init(")
        writer.append(gIndex.getClassId(superClassName)).append(",[")
        val interfaceIds = classInfo.interfaces
            .mapNotNull { gIndex.getClassIdOrNull(it) }
        for (interfaceId in interfaceIds.sorted()) {
            if (!writer.endsWith("[")) writer.append(',')
            writer.append(interfaceId)
        }
        writer.append("],\"")
        for ((field, annotationIds) in classInfo.fields) {
            if (!writer.endsWith("\"")) writer.append(';')
            val typeId = gIndex.classIndex[field.jvmType] ?: 0
            val modifiers = field.isStatic.toInt(ACC_STATIC)
            writer.append(field.name).append(',')
                .append(typeId).append(',')
                .append(modifiers)
            for (id in annotationIds) {
                writer.append(',').append(id)
            }
        }
        writer.append("\",\"")
        for ((shortName, method) in classInfo.methods) {
            if (!writer.endsWith("\"")) writer.append(';')
            // short-name is for calling it
            // we need the actual name for finding it...
            writer.append(if (method.name == INSTANCE_INIT) "" else method.name).append(',')
            writer.append(shortName).append(',')
            val modifiers = hIndex.isStatic(method).toInt(ACC_STATIC)
            writer.append(modifiers).append(',')
            val returnType = method.descriptor.returnType ?: "void"
            writer.append(gIndex.getClassIdOrParents(returnType))
            for (param in method.descriptor.params) {
                writer.append(',').append(gIndex.getClassIdOrParents(param))
            }
        }
        writer.append("\")").append(if (minifyJavaScript) ";" else ";\n")
    }

    // append all helper methods
    if (!minifyJavaScript) {
        writer.append("\n\n")
        writer.append("// helper methods\n")
    }
    for (impl in helperMethods.values) {
        if (impl.funcName !in ActuallyUsedIndex.usedBy) continue
        defineFunctionImplementation(
            impl, globals, functionByName,
            pureFunctions, true, "", impl.funcName
        )
    }

    // todo replace strings with JavaScript strings(?)

    // define all well-named alias functions as links to the actual methods
    if (!minifyJavaScript) writer.append("\n// aliases\n")
    else writer.append("let _=window;\n")
    for ((name, method) in hIndex.methodAliases) {
        if ('_' !in name && hIndex.isStatic(method)) {
            writer.append(if (minifyJavaScript) "_." else "window.").append(name).append(" = ")
                .append(classNameToJS(method.className)).append('.').append(shortName(method))
                .append(if (minifyJavaScript) ";" else ";\n")
        }
    }
    writer.append("\n")

    // defineFunctionImplementations(functions, globals, functionByName, pureFunctions)
    jsFolder.getChild("jvm2js.js").write()
    clock.stop("Writing Functions")
}

fun shortName(sig: MethodSig): String {
    return shortName(sig.className, sig.name, sig.descriptor.raw)
}

fun shortName(key: Triple<String, String, String>): String {
    return shortNames.getOrPut(key) {
        indexToName(shortNames.size)
    }
}

fun shortName(key: Triple<String, String, String>, debugExtra: String): String {
    return shortNames.getOrPut(key) {
        debugExtra + "_" + indexToName(shortNames.size)
    }
}

private val availableChars0 = ('A'..'Z') + ('a'..'z') + '_'
private val availableChars1 = availableChars0 + ('0'..'9') + '$'

private fun indexToName(idx: Int): String {
    var i = idx + 1
    val name = StringBuilder()
    while (i > 0) {
        val chars = if (name.isEmpty()) availableChars0 else availableChars1
        val char = chars[i % chars.size]
        name.append(char)
        i /= chars.size
    }
    var result = name.toString()
    if (result in jsKeywords) result = "_$result"
    return result
}

// todo instead of not renaming these classes, create aliases at the end of the file for them
val usedFromOutsideClasses = listOf(
    "java/lang/Class",
    "java/lang/reflect/Method",
    "java/lang/reflect/Constructor",
    "java/lang/reflect/Field",
    "java/lang/String",
    "[]"
) + NativeTypes.nativeArrays + NativeTypes.nativeTypeWrappers.values

fun classNameToJS(jvmType: String): String {
    return if (minifyJavaScript && jvmType !in usedFromOutsideClasses) {
        shortName(Triple("class", "", jvmType))
    } else classNameToJS0(jvmType)
}

private fun classNameToJS0(jvmType: String): String {
    if (jvmType == "void") return "_void"
    return jvmType.escapeChars()
}

private val shortNames = HashMap<Triple<String, String, String>, String>(4096)
fun shortName(className: String, name: String, args: String): String {
    return if (minifyJavaScript) {

        val isUsedFromOutside =
            className in NativeTypes.nativeTypeWrappers.values && name == "valueOf"

        if (isUsedFromOutside) {
            return shortName0(className, name, args)
        }

        val key = when (name) {
            STATIC_INIT -> Triple("static", "", "")
            INSTANCE_INIT -> Triple("new", className, args) // must include class-name to avoid inheritance
            else -> Triple("call", name, args)
        }

        shortName(key)
    } else shortName0(className, name, args)
}

fun shortName0(className: String, name: String, args: String): String {
    return when (name) {
        STATIC_INIT -> "static|()V"
        INSTANCE_INIT -> "new|$className|$args" // must include class-name to avoid inheritance
        else -> "$name|$args"
    }.escapeChars()
}

fun defineFunctionImplementationPureJS(
    numParamsInclSelf: Int, shortName: String,
    isStatic: Boolean, pureJavaScript: String
) {
    if (isStatic) writer.append("static ")
    writer.append(shortName).append("(")
    for (i in 0 until numParamsInclSelf) {
        if (i == 0 && !isStatic) continue
        if (!writer.endsWith("(")) writer.append(", ")
        writer.append("arg").append(i)
    }
    writer.append(") {")
    if (!isStatic) {
        writer.append("const arg0 = this;")
        if (!minifyJavaScript) writer.append("\n")
    }
    var code = pureJavaScript
    if (minifyJavaScript && ".CLASS_INSTANCE" in code) {
        code = code.replace(".CLASS_INSTANCE", ".$CLASS_INSTANCE_NAME")
    }
    writer.append(code) // to do indent this code??
    writer.append(if (minifyJavaScript) "}" else "\n}\n")
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
    if (className.isNotEmpty()) functionWriter.depth++ // inside a class -> one more
    val pos0 = writer.size
    try {
        val declarative = stackToDeclarative.write(function)
        val optimized = optimizer.write(function.withBody(declarative))
        val renamed = FunctionImpl(
            shortName, function.params, function.results,
            function.locals, optimized, function.isExported
        )
        functionWriter.write(renamed, className, function.funcName, isStatic)
    } catch (e: Throwable) {
        println(writer.toString(pos0, writer.size))
        throw RuntimeException("Failed writing ${function.funcName}", e)
    }
    if (!minifyJavaScript) writer.append('\n')
}
