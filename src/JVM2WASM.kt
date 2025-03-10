import dependency.ActuallyUsedIndex
import dependency.DependencyIndex
import dependency.StaticDependencies
import hierarchy.DelayedLambdaUpdate
import hierarchy.HierarchyIndex
import interpreter.WASMEngine
import jvm.JVM32
import me.anno.io.Streams.readText
import me.anno.maths.Maths.align
import me.anno.maths.Maths.ceilDiv
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertFalse
import me.anno.utils.assertions.assertTrue
import me.anno.utils.files.Files.formatFileSize
import org.objectweb.asm.Opcodes.ASM9
import translator.GeneratorIndex
import translator.GeneratorIndex.dataStart
import translator.GeneratorIndex.nthGetterMethods
import translator.GeneratorIndex.stringStart
import translator.GeneratorIndex.translatedMethods
import utils.*
import utils.DynIndex.appendDynamicFunctionTable
import utils.DynIndex.appendInheritanceTable
import utils.DynIndex.appendInvokeDynamicTable
import utils.DynIndex.resolveIndirectTablePtr
import utils.NativeHelperFunctions.appendNativeHelperFunctions
import utils.WASMTypes.*
import wasm.instr.Instructions.F64_SQRT
import wasm.parser.GlobalVariable
import wasm2cpp.FunctionOrder
import java.io.FileNotFoundException
import kotlin.math.sin

const val api = ASM9

// todo Kotlin reflection is currently broken, because
//  kotlin.reflect.jvm.internal.ReflectionFactoryImpl is missing,
//  because its dependency is never declared explicitly, only via Class.forName().
//  -> automatically replace all Kotlin-reflect stuff with KotlynReflect, or for now,
//     add this dependency manually

// todo replace simple bridge methods like the following with just an alias
/**
 * i32 me_anno_engine_ui_render_RenderModeXCompanion_getNO_DEPTH_Lme_anno_engine_ui_render_RenderMode(i32 p0) {
 *   // L117163766
 *   // line 176
 *   stackPush(34886);
 *   i32 tmp0 = me_anno_engine_ui_render_RenderMode_accessXgetNO_DEPTHXcp_Lme_anno_engine_ui_render_RenderMode();
 *   // static call
 *   stackPop();
 *   return tmp0;
 * }
 * */

// todo calculate dependency-tree from static initialization,
//  and call that once at the start, and then never again
// alternative/improvement:
// todo on each branch, mark which classes have been static-initialized,
//  and remove those on all necessarily following branches


// optimizations:
// done when there is no child implementations for a non-final, non-static function, call it directly
// todo also track, which methods theoretically can throw errors: mark them, and all their callers; -> optimization
// call_indirect/interface is more complicated... all methods need to have the same signature
// done if there is only a small number of implementations, make resolveDirect() use a if-else-chain instead (-> modify findUniquelyImplemented())


// todo Companion-objects are unique, so make all their fields and themselves static;
//  we don't need them to be instances

// todo test LuaScripts -> magically not working :/

// todo combine non-exported functions with the same content :3

// do we already replace dependencies before resolving them? probably could save us tons of space (and wabt compile time 😁)
// todo mark empty functions as such, and skip them; e.g. new_java_lang_Object_V, new_java_lang_Number_V

// todo loading bar for library loading


// to do find pure (nothrow) functions, and spread them across other methods
// todo implement WASM throwables instead
// todo finally fix huge switch-tables by splitting programs into sections & such
// -> partially done already :)


// we even could compile scene files to WASM for extremely fast load times 😄

var exportAll = false // 15% space-saving in WASM; 10% in compressed
var exportHelpers = exportAll

var useKotlynReflect = true
var useDefaultKotlinReflection = false

var callStaticInitOnce = false
var callStaticInitAtCompileTime = true // todo implement this

// todo this needs catch-blocks, somehow..., and we get a lot of type-mismatch errors at the moment
var useWASMExceptions = false
var crashOnAllExceptions = true
val anyMethodThrows = !useWASMExceptions && !crashOnAllExceptions

// experimental, not really JVM conform; might work anyway 😄, and be faster or use less memory
var enableTracing = true
var ignoreNonCriticalNullPointers = true
var checkArrayAccess = false
var checkNullPointers = false
var checkClassCasts = false
var checkIntDivisions = false

var useUTF8Strings = false // doesn't work with the compiler yet
var replaceStringInternals = true // another way for UTF-8 strings

var crashInStatic = false // crashes at runtime :/
val byteStrings = useUTF8Strings || replaceStringInternals

var disableAudio = true

var addDebugMethods = false

// todo doesn't work yet, missing functions :/
// if this flag is true, fields that aren't read won't be written
var fieldsRWRequired = false

var alwaysUseFieldCalls = true


val stackSize = if (enableTracing) 1024 * 32 else 0

val classReplacements = hashMapOf(
    "java/util/concurrent/ConcurrentHashMap" to "java/util/HashMap",
    "java/util/concurrent/ConcurrentLinkedQueue" to "java/util/LinkedList",
    "java/util/concurrent/atomic/AtomicInteger" to "jvm/custom/AtomicInt",
    "java/util/concurrent/atomic/AtomicLong" to "jvm/custom/AtomicLong",
    "java/util/concurrent/PriorityBlockingQueue" to "java/util/PriorityQueue",
    "java/util/concurrent/LinkedBlockingQueue" to "java/util/LinkedList",
    "java/util/concurrent/ConcurrentSkipListSet" to "java/util/HashSet",
    "java/util/concurrent/ConcurrentHashMap" to "java/util/HashMap",
    "java/util/concurrent/ConcurrentMap" to "java/util/Map",
    "java/lang/ref/WeakReference" to "jvm/custom/WeakRef",
    "java/lang/String" to if (useUTF8Strings) "jvm/custom/UTF8String" else "java/lang/String",
    "java/io/BufferedWriter" to if (byteStrings) "jvm/utf8/BufferedWriterUTF8" else "java/io/BufferedWriter",
    "java/io/OutputStreamWriter" to "jvm/utf8/OutputStreamWriterUTF8",
    // "java/lang/StringBuilder" to "jvm/utf8/StringBuilderUTF8",
    // "java/util/stream/IntStream" to "jvm/utf8/IntStreamV2", // only used for codepoints()
    "kotlin/SynchronizedLazyImpl" to "jvm/custom/SimpleLazy",
    "java/io/File" to "jvm/custom/File",
    "java/nio/file/Path" to "jvm/custom/File",
    "java/nio/file/Files" to "jvm/custom/File",
    "java/net/URL" to "jvm/custom/File",
    "java/net/URI" to "jvm/custom/File",
    "java/net/URLClassLoader" to "jvm/custom/URLClassLoader2",
    "java/util/Vector" to "java/util/ArrayList", // only difference is synchronization
    "java/util/Stack" to "java/util/ArrayList", // just a few extra functions
    "java/awt/Font" to "jvm/custom/awt/Font",
    "java/awt/Toolkit" to "jvm/custom/awt/Toolkit",
    "java/awt/Dimension" to "jvm/custom/awt/Dimension",
    "java/lang/ThreadLocal" to "jvm/custom/ThreadLocal2",
    "java/lang/RuntimePermission" to "jvm/custom/RTPermission",
).apply {
    if (!checkArrayAccess) {
        put("jvm/ArrayAccessSafe", "jvm/ArrayAccessUnchecked")
    }
}

fun replaceClass(clazz: String): String = replaceClassNullable(clazz)!!
fun replaceClassNullable(clazz: String?): String? {
    var clazz1 = clazz ?: return null
    assertTrue(!clazz1.endsWith(";"), clazz1)
    if (useKotlynReflect) {
        clazz1 = KotlynReflect.replaceClass(clazz1)
    }
    return classReplacements[clazz1] ?: clazz1
}

// forbid from being implemented -> "just" override its methods
fun cannotUseClass(clazz: String): Boolean {
    return (
            clazz.startsWith("sun/")
                    //     && !clazz.startsWith("sun/nio/cs/") && clazz != "sun/util/PreHashedMap"
                    && clazz != "sun/misc/VM"
                    && clazz != "sun/misc/Unsafe"
                    && clazz != "sun/reflect/Reflection"
                    && clazz != "sun/misc/SharedSecrets"
                    && clazz != "sun/misc/JavaNioAccess"
                    && clazz != "sun/misc/JavaLangAccess"
            ) ||
            clazz.startsWith("jdk/nashorn/") || // we don't want a JS engine in WASM 😂😅
            clazz.startsWith("org/apache/commons/") ||
            clazz.startsWith("java/time/") || // we don't need it, and it feels like a lot of code (actually, it looks like 2 MB assembly text)
            (clazz.startsWith("jdk/internal/") &&
                    clazz != "jdk/internal/misc/TerminatingThreadLocal") ||
            clazz.startsWith("com/github/junrar/") || // not needed
            clazz.startsWith("java/util/concurrent/") || // not useful without threading
            clazz.startsWith("java/awt/image/") || // not available anyway
            clazz.startsWith("java/awt/Component") || // not available anyway
            clazz.startsWith("javax/imageio/") || // not available anyway
            clazz.startsWith("java/util/regex/") || // let's see how much space it takes -> 2.2 MB wasm text out of 70
            // todo this is used by BigDecimal... surely it's not really needed... right?
            // clazz.startsWith("java/io/ObjectStream") ||
            sin(0f) < 0f // false
}

fun listEntryPoints(clazz: (String) -> Unit) {
    listEntryPoints(clazz) { clazz(it.clazz) }
}

val cannotThrow = HashSet<String>(256)

fun canThrowError(methodSig: MethodSig): Boolean {
    if (crashOnAllExceptions) return false
    if (crashInStatic && methodSig.name == STATIC_INIT) return false
    return methodName(methodSig) !in cannotThrow
}

val cl: ClassLoader = JVM32::class.java.classLoader
val resources = cl.getResourceAsStream("resources.txt")!!.readText()
    .split("\n").map { it.trim() }.filter { !it.startsWith("//") && it.isNotBlank() }
    .map { Pair(it, (cl.getResourceAsStream(it) ?: throw FileNotFoundException("Missing $it")).readBytes()) }

fun listEntryPoints(clazz: (String) -> Unit, method: (MethodSig) -> Unit) {

    clazz("engine/Engine")

    clazz("jvm/JVM32")
    clazz("jvm/JVMShared")
    clazz("jvm/GarbageCollector")
    clazz("jvm/MemDebug")

    if (checkArrayAccess) {
        clazz("jvm/ArrayAccessSafe")
    } else {
        clazz("jvm/ArrayAccessUnchecked")
    }

    // todo support KotlinReflect
    if (useDefaultKotlinReflection) {
        clazz("kotlin/reflect/jvm/internal/ReflectionFactoryImpl")
    }

    // for debugging
    if (addDebugMethods) {
        method(MethodSig.c("java/lang/Class", "getName", "()Ljava/lang/String;", false))
        method(MethodSig.c("java/lang/Object", "toString", "()Ljava/lang/String;", false))
        method(MethodSig.c("java/lang/Thread", INSTANCE_INIT, "()V", false))
    }
}

fun listLibrary(clazz: (String) -> Unit) {

    clazz("jvm/JVM32")
    clazz("jvm/JVMShared")
    clazz("jvm/MemDebug")

    clazz("jvm/JavaAWT")
    clazz("jvm/JavaConcurrent")
    clazz("jvm/JavaLang")
    clazz("jvm/JavaReflect")
    clazz("jvm/JavaReflectMethod")
    clazz("jvm/JavaThrowable")
    clazz("jvm/Chars")

    if (replaceStringInternals) {
        clazz("jvm/utf8/StringsUTF8")
        clazz("jvm/utf8/StringBuilderUTF8")
        clazz("jvm/utf8/BufferedWriterUTF8")
    } else {
        clazz("jvm/utf8v2/StringsUTF16")
    }

    if (disableAudio) {
        clazz("engine/NoAudio")
    }

    clazz("jvm/JavaIO")
    clazz("java/io/JavaIO")
    clazz("jvm/JavaNIO")
    clazz("jvm/JavaX")
    clazz("jvm/JavaUtil")
    clazz("jvm/LWJGLxAssimp")
    clazz("jvm/LWJGLxGLFW")
    clazz("jvm/LWJGLxGamepad")
    clazz("jvm/LWJGLxOpenGL")
    clazz("jvm/LWJGLxOpenAL")
    clazz("jvm/JNA")
    clazz("jvm/SunMisc")
    clazz("jvm/Kotlin")
    clazz("engine/TextGen")

    if (useDefaultKotlinReflection) {
        clazz("kotlin/reflect/jvm/internal/ReflectionFactoryImpl")
    } else if (!useKotlynReflect) {
        clazz("jvm/KotlinReflect")
    }

}

fun printMethodFieldStats() {
    val usedFields = dIndex.usedGetters.filter { it in dIndex.usedSetters }
    if (dIndex.usedMethods.size + usedFields.size < 1000) {

        println("classes:")
        for (clazz in dIndex.constructableClasses) {
            println(clazz)
        }
        println()

        println("methods:")
        for (m in dIndex.usedMethods.map { methodName(it) }.sorted()) {
            println(m)
        }
        println()

        println("fields:")
        for (f in usedFields.map { it.toString() }.toSortedSet()) {
            println(f)
        }
        println()
    }

    println(
        "${dIndex.usedMethods.size} methods + ${usedFields.size} fields, " +
                "${hIndex.superClass.size} classes indexed, ${dIndex.constructableClasses.size} constructable"
    )
}

val hIndex = HierarchyIndex
val dIndex = DependencyIndex
val gIndex = GeneratorIndex

val implementedMethods = HashMap<String, MethodSig>()

val t0 = System.nanoTime()

fun listSuperClasses(clazz: String, list: HashSet<String>): Collection<String> {
    val superClass = hIndex.superClass[clazz]
    if (superClass != null) {
        list.add(superClass)
        listSuperClasses(superClass, list)
    }
    val interfaces = hIndex.interfaces[clazz]
    if (interfaces != null) {
        for (interfaceI in interfaces) {
            list.add(interfaceI)
        }
    }
    return list
}

fun isRootType(clazz: String): Boolean {
    return when (clazz) {
        "java/lang/Object",
        "int", "float", "boolean", "byte",
        "short", "char", "long", "double" -> true
        else -> false
    }
}

val entrySig = MethodSig.c("", "entry", "()V", false)
val resolvedMethods = HashMap<MethodSig, MethodSig>(4096)
fun main() {
    jvm2wasm()
}

fun jvm2wasm() {

    // ok in Java, trapping in WASM
    // todo has this been fixed?
    // println(Int.MIN_VALUE/(-1)) // -> Int.MIN_VALUE

    val headerPrinter = StringBuilder2(256)
    val importPrinter = StringBuilder2(4096)
    val bodyPrinter = StringBuilder2(4096)
    val dataPrinter = StringBuilder2(4096)

    val predefinedClasses = listOf(
        "java/lang/Object", "[]", // 4
        "[I", "[F", // 4
        "[Z", "[B", // 1
        "[C", "[S", // 2
        "[J", "[D", // 8
        "java/lang/String", // #10
        "java/lang/Class", // #11
        "java/lang/System", // #12
        "java/io/Serializable", // #13
        "java/lang/Throwable", // #14
        "java/lang/StackTraceElement", // #15
        "java/lang/reflect/Field", // #16
        "int", "long", "float", "double",
        "boolean", "byte", "short", "char", "void" // #25
    )

    for (i in 1 until 13) {
        hIndex.registerSuperClass(predefinedClasses[i], predefinedClasses[StaticClassIndices.OBJECT])
    }

    for (i in predefinedClasses.indices) {
        val clazz = predefinedClasses[i]
        gIndex.classIndex[clazz] = i
        gIndex.classNamesByIndex.add(clazz)
    }

    assertEquals(gIndex.classIndex["java/lang/Object"], StaticClassIndices.OBJECT)
    assertEquals(gIndex.classIndex["java/lang/String"], StaticClassIndices.STRING)
    assertEquals(gIndex.classIndex["[]"], StaticClassIndices.ARRAY)

    assertEquals(gIndex.classIndex["int"], StaticClassIndices.NATIVE_INT)
    assertEquals(gIndex.classIndex["long"], StaticClassIndices.NATIVE_LONG)
    assertEquals(gIndex.classIndex["float"], StaticClassIndices.NATIVE_FLOAT)
    assertEquals(gIndex.classIndex["double"], StaticClassIndices.NATIVE_DOUBLE)
    assertEquals(gIndex.classIndex["boolean"], StaticClassIndices.NATIVE_BOOLEAN)
    assertEquals(gIndex.classIndex["byte"], StaticClassIndices.NATIVE_BYTE)
    assertEquals(gIndex.classIndex["short"], StaticClassIndices.NATIVE_SHORT)
    assertEquals(gIndex.classIndex["char"], StaticClassIndices.NATIVE_CHAR)
    assertEquals(gIndex.classIndex["void"], StaticClassIndices.NATIVE_VOID)

    for (i in StaticClassIndices.FIRST_NATIVE..StaticClassIndices.LAST_NATIVE) {
        hIndex.registerSuperClass(predefinedClasses[i], predefinedClasses[StaticClassIndices.OBJECT])
    }

    // todo confirm type shift

    registerDefaultOffsets()
    indexHierarchyFromEntryPoints()

    hIndex.notImplementedMethods.removeAll(hIndex.jvmImplementedMethods)
    hIndex.abstractMethods.removeAll(hIndex.jvmImplementedMethods)
    hIndex.nativeMethods.removeAll(hIndex.jvmImplementedMethods)

    resolveGenericTypes()
    findNoThrowMethods()

    // java_lang_StrictMath_sqrt_DD
    hIndex.inlined[MethodSig.c("java/lang/StrictMath", "sqrt", "(D)D", true)] = listOf(F64_SQRT)
    hIndex.inlined[MethodSig.c("java/lang/Math", "sqrt", "(D)D", true)] = listOf(F64_SQRT)

    findAliases()

    val (entryPoints, entryClasses) = collectEntryPoints()

    findExportedMethods()

    // unknown (because idk where [] is implemented), can cause confusion for my compiler
    hIndex.finalMethods.add(MethodSig.c("[]", "clone", "()Ljava/lang/Object;", false))

    replaceRenamedDependencies()
    checkMissingClasses()

    resolveAll(entryClasses, entryPoints)

    val staticCallOrder = if (callStaticInitOnce) {
        StaticDependencies.calculateStaticCallOrder()
    } else null

    // todo this is somehow used, not-implemented, but we don't crash
    val checked = MethodSig.c(
        "kotlyn/reflect/full/KClasses", "getSuperclasses",
        "(Lkotlin/reflect/KClass;)Ljava/util/List;", true
    )
    printUsed(checked)
    println("flags: ${hIndex.classFlags[checked.clazz]}")
    assertFalse(checked in hIndex.notImplementedMethods)

    if (false) {
        printUsed(validFinal)
        printUsed(invalidFinal)
        assertTrue(validFinal.clazz in dIndex.constructableClasses)
        assertTrue(invalidFinal.clazz in dIndex.constructableClasses)
        assertTrue(invalidFinal in dIndex.usedMethods)
        assertTrue(validFinal in dIndex.usedMethods, "Valid final is unused??/2")
    }

    indexFieldsInSyntheticMethods()
    calculateFieldOffsets()
    assignNativeCode()

    headerPrinter.append("(module\n")

    if (useWASMExceptions) {
        importPrinter.append("(import \"extmod\" \"exttag\" (tag \$exTag (param i32)))\n")
    }

    importPrinter.import1("fcmpl", listOf(f32, f32), listOf(i32))
    importPrinter.import1("fcmpg", listOf(f32, f32), listOf(i32))
    importPrinter.import1("dcmpl", listOf(f64, f64), listOf(i32))
    importPrinter.import1("dcmpg", listOf(f64, f64), listOf(i32))

    // this should be empty, and replaced using functions with JavaScript-pseudo implementations
    val missingMethods = HashSet<MethodSig>(4096)
    printForbiddenMethods(importPrinter, missingMethods)

    // only now usedMethods is complete
    printMethodFieldStats()

    for (sig in dIndex.usedMethods) {
        if (sig !in hIndex.nativeMethods) continue
        if (hIndex.hasAnnotation(sig, Annotations.JAVASCRIPT) ||
            hIndex.hasAnnotation(sig, Annotations.WASM)
        ) {
            hIndex.notImplementedMethods.remove(sig)
            hIndex.customImplementedMethods.add(sig)
            // println("Marked $sig as custom")
        }
    }

    printNotImplementedMethods(importPrinter, missingMethods)
    printNativeMethods(importPrinter, missingMethods)

    val jsImplemented = generateJavaScriptFile(missingMethods)
    val jsPseudoImplemented = createPseudoJSImplementations(jsImplemented, missingMethods)

    printAbstractMethods(bodyPrinter, missingMethods)

    for (sig in dIndex.usedMethods) {
        if (sig in hIndex.jvmImplementedMethods) {
            implementedMethods[methodName(sig)] = sig
        }
    }

    // 4811/15181 -> 3833/15181, 1906 unique
    findUniquelyImplemented(
        dIndex.usedMethods.filter { hIndex.getAlias(it) == it },
        implementedMethods.values.toSet()
    )

    /** translate method implementations */
    // find all aliases, that are being used
    val aliasedMethods = dIndex.usedMethods.map {
        hIndex.getAlias(it)
    }

    fun filterClass(clazz: String): Boolean {
        return !clazz.startsWith("[") &&
                clazz !in NativeTypes.nativeTypes
    }

    val classesToLoad = findClassesToLoad(aliasedMethods)
    indexMethodsIntoGIndex(classesToLoad, predefinedClasses, ::filterClass)
    ensureIndexForConstructableClasses()
    ensureIndexForInterfacesAndSuperClasses()

    /**
     * calculate static layout and then set string start
     * */
    var ptr = dataStart
    val numClasses = gIndex.classIndex.size
    gIndex.lockClasses = true
    ptr = appendStaticInstanceTable(dataPrinter, ptr, numClasses)
    stringStart = ptr

    appendNativeHelperFunctions()
    translateMethods(classesToLoad, ::filterClass)
    buildSyntheticMethods()

    ptr = appendStringData(dataPrinter, gIndex) // idx -> string

    val classTableStart = ptr

    // class -> super, instance size, interfaces, interface functions
    ptr = appendInheritanceTable(dataPrinter, ptr, numClasses)
    // class -> function[] for resolveIndirect
    ptr = appendInvokeDynamicTable(dataPrinter, ptr, numClasses)
    // java.lang.Class, with name, fields and methods
    ptr = appendClassInstanceTable(dataPrinter, ptr, numClasses)
    // idx -> class, line for stack trace
    ptr = appendStackTraceTable(dataPrinter, ptr)
    // length, [String, byte[]]x resources
    ptr = appendResourceTable(dataPrinter, ptr)

    // must come after invoke dynamic
    appendDynamicFunctionTable(dataPrinter, implementedMethods) // idx -> function
    appendFunctionTypes(dataPrinter)

    val usedButNotImplemented = HashSet<String>(gIndex.actuallyUsed.usedBy.keys)

    for (func in jsImplemented) usedButNotImplemented.remove(func.key)
    for (func in jsPseudoImplemented) usedButNotImplemented.remove(func.key)
    for (func in gIndex.translatedMethods) usedButNotImplemented.remove(methodName(func.key))
    for ((name, dlu) in DelayedLambdaUpdate.needingBridgeUpdate) {
        if (name in dIndex.constructableClasses) {
            usedButNotImplemented.remove(methodName(dlu.calledMethod))
            usedButNotImplemented.remove(methodName(dlu.bridgeMethod))
        }
    }

    // append nth-getter-methods
    for (desc in gIndex.nthGetterMethods.values.sortedWith(FunctionOrder)) {
        bodyPrinter.append(desc)
    }

    listEntryPoints({
        for (sig in hIndex.methodsByClass[it]!!) {
            ActuallyUsedIndex.add(entrySig, sig)
        }
    }, { sig ->
        ActuallyUsedIndex.add(entrySig, sig)
    })

    val usedMethods = ActuallyUsedIndex.resolve()
    usedButNotImplemented.retainAll(usedMethods)

    val nameToMethod = calculateNameToMethod()
    val usedBotNotImplementedMethods =
        usedButNotImplemented
            .mapNotNull { nameToMethod[it] }

    for (sig in usedBotNotImplementedMethods) {
        if (sig.clazz in hIndex.interfaceClasses &&
            sig !in hIndex.jvmImplementedMethods &&
            sig !in hIndex.customImplementedMethods
        ) {
            usedButNotImplemented.remove(methodName(sig))
        }
        if (sig in hIndex.abstractMethods) {
            usedButNotImplemented.remove(methodName(sig))
        }
    }

    if (usedButNotImplemented.isNotEmpty()) {
        printMissingFunctions(usedButNotImplemented, usedMethods)
    }

    fun printGlobal(name: String, type: String, type2: String, value: Int) {
        // can be mutable...
        dataPrinter.append("(global $").append(name).append(" ").append(type).append(" (").append(type2)
            .append(".const ").append(value).append("))\n")
    }

    fun defineGlobal(name: String, type: String, value: Int, isMutable: Boolean = false) {
        printGlobal(name, if (isMutable) "(mut $type)" else type, type, value)
        globals.add(GlobalVariable("global_$name", type, value, isMutable))
    }

    dataPrinter.append(";; globals:\n")
    defineGlobal("inheritanceTable", ptrType, classTableStart) // class table
    defineGlobal("staticTable", ptrType, staticTablePtr) // static table
    defineGlobal("resolveIndirectTable", ptrType, resolveIndirectTablePtr)
    defineGlobal("numClasses", ptrType, numClasses)
    defineGlobal("classInstanceTable", ptrType, classInstanceTablePtr)
    defineGlobal("classSize", ptrType, gIndex.getFieldOffsets("java/lang/Class", false).offset)
    defineGlobal("staticInitTable", ptrType, staticInitFlagTablePtr)
    defineGlobal("stackTraceTable", ptrType, stackTraceTablePtr)
    defineGlobal("resourceTable", ptrType, resourceTablePtr)

    defineGlobal("stackEndPointer", ptrType, ptr) // stack end ptr
    ptr += stackSize
    defineGlobal("stackPointer", ptrType, ptr, true) // stack ptr
    defineGlobal("stackPointerStart", ptrType, ptr) // stack ptr start address

    ptr = align(ptr + 4, 16)
    // allocation start address
    defineGlobal("allocationPointer", ptrType, ptr, true)
    defineGlobal("allocationStart", ptrType, ptr) // original allocation start address

    if (callStaticInitAtCompileTime) {
        val extraMemory = 16 shl 20
        val vm = WASMEngine(ptr + extraMemory)
        assertTrue(globals.isNotEmpty()) // should not be empty
        vm.registerGlobals(globals)
        vm.registerSpecialFunctions()
        vm.registerMemorySections(segments)
        assertTrue(translatedMethods.isNotEmpty()) // should not be empty
        val functions = translatedMethods.values + helperFunctions.values + nthGetterMethods.values
        vm.registerFunctions(functions)
        assertTrue(functionTable.isNotEmpty()) // usually should not be empty
        vm.registerFunctionTable(functionTable)
        // todo create vm
        // todo initialize memory properly
        // todo call all static init functions
        val staticInitFunctions = dIndex.usedMethods
            .filter { it.name == STATIC_INIT }
            .sortedBy { it.name } // for a little consistency ^^
            .map { methodName(it) }
        for (name in staticInitFunctions) {
            val func = vm.getFunction(name)
            vm.executeFunction(func)
        }
    }

    printMethodImplementations(bodyPrinter, usedMethods)
    printInterfaceIndex()

    val sizeInPages = ceilDiv(ptr, 65536) + 1 // number of 64 kiB pages
    headerPrinter.append("(memory (import \"js\" \"mem\") ").append(sizeInPages).append(")\n")

    createJSImports(jsImplemented, jsPseudoImplemented, sizeInPages)

    // close module
    headerPrinter.ensureExtra(importPrinter.size + dataPrinter.size + bodyPrinter.size)
    headerPrinter.append(importPrinter)
    headerPrinter.append(dataPrinter)
    headerPrinter.append(bodyPrinter)
    headerPrinter.append(") ;; end of module\n")

    println("  Total size (with comments): ${headerPrinter.length.toLong().formatFileSize(1024)}")
    println("  Setter/Getter-Methods: ${hIndex.setterMethods.size}/${hIndex.getterMethods.size}")

    compileToWASM(headerPrinter)

    println("  ${dIndex.constructableClasses.size}/${gIndex.classNamesByIndex.size} classes are constructable")
}

val globals = ArrayList<GlobalVariable>()

fun printMissingFunctions(usedButNotImplemented: Set<String>, resolved: Set<String>) {
    println("\nMissing functions:")
    val nameToMethod = calculateNameToMethod()
    for (name in usedButNotImplemented) {
        println("  $name")
        println("    resolved: ${name in resolved}")
        val sig = hIndex.getAlias(name) ?: nameToMethod[name]
        if (sig != null) {
            println("    alias: " + hIndex.getAlias(name))
            println("    name2method: " + nameToMethod[name])
            println("    translated: " + (sig in gIndex.translatedMethods))
            print("    ")
            printUsed(sig)
        }
    }
    println()
    println("additional info:")
    printUsed(MethodSig.c("java/lang/reflect/Constructor", "getDeclaredAnnotations", "()[Ljava/lang/Object;", false))
    printUsed(MethodSig.c("java/lang/reflect/Executable", "getDeclaredAnnotations", "()[Ljava/lang/Object;", false))
    throw IllegalStateException("Missing ${usedButNotImplemented.size} functions")
}
