import dependency.ActuallyUsedIndex
import dependency.ActuallyUsedIndex.addEntryPointsToActuallyUsed
import dependency.DependencyIndex
import dependency.DependencyIndex.constructableClasses
import dependency.StaticDependencies
import hierarchy.HierarchyIndex
import jvm.JVM32
import me.anno.engine.inspector.CachedReflections.Companion.getGetterName
import me.anno.io.Streams.readText
import me.anno.maths.Maths.ceilDiv
import me.anno.utils.Clock
import me.anno.utils.assertions.assertTrue
import me.anno.utils.files.Files.formatFileSize
import me.anno.utils.types.Strings.titlecase
import org.apache.logging.log4j.LogManager
import translator.GeneratorIndex
import translator.GeneratorIndex.alignPointer
import translator.GeneratorIndex.classNamesByIndex
import translator.GeneratorIndex.dataStart
import translator.GeneratorIndex.stringStart
import translator.MethodTranslator.Companion.comments
import utils.*
import utils.DefaultClassLayouts.registerDefaultOffsets
import utils.DefaultClasses.registerDefaultClasses
import utils.Descriptor.Companion.voidDescriptor
import utils.DynIndex.appendDynamicFunctionTable
import utils.DynIndex.appendInheritanceTable
import utils.DynIndex.appendInvokeDynamicTable
import utils.DynIndex.calculateDynamicFunctionTable
import utils.DynIndex.resolveIndirectTablePtr
import utils.MissingFunctions.checkUsedButNotImplemented
import utils.MissingFunctions.findUsedButNotImplemented
import utils.NativeHelperFunctions.appendNativeHelperFunctions
import utils.WASMTypes.*
import wasm.instr.Instructions.F64_SQRT
import wasm.parser.GlobalVariable
import wasm2cpp.FunctionOrder
import java.io.FileNotFoundException
import kotlin.math.sin

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

// todo it would be nice to create a heap-dump after a crash in C++/JS onto disk,
//  and to create an explorer for it, which shows all objects, properties, ...

// todo find pseudo-instances, which are actually created exactly once,
//  and make all their fields static, and eliminate that instance (maybe replace it with a shared standard java/lang/Object)
// todo Companion-objects are unique, so make all their fields and themselves static;
//  we don't need them to be instances

// todo test: compile our C++ using Emscripten, and compare its performance to our own WASM (using SciMark)

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

// not supported, because there is lots of cyclic dependencies (24 cycles)
// var callStaticInitOnce = false

/**
 * calls all static-init-blocks at compile time using an interpreter:
 * - static init code can be removed from the final executable
 * - startup time might be slightly reduced
 * - executable constant size gets larger
 *
 * -> regarding WASM, the resulting file is ~12% bigger after compression
 * */
var callStaticInitAtCompileTime = false

// todo this needs catch-blocks, somehow..., and we get a lot of type-mismatch errors at the moment
var useWASMExceptions = false
var crashOnAllExceptions = true
val useResultForThrowables = !useWASMExceptions && !crashOnAllExceptions

// experimental, not really JVM conform; might work anyway 😄, and be faster or use less memory
var enableTracing = false
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
    "java/util/concurrent/Semaphore" to "jvm/custom/Semaphore",
    "java/util/concurrent/ThreadLocalRandom" to "jvm/custom/ThreadLocalRandom",
    "java/lang/ref/WeakReference" to "jvm/custom/WeakRef",
    "java/lang/String" to if (useUTF8Strings) "jvm/custom/UTF8String" else "java/lang/String",
    "java/io/BufferedWriter" to if (byteStrings) "jvm/utf8/BufferedWriterUTF8" else "java/io/BufferedWriter",
    "java/io/OutputStreamWriter" to "jvm/utf8/OutputStreamWriterUTF8",
    // "java/lang/StringBuilder" to "jvm/utf8/StringBuilderUTF8",
    // "java/util/stream/IntStream" to "jvm/utf8/IntStreamV2", // only used for codepoints()
    "kotlin/SynchronizedLazyImpl" to "jvm/custom/SimpleLazy",
    "java/io/File" to "jvm/custom/File",
    "java/io/RandomAccessFile" to "jvm/custom/RandomAccessFile",
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
            (clazz.startsWith("java/util/concurrent/") && // not useful without threading
                    clazz != "java/util/concurrent/TimeoutException" &&
                    clazz != "java/util/concurrent/TimeUnit") ||
            clazz.startsWith("java/awt/image/") || // not available anyway
            clazz.startsWith("java/awt/Component") || // not available anyway
            clazz.startsWith("javax/imageio/") || // not available anyway
            clazz.startsWith("java/util/regex/") || // let's see how much space it takes -> 2.2 MB wasm text out of 70
            clazz.startsWith("java/security/") ||
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
        method(MethodSig.c("java/lang/Class", "getName", "()Ljava/lang/String;"))
        method(MethodSig.c("java/lang/Object", "toString", "()Ljava/lang/String;"))
        method(MethodSig.c("java/lang/Thread", INSTANCE_INIT, voidDescriptor))
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

        LOGGER.info("classes:")
        for (clazz in dIndex.constructableClasses) {
            LOGGER.info("[${gIndex.getClassIndex(clazz)}] $clazz")
        }
        LOGGER.info()

        LOGGER.info("methods:")
        for (m in dIndex.usedMethods.map { methodName(it) }.sorted()) {
            LOGGER.info("- $m")
        }
        LOGGER.info()

        LOGGER.info("fields:")
        for (field in usedFields.map { it.toString() }.toSortedSet()) {
            LOGGER.info("- $field")
        }
        LOGGER.info()
    }

    LOGGER.info(
        "${dIndex.usedMethods.size} methods + ${usedFields.size} fields, " +
                "${hIndex.superClass.size} classes indexed, ${dIndex.constructableClasses.size} constructable"
    )
}

val hIndex = HierarchyIndex
val dIndex = DependencyIndex
val gIndex = GeneratorIndex

val implementedMethods = HashMap<String, MethodSig>(1 shl 16)

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
val entrySig = MethodSig.c("", "entry", voidDescriptor)
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

    // todo confirm type shift using WASMEngine

    val predefinedClasses = registerDefaultClasses()
    registerDefaultOffsets()
    indexHierarchyFromEntryPoints()
    clock.stop("Index Hierarchy")

    applyKotlinFieldAnnotations()
    clock.stop("Apply Kotlin Field Annotations")

    cleanupJVMImplemented()
    clock.stop("Cleanup JVM Implemented")

    resolveGenericMethodTypes()
    clock.stop("Resolve Generics")

    findNoThrowMethods()
    clock.stop("Find NoThrow-Methods")

    // java_lang_StrictMath_sqrt_DD
    hIndex.inlined[MethodSig.c("java/lang/StrictMath", "sqrt", "(D)D")] = listOf(F64_SQRT)
    hIndex.inlined[MethodSig.c("java/lang/Math", "sqrt", "(D)D")] = listOf(F64_SQRT)

    findAliases()
    clock.stop("Find Aliases")

    val (entryPoints, entryClasses) = collectEntryPoints()
    clock.stop("Collect Entry Points")

    findExportedMethods()
    clock.stop("Find Exported Methods")

    // unknown (because idk where [] is implemented), can cause confusion for my compiler
    hIndex.finalMethods.add(MethodSig.c("[]", "clone", "()Ljava/lang/Object;"))

    replaceRenamedDependencies(clock)
    clock.stop("Replace Renamed Methods")

    checkMissingClasses()
    clock.stop("Check missing classes")

    resolveAll(entryClasses, entryPoints)
    clock.stop("Resolve All")

    // doesn't work yet, because of java/lang-class interdependencies,
    // and probably lots of them in our project, too
    val staticCallOrder = StaticDependencies.calculatePartialStaticCallOrder()
    clock.stop("Static Call Order")

    indexFieldsInSyntheticMethods()
    calculateFieldOffsets()
    clock.stop("Field Offsets")

    parseInlineWASM()
    clock.stop("Parse Inline WASM")

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
        if (!hIndex.isNative(sig)) continue
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
    clock.stop("Printing forbidden/native/... methods")

    for (sig in dIndex.usedMethods) {
        if (sig in hIndex.jvmImplementedMethods) {
            implementedMethods[methodName(sig)] = sig
        }
    }

    // 4811/15181 -> 3833/15181, 1906 unique
    findUniquelyImplemented(
        dIndex.usedMethods.filter { hIndex.getAlias(it) == it },
        implementedMethods.values.toSet(), clock
    )
    clock.stop("Uniquely Implemented Methods")

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
    ensureIndexForAnnotations()

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
    calculateDynamicFunctionTable() // idx -> function

    appendNthGetterMethods(bodyPrinter)

    val usedButNotImplemented = findUsedButNotImplemented(jsImplemented, jsPseudoImplemented)

    addEntryPointsToActuallyUsed()

    val usedMethods = ActuallyUsedIndex.resolve()
    checkUsedButNotImplemented(usedMethods, usedButNotImplemented)

    clock.stop("Before Globals")

    ptr = defineGlobals(classTableStart, numClasses, ptr)

    // todo code-size optimization:
    //  inline calls to functions, which only call

    // todo code-size optimization:
    //  1. resolve what we need by all entry points (done)
    //  2. resolve what we need by static-init and translate it (we still do too much)
    //  3. execute static init
    //  4. resolve what we need without static-init and re-translate/optimize&ship it
    //    - also optimize static field reading: many will be read-only after static-init

    if (callStaticInitAtCompileTime) {
        ptr = CallStaticInit.callStaticInitAtCompileTime(ptr, staticCallOrder, dataPrinter)
    }

    appendDynamicFunctionTable(dataPrinter)
    appendFunctionTypes(dataPrinter)

    appendGlobals(dataPrinter)

    printMethodImplementations(bodyPrinter, usedMethods)
    printInterfaceIndex()

    val sizeInPages = ceilDiv(ptr, 65536) + 1 // number of 64 kiB pages

    writeJavaScriptImportsFile(jsImplemented, jsPseudoImplemented, sizeInPages)

    val joined = joinPrinters(
        sizeInPages, headerPrinter,
        importPrinter, dataPrinter, bodyPrinter
    )

    printStats(joined)
    compileToWASM(joined)
}

private fun cleanupJVMImplemented() {
    hIndex.notImplementedMethods.removeAll(hIndex.jvmImplementedMethods)
    hIndex.abstractMethods.removeAll(hIndex.jvmImplementedMethods)
    hIndex.nativeMethods.removeAll(hIndex.jvmImplementedMethods)
}

private fun appendNthGetterMethods(bodyPrinter: StringBuilder2) {
    // append nth-getter-methods
    for (desc in gIndex.nthGetterMethods.values.sortedWith(FunctionOrder)) {
        bodyPrinter.append(desc)
    }
}

private fun defineGlobal(name: String, type: String, value: Int, isMutable: Boolean = false) {
    globals[name] = GlobalVariable(name, type, value, isMutable)
}

private fun defineGlobals(classTableStart: Int, numClasses: Int, ptr0: Int): Int {

    defineGlobal("inheritanceTable", ptrType, classTableStart) // class table
    defineGlobal("staticTable", ptrType, staticFieldOffsetsPtr) // static table
    defineGlobal("resolveIndirectTable", ptrType, resolveIndirectTablePtr)
    defineGlobal("numClasses", ptrType, numClasses)
    defineGlobal("classInstanceTable", ptrType, classInstanceTablePtr)
    defineGlobal("classSize", ptrType, gIndex.getInstanceSize("java/lang/Class"))
    defineGlobal("staticInitTable", ptrType, staticInitFlagsPtr)
    defineGlobal("stackTraceTable", ptrType, stackTraceTablePtr)
    defineGlobal("resourceTable", ptrType, resourceTablePtr)

    var ptr = ptr0
    defineGlobal("stackEndPointer", ptrType, ptr) // stack end ptr
    ptr += stackSize
    defineGlobal("stackPointer", ptrType, ptr, true) // stack ptr
    defineGlobal("stackPointerStart", ptrType, ptr) // stack ptr start address

    ptr = alignPointer(ptr + 4) // +4 for the top stack entry
    allocationStart = ptr
    // allocation start address
    defineGlobal("allocationPointer", ptrType, ptr, true)
    defineGlobal("allocationStart", ptrType, ptr) // original allocation start address
    return ptr
}

private fun appendGlobals(dataPrinter: StringBuilder2) {
    if (comments) dataPrinter.append(";; globals:\n")
    for (global in globals.values.sortedBy { it.name }) {
        dataPrinter.append("(global $").append(global.name).append(" ")
        if (global.isMutable) dataPrinter.append("(mut ")
        dataPrinter.append(global.type)
        if (global.isMutable) dataPrinter.append(')')
        dataPrinter
            .append(" (").append(global.type).append(".const ")
            .append(global.initialValue).append("))\n")
    }
}

private fun joinPrinters(
    sizeInPages: Int,
    headerPrinter: StringBuilder2, importPrinter: StringBuilder2,
    dataPrinter: StringBuilder2, bodyPrinter: StringBuilder2
): StringBuilder2 {
    // close module
    headerPrinter.append("(memory (import \"js\" \"mem\") ").append(sizeInPages).append(")\n")
    headerPrinter.ensureExtra(importPrinter.size + dataPrinter.size + bodyPrinter.size)
    headerPrinter.append(importPrinter)
    headerPrinter.append(dataPrinter)
    headerPrinter.append(bodyPrinter)
    headerPrinter.append(if (comments) ") ;; end of module\n" else ")\n")
    return headerPrinter
}

private fun printStats(printer: StringBuilder2) {
    LOGGER.info(
        "WAT size (${if (comments) "with" else "without"} comments): " +
                printer.length.formatFileSize()
    )
    LOGGER.info("Setter/Getter-Methods: ${hIndex.setterMethods.size}/${hIndex.getterMethods.size}")
    LOGGER.info(
        "Number of constant Strings: ${gIndex.stringSet.size}, " +
                "size: ${gIndex.totalStringSize.formatFileSize()}"
    )
    LOGGER.info("${constructableClasses.size}/${classNamesByIndex.size} classes are constructable")
}

val globals = HashMap<String, GlobalVariable>()
