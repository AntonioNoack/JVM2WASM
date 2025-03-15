import dependency.ActuallyUsedIndex
import dependency.DependencyIndex
import dependency.DependencyIndex.constructableClasses
import dependency.StaticDependencies
import hierarchy.DelayedLambdaUpdate
import hierarchy.HierarchyIndex
import interpreter.WASMEngine
import interpreter.functions.TrackCallocInstr
import interpreter.memory.MemoryOptimizer
import interpreter.memory.StaticInitRemover
import jvm.JVM32
import me.anno.io.Streams.readText
import me.anno.maths.Maths.align
import me.anno.maths.Maths.ceilDiv
import me.anno.maths.Maths.min
import me.anno.utils.Clock
import me.anno.utils.assertions.assertTrue
import me.anno.utils.files.Files.formatFileSize
import me.anno.utils.types.Floats.f1
import me.anno.utils.types.Floats.f3
import org.apache.logging.log4j.LogManager
import org.objectweb.asm.Opcodes.ASM9
import translator.GeneratorIndex
import translator.GeneratorIndex.classNamesByIndex
import translator.GeneratorIndex.dataStart
import translator.GeneratorIndex.nthGetterMethods
import translator.GeneratorIndex.stringStart
import translator.GeneratorIndex.translatedMethods
import translator.MethodTranslator.Companion.comments
import utils.*
import utils.DefaultClassLayouts.registerDefaultOffsets
import utils.DynIndex.appendDynamicFunctionTable
import utils.DynIndex.appendInheritanceTable
import utils.DynIndex.appendInvokeDynamicTable
import utils.DynIndex.resolveIndirectTablePtr
import utils.NativeHelperFunctions.appendNativeHelperFunctions
import utils.PrintUsed.printUsed
import utils.WASMTypes.*
import wasm.instr.Instructions.F64_SQRT
import wasm.parser.GlobalVariable
import wasm2cpp.FunctionOrder
import java.io.FileNotFoundException
import kotlin.math.ceil
import kotlin.math.sin

const val api = ASM9
private val LOGGER = LogManager.getLogger("JVM2WASM")

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

// todo find pseudo-instances, which are actually created exactly once,
//  and make all their fields static, and eliminate that instance (maybe replace it with a shared standard java/lang/Object)
// todo Companion-objects are unique, so make all their fields and themselves static;
//  we don't need them to be instances


// optimizations:
// done when there is no child implementations for a non-final, non-static function, call it directly
// todo also track, which methods theoretically can throw errors: mark them, and all their callers; -> optimization
// call_indirect/interface is more complicated... all methods need to have the same signature
// done if there is only a small number of implementations, make resolveDirect() use a if-else-chain instead (-> modify findUniquelyImplemented())

// todo test LuaScripts -> magically not working :/

// todo combine non-exported functions with the same content :3

// do we already replace dependencies before resolving them? probably could save us tons of space (and wabt compile time 😁)
// done mark empty functions as such, and skip them; e.g. new_java_lang_Object_V, new_java_lang_Number_V
//  doesn't work recursively yet though

// todo loading bar for library loading


// to do find pure (nothrow) functions, and spread them across other methods
// todo implement WASM throwables instead

// we even could compile scene files to WASM for extremely fast load times 😄

var exportAll = false // 15% space-saving in WASM; 10% in compressed
var exportHelpers = exportAll

var useKotlynReflect = true
var useDefaultKotlinReflection = false

var alignFieldsProperly = true

var callStaticInitOnce = false // not supported, because there is lots of cyclic dependencies (24 cycles)
var callStaticInitAtCompileTime = false // todo implement this

// todo this needs catch-blocks, somehow..., and we get a lot of type-mismatch errors at the moment
var useWASMExceptions = false
var crashOnAllExceptions = true
val useResultForThrowables = !useWASMExceptions && !crashOnAllExceptions

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
    "java/util/Locale" to "jvm/custom/Locale",
    "java/util/Calendar" to "jvm/custom/Calendar",
    "java/text/SimpleDateFormat" to "jvm/custom/SimpleDateFormat",
    "java/util/zip/Inflater" to "jvm/custom/Inflater",
    "java/util/zip/Deflater" to "jvm/custom/Deflater",
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
    clazz("jvm/ThrowJS")

    if (checkArrayAccess) {
        clazz("jvm/ArrayAccessSafe")
    } else {
        clazz("jvm/ArrayAccessUnchecked")
    }

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

val clock = Clock("JVM2WASM")

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

var allocationStart = -1
val entrySig = MethodSig.c("", "entry", "()V", false)
val resolvedMethods = HashMap<MethodSig, MethodSig>(4096)
fun main() {
    jvm2wasm()
}

fun jvm2wasm() {

    val clock = Clock(LOGGER)

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
        // do we really need to hardcode them? yes, for convenience in JavaScript
        // todo -> use named constants in JavaScript
        "java/lang/String", // #10
        "java/lang/Class", // #11
        "java/lang/System", // #12
        "java/io/Serializable", // #13
        "java/lang/Throwable", // #14
        "java/lang/StackTraceElement", // #15
        "int", "long", "float", "double",
        "boolean", "byte", "short", "char", "void", // #24
    )

    for (i in 1 until 13) {
        hIndex.registerSuperClass(predefinedClasses[i], "java/lang/Object")
    }

    for (i in predefinedClasses.indices) {
        val clazz = predefinedClasses[i]
        gIndex.classIndex[clazz] = i
        gIndex.classNamesByIndex.add(clazz)
    }

    StaticClassIndices.validateClassIndices()

    for (i in StaticClassIndices.FIRST_NATIVE..StaticClassIndices.LAST_NATIVE) {
        hIndex.registerSuperClass(predefinedClasses[i], "java/lang/Object")
    }

    // todo confirm type shift using WASMEngine

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

    // doesn't work yet, because of java/lang-class interdependencies,
    // and probably lots of them in our project, too
    val staticCallOrder = StaticDependencies.calculatePartialStaticCallOrder()

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

    fun defineGlobal(name: String, type: String, value: Int, isMutable: Boolean = false) {
        globals[name] = GlobalVariable(name, type, value, isMutable)
    }

    defineGlobal("inheritanceTable", ptrType, classTableStart) // class table
    defineGlobal("staticTable", ptrType, staticTablePtr) // static table
    defineGlobal("resolveIndirectTable", ptrType, resolveIndirectTablePtr)
    defineGlobal("numClasses", ptrType, numClasses)
    defineGlobal("classInstanceTable", ptrType, classInstanceTablePtr)
    defineGlobal("classSize", ptrType, gIndex.getInstanceSize("java/lang/Class"))
    defineGlobal("staticInitTable", ptrType, staticInitFlagTablePtr)
    defineGlobal("stackTraceTable", ptrType, stackTraceTablePtr)
    defineGlobal("resourceTable", ptrType, resourceTablePtr)

    defineGlobal("stackEndPointer", ptrType, ptr) // stack end ptr
    ptr += stackSize
    defineGlobal("stackPointer", ptrType, ptr, true) // stack ptr
    defineGlobal("stackPointerStart", ptrType, ptr) // stack ptr start address

    ptr = align(ptr + 4, 16)
    allocationStart = ptr
    // allocation start address
    defineGlobal("allocationPointer", ptrType, ptr, true)
    defineGlobal("allocationStart", ptrType, ptr) // original allocation start address

    // todo code-size optimization:
    //  inline calls to functions, which only call

    // todo code-size optimization:
    //  1. resolve what we need by all entry points
    //  2. resolve what we need by static-init and translate it
    //  3. execute static init
    //  4. resolve what we need without static-init and re-translate/optimize&ship it
    //    - also optimize static field reading: many will be read-only after static-init

    if (callStaticInitAtCompileTime) {

        // create VM
        val originalMemory = ptr
        val extraMemory = 17 shl 20
        val vm = WASMEngine(originalMemory + extraMemory)
        assertTrue(globals.isNotEmpty()) // should not be empty
        vm.registerGlobals(globals)
        vm.registerSpecialFunctions()
        vm.registerMemorySections(segments)
        assertTrue(translatedMethods.isNotEmpty()) // should not be empty
        vm.registerFunctions(translatedMethods.values)
        vm.registerFunctions(helperFunctions.values)
        vm.registerFunctions(nthGetterMethods.values)
        vm.resolveCalls()
        assertTrue(functionTable.isNotEmpty()) // usually should not be empty
        vm.registerFunctionTable(functionTable)
        // call all static init functions;
        // partially sort these methods by dependencies
        //  for better code-complexity and allocation measurements
        val staticInitFunctions = staticCallOrder
        val time0i = System.nanoTime()
        try {
            var instr0 = vm.instructionCounter
            var memory0 = vm.globals["allocationPointer"]!!.toInt()
            var time0 = time0i
            val minLogInstructions = 2_000_000L
            val minLogSize = 64_000
            LOGGER.info("Total static init functions: ${staticInitFunctions.size}")
            LOGGER.info("[-1] init")
            vm.executeFunction("init")
            fun printStats() {
                val minPrintedCount = 0
                val maxPrintedClasses = 10
                if (WASMEngine.printCallocSummary) {
                    val allocations = TrackCallocInstr.counters.entries
                        .filter { it.value >= minPrintedCount }
                        .sortedByDescending { it.value }
                    if (allocations.isNotEmpty()) {
                        LOGGER.info("   Allocations:")
                        for (k in 0 until min(allocations.size, maxPrintedClasses)) {
                            val (classId, count) = allocations[k]
                            val className = gIndex.classNamesByIndex[classId]
                            LOGGER.info("   - ${count}x $className")
                        }
                        if (allocations.size > maxPrintedClasses) {
                            val more = allocations.size - maxPrintedClasses
                            val total = allocations.sumOf { it.value }
                            LOGGER.info("     ... ($more more, $total total)")
                        }
                    }
                    TrackCallocInstr.counters.clear()
                }
                val instrI = vm.instructionCounter
                val memory1 = vm.globals["allocationPointer"]!!.toInt()
                val timeI = System.nanoTime()
                if (instrI - instr0 > minLogInstructions)
                    LOGGER.info(
                        "   " +
                                "${((instrI - instr0) / 1e6f).f1()} MInstr, " +
                                "${ceil((timeI - time0) / 1e6f).toInt()} ms, " +
                                "${((instrI - instr0) * 1e3f / (timeI - time0)).toInt()} MInstr/s"
                    )
                if (memory1 - memory0 > minLogSize) LOGGER.info("   +${(memory1 - memory0).formatFileSize()}")
                instr0 = instrI
                memory0 = memory1
                time0 = timeI
            }
            printStats()
            for (i in staticInitFunctions.indices) {
                val name = staticInitFunctions[i]
                LOGGER.info("[$i] $name")
                vm.executeFunction(methodName(name))
                printStats()
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
        // calculate how much new memory was used
        val timeI = System.nanoTime()
        val allocationStart = vm.globals["allocationStart"]!!.toInt()
        val allocationPointer = vm.globals["allocationPointer"]!!.toInt()
        LOGGER.info("Base Memory: ($allocationStart) ${allocationStart.formatFileSize()}")
        LOGGER.info("Allocated ${(allocationPointer - allocationStart).formatFileSize()} during StaticInit")
        LOGGER.info("Executed ${vm.instructionCounter} instructions for StaticInit")
        LOGGER.info(
            "Took ${((timeI - time0i) / 1e6f).f3()} s for that, " +
                    "${(vm.instructionCounter * 1e3f / (timeI - time0i)).f1()} MInstr/s"
        )
        ptr = MemoryOptimizer.optimizeMemory(vm, dataPrinter)
        StaticInitRemover.removeStaticInit()
        LOGGER.info("New Base Memory: $allocationStart (${allocationStart.formatFileSize()})")
        clock.stop("StaticInit WASM-VM")
    }

    if (comments) dataPrinter.append(";; globals:\n")

    fun printGlobal(name: String, type: String, type2: String, value: Int) {
        // can be mutable...
        dataPrinter.append("(global $").append(name).append(" ").append(type).append(" (").append(type2)
            .append(".const ").append(value).append("))\n")
    }

    for (global in globals.values.sortedBy { it.name }) {
        val isMutable = global.isMutable
        val type = global.type
        val value = global.initialValue
        printGlobal(global.name, if (isMutable) "(mut $type)" else type, type, value)
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
    headerPrinter.append(if (comments) ") ;; end of module\n" else ")\n")

    LOGGER.info(
        "WAT size (${if (comments) "with" else "without"} comments): " +
                headerPrinter.length.formatFileSize()
    )
    LOGGER.info("Setter/Getter-Methods: ${hIndex.setterMethods.size}/${hIndex.getterMethods.size}")
    LOGGER.info(
        "Number of constant Strings: ${gIndex.stringSet.size}, " +
                "size: ${gIndex.totalStringSize.formatFileSize()}"
    )
    LOGGER.info("${constructableClasses.size}/${classNamesByIndex.size} classes are constructable")

    compileToWASM(headerPrinter)
}

val globals = HashMap<String, GlobalVariable>()

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
