import dependency.ActuallyUsedIndex
import dependency.DependencyIndex
import hierarchy.DelayedLambdaUpdate
import hierarchy.HierarchyIndex
import jvm.JVM32
import me.anno.io.Streams.readText
import me.anno.maths.Maths.align
import me.anno.maths.Maths.ceilDiv
import me.anno.utils.assertions.assertTrue
import me.anno.utils.files.Files.formatFileSize
import me.anno.utils.structures.lists.Lists.any2
import org.objectweb.asm.Opcodes.ASM9
import translator.GeneratorIndex
import translator.GeneratorIndex.dataStart
import translator.GeneratorIndex.stringStart
import utils.*
import wasm.instr.Instructions.F64_SQRT
import java.io.FileNotFoundException
import kotlin.math.sin

const val api = ASM9

// optimizations:
// done when there is no child implementations for a non-final, non-static function, call it directly
// todo also track, which methods theoretically can throw errors: mark them, and all their callers; -> optimization
// call_indirect/interface is more complicated... all methods need to have the same signature
// todo if there is only a small number of implementations, make resolveDirect() use a if-else-chain instead (-> modify findUniquelyImplemented())

// todo on each branch, mark which classes have been static-initialized,
//  and remove those on all necessarily following branches


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

var exportAll = false // 15% space-saving; 10% in compressed
var exportHelpers = exportAll

// todo this needs catch-blocks, somehow..., and we get a lot of type-mismatch errors at the moment
var useWASMExceptions = false

// experimental, not really JVM conform; might work anyway 😄, and be faster or use less memory
var enableTracing = true
var ignoreNonCriticalNullPointers = true
var checkArrayAccess = false
var checkNullPointers = false
var checkClassCasts = false
var checkIntDivisions = false

var useUTF8Strings = false // doesn't work with the compiler yet
var replaceStringInternals = true // another way for UTF-8 strings

var crashOnAllExceptions = false // todo not yet supported

var crashInStatic = false // crashes at runtime :/
val byteStrings = useUTF8Strings || replaceStringInternals

var disableAudio = true

var addDebugMethods = false

// todo doesn't work yet, missing functions :/
// if this flag is true, fields that aren't read won't be written
var fieldsRWRequired = false

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


fun replaceClass0(clazz: String?) = classReplacements[clazz] ?: clazz
fun replaceClass1(clazz: String) = classReplacements[clazz] ?: clazz

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
            clazz.startsWith("java/io/ObjectStream") ||
            sin(0f) < 0f // false
}

fun listEntryPoints(clazz: (String) -> Unit) {
    listEntryPoints(clazz) { clazz(it.clazz) }
}

val cannotThrow = HashSet<String>(256)

fun canThrowError(methodSig: MethodSig): Boolean {
    if (crashOnAllExceptions) return false
    if (crashInStatic && methodSig.name == "<clinit>") return false
    return methodName(methodSig) !in cannotThrow
}

val cl: ClassLoader = JVM32::class.java.classLoader
val resources = cl.getResourceAsStream("resources.txt")!!.readText()
    .split("\n").map { it.trim() }.filter { !it.startsWith("//") && it.isNotBlank() }
    .map { Pair(it, (cl.getResourceAsStream(it) ?: throw FileNotFoundException("Missing $it")).readBytes()) }

fun listEntryPoints(clazz: (String) -> Unit, method: (MethodSig) -> Unit) {

    clazz("engine/Engine")

    clazz("jvm/JVM32")
    clazz("jvm/GC")
    clazz("jvm/MemDebug")

    if (checkArrayAccess) {
        clazz("jvm/ArrayAccessSafe")
    } else {
        clazz("jvm/ArrayAccessUnchecked")
    }

    // for debugging
    if (addDebugMethods) {
        method(MethodSig.c("java/lang/Class", "getName", "()Ljava/lang/String;"))
        method(MethodSig.c("java/lang/Object", "toString", "()Ljava/lang/String;"))
        method(MethodSig.c("java/lang/Thread", "<init>", "()V"))
    }

    clazz("jvm/Boxing")

}

fun listLibrary(clazz: (String) -> Unit) {

    clazz("jvm/JVM32")
    clazz("jvm/MemDebug")

    clazz("jvm/JavaAWT")
    clazz("jvm/JavaConcurrent")
    clazz("jvm/JavaLang")
    clazz("jvm/JavaReflect")
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

}

fun printMethodFieldStats() {
    val usedFields = dIndex.usedFieldsR.filter { it in dIndex.usedFieldsW }
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

val entrySig = MethodSig.c("", "entry", "()V")
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
        "int", "float", "boolean", "byte",
        "short", "char", "long", "double", // #24
    )

    for (i in 1 until 13) {
        hIndex.registerSuperClass(predefinedClasses[i], predefinedClasses[0])
    }

    for (i in predefinedClasses.indices) {
        val clazz = predefinedClasses[i]
        gIndex.classIndex[clazz] = i
        gIndex.classNames.add(clazz)
    }

    registerDefaultOffsets()
    listEntryPoints()

    hIndex.notImplementedMethods.removeAll(hIndex.jvmImplementedMethods)
    hIndex.abstractMethods.removeAll(hIndex.jvmImplementedMethods)
    hIndex.nativeMethods.removeAll(hIndex.jvmImplementedMethods)

    resolveGenericTypes()
    findNoThrowMethods()

    // java_lang_StrictMath_sqrt_DD
    hIndex.inlined[MethodSig.c("java/lang/StrictMath", "sqrt", "(D)D")] = listOf(F64_SQRT)
    hIndex.inlined[MethodSig.c("java/lang/Math", "sqrt", "(D)D")] = listOf(F64_SQRT)

    findAliases()

    val (entryPoints, entryClasses) = collectEntryPoints()

    findExportedMethods()

    // unknown (because idk where [] is implemented), can cause confusion for my compiler
    hIndex.finalMethods.add(MethodSig.c("[]", "clone", "()Ljava/lang/Object;"))

    replaceRenamedDependencies()
    checkMissingClasses()
    resolveAll(entryClasses, entryPoints)
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

    for (sig in dIndex.usedMethods
        .filter { it in hIndex.nativeMethods }) {
        val annotations = hIndex.annotations[sig] ?: continue
        if (annotations.any2 { it.clazz == "annotations/JavaScript" || it.clazz == "annotations/WASM" }) {
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
        return !clazz.startsWith("[")
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

    translateMethods(classesToLoad, ::filterClass)
    buildSyntheticMethods()

    ptr = appendStringData(dataPrinter, gIndex) // idx -> string

    val classTableStart = ptr

    // class -> super, instance size, interfaces, interface functions
    ptr = appendInheritanceTable(dataPrinter, ptr, numClasses)
    // class -> function[] for resolveIndirect
    ptr = appendInvokeDynamicTable(dataPrinter, ptr, numClasses)
    // java.lang.Class, with name, fields
    ptr = appendClassInstanceTable(dataPrinter, ptr, numClasses)
    // idx -> class, line for stack trace
    ptr = appendThrowableLookup(dataPrinter, ptr)
    // length, [String, byte[]]x resources
    ptr = appendResourceTable(dataPrinter, ptr)

    // must come after invoke dynamic
    appendDynamicFunctionTable(dataPrinter, implementedMethods) // idx -> function
    appendFunctionTypes(dataPrinter)
    appendNativeHelperFunctions(dataPrinter)

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
    for (desc in gIndex.nthGetterMethods.map { it.value }) {
        bodyPrinter.append(desc)
    }

    listEntryPoints({
        for (sig in hIndex.methods[it]!!) {
            ActuallyUsedIndex.add(entrySig, sig)
        }
    }, { sig ->
        ActuallyUsedIndex.add(entrySig, sig)
    })

    val usedMethods = ActuallyUsedIndex.resolve()

    val isUsed = MethodSig.c(
        "kotlin/jvm/internal/PropertyReference1", "invoke",
        "(Ljava_lang_Object;)Ljava_lang_Object;"
    )

    val isMaybeUsed = MethodSig.c(
        "kotlin/jvm/internal/PropertyReference1", "get",
        "(Ljava_lang_Object;)Ljava_lang_Object;"
    )

    val parentClazz = "kotlin/jvm/internal/PropertyReference1Impl"
    val clazzMissing = "me/anno/ecs/systems/Systems\$registerSystem\$1\$1"
    println("  SuperClass: ${hIndex.superClass[clazzMissing]}, Interfaces: ${hIndex.interfaces[clazzMissing]}")
    val parentMissing = MethodSig.c(
        parentClazz, "get",
        "(Ljava_lang_Object;)Ljava_lang_Object;"
    )
    val isMissing = MethodSig.c(
        clazzMissing, "get",
        "(Ljava_lang_Object;)Ljava_lang_Object;"
    )

    printUsed(isUsed)
    printUsed(isMaybeUsed)
    printUsed(isMissing)
    printUsed(parentMissing)

    assertTrue(methodName(isUsed) in usedMethods)
    assertTrue(methodName(isMissing) in usedMethods)

    val isUsed2 = MethodSig.c("java/io/InputStreamReader", "close", "()V")
    printUsed(isUsed2)
    assertTrue(methodName(isUsed2) in usedMethods)

    usedButNotImplemented.retainAll(usedMethods)

    val nameToMethod = nameToMethod
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

    printMethodImplementations(bodyPrinter, usedMethods)

    printInterfaceIndex()

    fun global(name: String, type: String, type2: String, value: Int) {
        // can be mutable...
        dataPrinter.append("(global $").append(name).append(" ").append(type).append(" (").append(type2)
            .append(".const ").append(value).append("))\n")
    }

    fun global(name: String, type: String, value: Int) {
        global(name, type, type, value)
    }

    dataPrinter.append(";; globals:\n")
    global("S", ptrType, stringStart) // string table
    global("c", ptrType, classTableStart) // class table
    global("s", ptrType, staticTablePtr) // static table
    global("M", ptrType, methodTablePtr)
    global("X", ptrType, numClasses)
    global("Y", ptrType, classInstanceTablePtr)
    global("YS", ptrType, gIndex.getFieldOffsets("java/lang/Class", false).offset)
    global("Z", ptrType, clInitFlagTable)
    global("L", ptrType, throwableLookupPtr)
    global("R", ptrType, resourceTablePtr)

    // todo if we run out of stack space, throw exception

    global("q", ptrType, ptr) // stack end ptr
    ptr += stackSize
    global("Q", "(mut i32)", ptrType, ptr) // stack ptr
    global("Q0", ptrType, ptr) // stack ptr start address

    ptr = align(ptr + 4, 16)
    // allocation start address
    global("G", "(mut i32)", ptrType, ptr)
    global("G0", ptrType, ptr) // original allocation start address

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

    /*for (it in gIndex.interfaceIndex.entries.sortedBy { it.value }) {
        println("${it.value}: ${it.key}")
    }*/

    println("  Setter/Getter-Methods: ${hIndex.setterMethods.size}/${hIndex.getterMethods.size}")

    compileToWASM(headerPrinter)

    println("  ${dIndex.constructableClasses.size}/${gIndex.classNames.size} classes are constructable")

    /*for ((name, size) in gIndex.classNames
        .map { it to gIndex.getFieldOffsets(it, false).offset }
        .sortedByDescending { it.second }
        .subList(0, min(gIndex.classNames.size, 100))) {
        println(
            "$name: $size, ${
                gIndex.getFieldOffsets(name, false).fields.entries.sortedBy { it.value.offset }.map { it.key }
            }"
        )
    }*/

}

fun printMissingFunctions(usedButNotImplemented: Set<String>, resolved: Set<String>) {
    println("\nMissing functions:")
    val nameToMethod = nameToMethod
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
    throw IllegalStateException("Missing ${usedButNotImplemented.size} functions")
}
