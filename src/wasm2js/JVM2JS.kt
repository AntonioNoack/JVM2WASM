package wasm2js

import dIndex
import dependency.ActuallyUsedIndex
import gIndex
import globals
import hIndex
import hierarchy.Annota
import jvm2wasm
import me.anno.utils.Clock
import me.anno.utils.OS.documents
import me.anno.utils.types.Booleans.toInt
import org.objectweb.asm.Opcodes.ACC_STATIC
import utils.*
import utils.DefaultClassLayouts.GC_FIELD_NAME
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
    if (jvmType == "void") return "_void"
    return jvmType.escapeChars()
}

data class Method(val jsName: String, val sig: MethodSig)
data class Field(val sig: FieldSig, val annotations: List<Int>)

data class ClassInfo(
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
    writer.append("const DO_NOTHING = () => {};\n")

    fun appendValue(value: Any?, type: String) {
        when (value) {
            null -> {
                val nullValue = when (type) {
                    "long" -> "0n"
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
    fun writeClassInstanceCreateCall(className: String) {
        val classId = gIndex.getClassId(className)
        val classInfo = classInfos[classId]
        val superClassName = hIndex.superClass[className] ?: "java/lang/Object"
        writer.append("init(")
        writer.append(gIndex.getClassId(superClassName)).append(",\"")
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
            val returnType = method.descriptor.returnType
            val returnTypeId = if (returnType != null) gIndex.getClassIdOrParents(returnType) else -1
            writer.append(returnTypeId)
            for (param in method.descriptor.params) {
                writer.append(',').append(gIndex.getClassIdOrParents(param))
            }
        }
        writer.append("\");\n")
    }

    // todo group them by package...
    val classNames = gIndex.classNames
    for (classId in classNames.indices) {

        val className = classNames[classId]
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

        writer.append("  static CLASS_INSTANCE = null;\n")

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

        val fields1 = ArrayList<Field>(staticFields.size + instanceFields.size)
        val methods1 = ArrayList<Method>(methods.size)

        fun appendField(fieldSig: FieldSig) {
            val name = fieldSig.name
            val value = hIndex.finalFields[fieldSig]
            writer.append("  ")
            if (fieldSig.isStatic) writer.append("static ")
            writer.append(name).append(" = ")
            appendValue(value, fieldSig.jvmType)
            writer.append(";\n")
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
        writer.append("\n") // there is always static fields

        if (isConstructable && instanceFields.isNotEmpty()) {
            for ((name, data) in instanceFields) {
                if (name == GC_FIELD_NAME) continue // implicit field
                if (name == "length" && className.startsWith("[")) continue // implicit field
                appendField(FieldSig(className, name, data.jvmType, false))
            }
            writer.append("\n")
        }

        // println(className)

        // write functions/function links here
        for (method in methods.sortedBy { it.name }) {
            val isStatic = hIndex.isStatic(method)
            if (!isStatic && !isConstructable) continue
            val resolved =
                if (isStatic) hIndex.getAlias(method)
                else resolveMethod(method, false)
            // println("  $method -> $resolved")
            resolved ?: continue
            val pureJS = hIndex.getAnnotation(resolved, Annotations.PURE_JAVASCRIPT)
            val jsImplementation = pureJS ?: hIndex.getAnnotation(resolved, Annotations.JAVASCRIPT)
            val func = functionByName[methodName(resolved)]
            if (jsImplementation != null) {
                val shortName = shortName(method.name, method.descriptor.raw)
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
            val shortName = shortName(method.name, method.descriptor.raw)
            // println("  -> '$shortName'")
            defineFunctionImplementation(
                func, globals, functionByName, pureFunctions,
                isStatic, className, shortName
            )
            if (method.name != STATIC_INIT) {
                methods1.add(Method(shortName, method))
            }
        }

        // todo if a function is already defined in the parent class, skip it
        // todo if a function is a default-interface-function, link to it via a bridge method somehow...
        // todo we need to handle super-calls properly, too, with super.(), which we removed...

        // todo replace tree-calls with actual call
        // todo replace instanceof-function-call with proper instance-of check

        classInfos.add(ClassInfo(fields1, methods1))

        writer.append("}\n")
    }

    // what do we need? name, shortName, fields, methods
    writer.append("// class instances\n")
    writer.append("const global_numClasses = ").append(gIndex.classNames.size).append(";\n")
    writer.append("const CLASS_INSTANCES = new Array(global_numClasses);\n") // only named classes
    writer.append(
        "" +
                "for(let i=0;i<global_numClasses;i++){\n" +
                "   CLASS_INSTANCES[i] = new java_lang_Class();\n" +
                "}\n"
    )
    writer.append("\n")

    for (classId in classNames.indices) {
        val className = classNames[classId]
        val jsClassName = classNameToJS(className)
        val jvmClassName = className.replace('/', '.')
        writer.append("link(\"").append(jvmClassName).append("\",").append(jsClassName).append(");\n")
    }
    writer.append("\n")

    writer.append("// annotation instances\n")
    writer.append("const ANNOTATION_INSTANCES = new Array(").append(annotationInstances.size).append(");\n")
    for ((annota) in annotationInstances.entries.sortedBy { it.value }) {
        writer.append("annota(").append(gIndex.getClassId(annota.implClass)).append(");\n")
    }
    writer.append("\n")

    for (classIdI in classNames.indices) {
        writeClassInstanceCreateCall(classNames[classIdI])
    }
    writer.append("\n")

    // append all helper methods
    writer.append("\n")
    writer.append("// helper methods\n")
    for (impl in helperMethods.values) {
        if (impl.funcName !in ActuallyUsedIndex.usedBy) continue
        defineFunctionImplementation(
            impl, globals, functionByName,
            pureFunctions, true, "", impl.funcName
        )
    }

    // todo replace strings with JavaScript strings(?)

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
    if (!isStatic) writer.append("const arg0 = this;\n")
    writer.append(pureJavaScript) // indent this code??
    writer.append("\n}\n")
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
        functionWriter.write(renamed, className, isStatic)
    } catch (e: Throwable) {
        println(writer.toString(pos0, writer.size))
        throw RuntimeException("Failed writing ${function.funcName}", e)
    }
    writer.append('\n')
}
