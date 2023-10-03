import dependency.ActuallyUsedIndex
import dependency.DependencyIndex
import hierarchy.DelayedLambdaUpdate
import hierarchy.DelayedLambdaUpdate.Companion.synthClassName
import hierarchy.FirstClassIndexer
import hierarchy.FirstClassIndexer.Companion.readType
import hierarchy.HierarchyIndex
import jvm.JVM32
import jvm.JVM32.*
import jvm.appendNativeHelperFunctions
import me.anno.io.Streams.readText
import me.anno.maths.Maths.align
import me.anno.maths.Maths.ceilDiv
import me.anno.maths.Maths.hasFlag
import me.anno.utils.files.Files.formatFileSize
import me.anno.utils.structures.Compare.ifSame
import me.anno.utils.structures.lists.Lists.sortedByTopology
import me.anno.utils.types.Booleans.toInt
import me.anno.utils.types.Floats.f3
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.ACC_INTERFACE
import org.objectweb.asm.Opcodes.ASM9
import translator.ClassTranslator
import translator.GeneratorIndex
import translator.GeneratorIndex.dataStart
import translator.GeneratorIndex.stringStart
import utils.*
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.math.sin

const val api = ASM9

// todo test LuaScripts -> magically not working :/

// todo combine non-exported functions with the same content :3

// todo often, there is local.getXX before drop -> remove those

// do we already replace dependencies before resolving them? probably could save us tons of space (and wabt compile time 😁)
// todo mark empty functions as such, and skip them; e.g. new_java_lang_Object_V, new_java_lang_Number_V

// todo loading bar for library loading


// to do find pure (nothrow) functions, and spread them across other methods
// todo implement WASM throwables instead
// todo finally fix huge switch-tables by splitting programs into sections & such
// -> partially done already :)


// we even could compile scene files to WASM for extremely fast load times 😄

var exportAll = true // 15% space-saving; 10% in compressed
var exportHelpers = exportAll

var useWASMExceptions = false

// experimental, not really JVM conform; might work anyway 😄, and be faster or use less memory
var enableTracing = true
var ignoreNonCriticalNullPointers = true
var useUTF8Strings = false // doesn't work with the compiler yet
var replaceStringInternals = true // another way for UTF-8 strings
val byteStrings = useUTF8Strings || replaceStringInternals

var disableAudio = true

// todo doesn't work yet, missing functions :/
var fieldsRWRequired = false

val stackSize = if (enableTracing) 1024 * 1024 else 0

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
    "java/awt/Font" to "jvm/custom/Font2",
    "java/lang/ThreadLocal" to "jvm/custom/ThreadLocal2",
    "java/lang/RuntimePermission" to "jvm/custom/RTPermission",
)

fun rep(clazz: String?) = classReplacements[clazz] ?: clazz
fun reb(clazz: String) = classReplacements[clazz] ?: clazz

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
            sin(0f) < 0f // false
}

fun listEntryPoints(clazz: (String) -> Unit) {
    listEntryPoints(clazz) { clazz(it.clazz) }
}

val cannotThrow = HashSet<String>(256)

fun cannotThrowError(methodSig: MethodSig): Boolean {
    return methodName(methodSig) in cannotThrow
}

fun canThrowError(methodSig: MethodSig): Boolean {
    return !cannotThrowError(methodSig)
}

val cl: ClassLoader = JVM32::class.java.classLoader
val resources = cl.getResourceAsStream("resources.txt")!!.readText()
    .split("\n").map { it.trim() }.filter { !it.startsWith("//") && it.isNotBlank() }
    .map { Pair(it, (cl.getResourceAsStream(it) ?: throw FileNotFoundException("Missing $it")).readBytes()) }

@Suppress("unused_parameter")
fun listEntryPoints(clazz: (String) -> Unit, method: (MethodSig) -> Unit) {

    // constructable classes still don't seem to be fully correct...
    // Non-constructable classes are irrelevant to be resolved (kotlin/collections/CollectionsKt___CollectionsKt)

    // those two unlock 1.7M lines of wasm, why ever...
    // clazz("me/anno/ecs/Component")
    // clazz("me/anno/ecs/Entity")
    // clazz("me/anno/engine/RemsEngine") // our final goal :3
    clazz("engine/Engine") // even better goal ;)

    // needed for Kotlin reflection to work
    // to do check that this works...
    // todo this loaded 40MB extra, which is INSANE!
    // clazz("kotlin/reflect/jvm/internal/ReflectionFactoryImpl")

    // clazz("com/github/junrar/io/ReadOnlyAccessFile")

    // clazz("test/HashMapTest")
    // clazz("test/Animal")
    // clazz("test/Cat")
    // clazz("test/Dog")
    // clazz("test/Fish")
    // clazz("test/SiameseCat")
    // clazz("test/HashCat")

    // method(MethodSig("java/lang/Process", "waitFor", "(JLjava/util/concurrent/TimeUnit;)Z")) // small bug in structural analysis
    // clazz("java/lang/String")
    // clazz("java/lang/Float")
    // method(MethodSig("java/util/regex/Pattern","sequence","(Ljava/util/regex/Pattern\$Node;)Ljava/util/regex/Pattern\$Node;"))
    // method(MethodSig("java/util/HashMap","putMapEntries","(Ljava/util/Map;Z)V"))
    // method(MethodSig("java/util/regex/Pattern","append","(II)V"))
    // clazz("java/util/regex/Pattern") // exception issues
    // clazz("java/lang/Double")
    // clazz("me/anno/gpu/GFX")
    // clazz("javax/vecmath/VecMathUtil") // too old Java
    // method(MethodSig("org/luaj/vm2/Varargs","checkvalue","(I)Lorg/luaj/vm2/LuaValue;")) // stack issues
    // clazz("test/InlineIfTest")
    // method(MethodSig("me/anno/io/text/TextReaderBase","readProperty","(Lme/anno/io/ISaveable;Ljava/lang/String;)Lme/anno/io/ISaveable;"))
    // clazz("test/SwitchTest")

    // this should be the first one, always
    // this causes 1004 classes to be loaded
    // -> because we had duplicates, then it was only 900 😉
    // clazz("java/lang/Object")

    // this is just one extra, as you'd expect
    // clazz("test/TestClass")
    //clazz("test/LambdaTest")
    //clazz("test/EnumTest")
    //clazz("test/CatchTest")
    //clazz("test/BaseTest")
    //clazz("test/InvokeTest")
    //clazz("test/A")
    //clazz("test/B")

    clazz("jvm/JVM32")
    clazz("jvm/GC")
    clazz("jvm/MemDebug")

    // for debugging
    method(MethodSig.c("java/lang/Class", "getName", "()Ljava/lang/String;"))
    method(MethodSig.c("java/lang/Object", "toString", "()Ljava/lang/String;"))
    method(MethodSig.c("java/lang/Thread", "<init>", "()V"))

    clazz("jvm/Boxing")

    // method(MethodSig("java/io/ObjectStreamClass","processQueue","(Ljava/lang/ref/ReferenceQueue;Ljava/util/concurrent/ConcurrentMap;)V"))

    /*clazz("java/lang/System")
    clazz("java/nio/Buffer")
    clazz("java/nio/ByteBuffer")
    clazz("java/nio/CharBuffer")
    clazz("java/util/EnumMap")
    clazz("java/util/Hashtable")*/
    // clazz("java/util/Formatter\$FormatSpecifier")

    // method(MethodSig("java/util/AbstractMap","put","(Ljava/lang/Object;)Ljava/lang/Object;"))
    // clazz("java/lang/CharSequence")
    // clazz("java/lang/Integer")

    // needed for environment
    // clazz("java/lang/NullPointerException")

    // optimizations:
    // done when there is no child implementations for a non-final, non-static function, call it directly
    // todo also track, which methods theoretically can throw errors: mark them, and all their callers; -> optimization
    // call_indirect/interface is more complicated... all methods need to have the same signature

}

fun listLibrary(clazz: (String) -> Unit) {

    clazz("jvm/JVM32")
    clazz("jvm/MemDebug")

    clazz("jvm/JavaAWT")
    clazz("jvm/JavaConcurrent")
    clazz("jvm/JavaLang")
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

fun <V> eq(a: V, b: V) {
    if (a != b) throw IllegalStateException("$a != $b")
}

val entrySig = MethodSig.c("", "entry", "")
val resolvedMethods = HashMap<MethodSig, MethodSig>(4096)
fun main() {

    // ok in Java, trapping in WASM
    // todo has this been fixed?
    // println(Int.MIN_VALUE/(-1)) // -> Int.MIN_VALUE

    val headerPrinter = StringBuilder2(256)
    val importPrinter = StringBuilder2(4096)
    val bodyPrinter = Builder(4096)
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
        hIndex.superClass[predefinedClasses[i]] = predefinedClasses[0]
        hIndex.childClasses.getOrPut(predefinedClasses[0]) { HashSet() }.add(predefinedClasses[i])
    }

    for ((i, clazz) in predefinedClasses.withIndex()) {
        gIndex.classIndex[clazz] = i
        gIndex.classNames.add(clazz)
    }

    val idx = gIndex.getDynMethodIdx(MethodSig.c("java/lang/Object", "<init>", "()V"))
    if (idx != 0) throw IllegalStateException()
    if (gIndex.getType("()V", true) != "\$fRV0") throw IllegalStateException(gIndex.getType("()V", true))

    // prepare String properties
    gIndex.stringClass = gIndex.getClassIndex(reb("java/lang/String"))
    gIndex.stringArrayClass = gIndex.getClassIndex(if (byteStrings) "[B" else "[C")

    eq(gIndex.getFieldOffset("[]", "length", "I", false), objectOverhead)
    eq(gIndex.getFieldOffset(reb("java/lang/String"), "value", "[C", false), objectOverhead)
    eq(gIndex.getFieldOffset(reb("java/lang/String"), "hash", "I", false), objectOverhead + ptrSize)

    hIndex.superClass["java/lang/reflect/Field"] = "java/lang/reflect/AccessibleObject"

    gIndex.getFieldOffset("java/lang/System", "in", "Ljava/io/InputStream;", true)
    gIndex.getFieldOffset("java/lang/System", "out", "Ljava/io/PrintStream;", true)
    gIndex.getFieldOffset("java/lang/System", "err", "Ljava/io/PrintStream;", true)
    eq(gIndex.getFieldOffset("java/lang/Throwable", "detailMessage", "Ljava/lang/String;", false), objectOverhead + 0)
    eq(
        gIndex.getFieldOffset("java/lang/Throwable", "stackTrace", "[Ljava/lang/StackTraceElement;", false),
        objectOverhead + 4
    )

    gIndex.getFieldOffset("java/lang/StackTraceElement", "declaringClass", "Ljava/lang/String;", false)
    gIndex.getFieldOffset("java/lang/StackTraceElement", "methodName", "Ljava/lang/String;", false)
    gIndex.getFieldOffset("java/lang/StackTraceElement", "fileName", "Ljava/lang/String;", false)
    gIndex.getFieldOffset("java/lang/StackTraceElement", "lineNumber", "I", false)

    eq(gIndex.getFieldOffset("java/lang/Class", "name", "Ljava/lang/String", false), objectOverhead + 0)
    eq(gIndex.getFieldOffset("java/lang/Class", "fields", "[Ljava/lang/reflect/Field", false), objectOverhead + 4)
    eq(gIndex.getFieldOffset("java/lang/Class", "methods", "[Ljava/lang/reflect/Method", false), objectOverhead + 8)
    eq(gIndex.getFieldOffset("java/lang/Class", "index", "I", false), objectOverhead + 12)

    gIndex.getFieldOffset("java/lang/reflect/AccessibleObject", "securityCheckCache", "Ljava/lang/Object", false) // 0
    gIndex.getFieldOffset("java/lang/reflect/AccessibleObject", "override", "Z", false) // 4
    gIndex.getFieldOffset("java/lang/reflect/Field", "securityCheckCache", "Ljava/lang/Object", false) // 0
    gIndex.getFieldOffset("java/lang/reflect/Field", "override", "Z", false) // 4
    eq(gIndex.getFieldOffset("java/lang/reflect/Field", "name", "Ljava/lang/String", false), objectOverhead + 5)
    eq(gIndex.getFieldOffset("java/lang/reflect/Field", "slot", "I", false), objectOverhead + 9)
    eq(gIndex.getFieldOffset("java/lang/reflect/Field", "type", "Ljava/lang/Class", false), objectOverhead + 13)
    eq(gIndex.getFieldOffset("java/lang/reflect/Field", "modifiers", "I", false), objectOverhead + 17)
    eq(gIndex.getFieldOffset("java/lang/reflect/Field", "clazz", "Ljava/lang/Class", false), objectOverhead + 21)

    // for sun/misc
    gIndex.getFieldOffset("java/lang/Thread", "threadLocalRandomSeed", "J", false)
    gIndex.getFieldOffset("java/lang/Thread", "threadLocalRandomSecondarySeed", "J", false)
    gIndex.getFieldOffset("java/lang/Thread", "threadLocalRandomProbe", "I", false)

    gIndex.getFieldOffset("java/lang/Class", "enumConstants", "[]", false)

    // reduce number of requests to <clinit> (was using 11% CPU time according to profiler)
    hIndex.finalFields[FieldSig("jvm/JVM32", "objectOverhead", "I", true)] = objectOverhead
    hIndex.finalFields[FieldSig("jvm/JVM32", "arrayOverhead", "I", true)] = arrayOverhead
    hIndex.finalFields[FieldSig("jvm/JVM32", "trackAllocations", "Z", true)] = trackAllocations

    if (gIndex.getInterfaceIndex("", "<clinit>", "()V") != 0) {
        throw IllegalStateException("Modify code to adjust!")
    }

    listEntryPoints { clazz ->
        if (hIndex.doneClasses.add(clazz)) {
            ClassReader(clazz)
                .accept(FirstClassIndexer(hIndex, clazz), 0)
        }
    }

    listLibrary { clazz ->
        if (hIndex.doneClasses.add(clazz)) {
            try {
                ClassReader(clazz)
                    .accept(FirstClassIndexer(hIndex, clazz), 0)
            } catch (e: Exception) {
                throw RuntimeException(clazz, e)
            }
        }
    }

    hIndex.notImplementedMethods.removeAll(hIndex.jvmImplementedMethods)
    hIndex.abstractMethods.removeAll(hIndex.jvmImplementedMethods)
    hIndex.nativeMethods.removeAll(hIndex.jvmImplementedMethods)

    /**
     * generics-mapping-pass
     * */
    for ((clazz, superTypes) in hIndex.genericSuperTypes) {
        val baseMethods = hIndex.methods[clazz] ?: continue
        val ownGenerics = hIndex.generics[clazz]
        for (superType in superTypes) {

            // todo map all its generic methods
            val clearSuperType = single(descWithoutGenerics(superType))
            val generics = hIndex.generics[clearSuperType]

            if (generics == null) {
                println("Missing generics of $clearSuperType by $superType")
                continue
            }

            var i = superType.indexOf('<') + 1
            val params = ArrayList<String>(generics.size)
            for (j in generics.indices) {
                // extract next param from generics
                val k = superType.readType(i)
                params.add(descWithoutGenerics(superType.substring(i, k)))
                i = if (superType[k] == '>') superType.indexOf('<', k + 1) + 1 else k
                if (i == 0) break
            }

            // to do find all abstract methods in superType to be mapped
            // to do all parent classes as well (?)
            val abstractMethods = hIndex.methods[clearSuperType]
                ?.filter { it in hIndex.abstractMethods && it in hIndex.genericMethodSigs }
                ?: continue

            for (method in abstractMethods) {

                // first test, that there is valid candidates?
                // same name, same number of params, not abstract
                val typies = genericsTypies(method) // abstract method cannot be static
                val candidates = baseMethods.filter {
                    it.name == method.name &&
                            it.descriptor != method.descriptor &&
                            it !in hIndex.abstractMethods &&
                            // test for same number of arguments & same-typy return type
                            genericsTypies(it) == typies
                }
                if (candidates.isNotEmpty()) {

                    // remove generics from call (for now)
                    val desc2 = hIndex.genericMethodSigs[method]!!
                    val desc2i = descWithoutGenerics(desc2)
                    // if so, no base-type generics are present -> nothing needs to be replaced or mapped
                    if (method.descriptor == desc2i) continue

                    /*if (!hasPrintedHeader) {
                        println("mapping task [$clazz]: $generics to $superType by $params")
                        hasPrintedHeader = true
                    }

                    println(" - ${method.name}, ${method.descriptor}, $desc2i")
                    println("   candidates: ${candidates.map { it.descriptor }}")
                    if (ownGenerics != null) println("   own generics: $ownGenerics")*/

                    if (params.size != generics.size) {
                        if (candidates.size == 1) {
                            // a guess 😅
                            val sig2 = MethodSig.c(clazz, method.name, method.descriptor)
                            val sig3 = candidates.first()
                            if (sig2 == sig3) throw NotImplementedError()
                            hIndex.methodAliases[methodName(sig2)] = sig3
                            println("Arguments mismatch!, assumed only viable to match")
                        } else println("Arguments mismatch!")
                        continue
                    }

                    val mappedParams = params.mapNotNull { p ->
                        if (p[0] == 'T') {
                            (generics.firstOrNull { it.name == p }
                                ?: ownGenerics?.firstOrNull { it.name == p }
                                    // ?: throw NullPointerException("Didn't find mapping for $p, own generics: $ownGenerics")
                                    )?.descriptor
                        } else p
                    }

                    if (mappedParams.size != params.size) {
                        if (candidates.size == 1) {
                            // map candidate
                            println("Using only candidate, bc didn't find all mappings for $clazz, $method")
                            val sig2 = MethodSig.c(clazz, method.name, method.descriptor)
                            val sig3 = candidates.first()
                            if (sig2 == sig3) throw NotImplementedError()
                            hIndex.methodAliases[methodName(sig2)] = sig3
                        } else {
                            println("Didn't find all mappings for $clazz, $method!")
                        }
                        continue
                    }

                    val desc2j = split2(desc2i, true)
                    val mappedDesc = desc2j.map { p ->
                        if (p[0] == 'T') {
                            mappedParams[generics.indexOfFirst { it.name == p }]
                        } else p
                    }

                    // generate a new signature
                    val newDescBuilder = Builder(mappedDesc.sumOf { it.length } + 2)
                    newDescBuilder.append('(')
                    for (j in 0 until mappedDesc.size - 1) {
                        newDescBuilder.append(mappedDesc[j])
                    }
                    newDescBuilder.append(')')
                    newDescBuilder.append(mappedDesc.last())
                    val newDesc = newDescBuilder.toString()

                    // check if this newly generated signature is implemented
                    val implMethod = candidates.firstOrNull { it.descriptor == newDesc }
                    if (implMethod != null) {
                        // define mapping
                        val sig2 = MethodSig.c(clazz, method.name, method.descriptor)
                        if (sig2 == implMethod) throw NotImplementedError()
                        hIndex.methodAliases[methodName(sig2)] = implMethod
                    } else println("warn! no mapping found for [$clazz]: $generics to $superType by $params, $candidates")

                }
            }
        }
    }

    /**
     * find NoThrow annotations
     * */
    for ((sig, annotations) in hIndex.annotations) {
        if (annotations.any { it.clazz == "annotations/NoThrow" }) {
            cannotThrow.add(methodName(sig))
        }
    }

    // java_lang_StrictMath_sqrt_DD
    hIndex.inlined[MethodSig.c("java/lang/StrictMath", "sqrt", "(D)D")] = "f64.sqrt"
    hIndex.inlined[MethodSig.c("java/lang/Math", "sqrt", "(D)D")] = "f64.sqrt"

    /**
     * find aliases
     * */
    val nameToMethod0 = nameToMethod
    for ((sig, annotations) in hIndex.annotations) {
        val alias = annotations.firstOrNull { it.clazz == "annotations/Alias" }
        if (alias != null) {
            @Suppress("UNCHECKED_CAST")
            val aliasNames = alias.properties["names"] as List<String>
            for (aliasName in aliasNames) {
                if('|' in aliasName) throw IllegalStateException(aliasName)
                // val aliasName = alias.properties["name"] as String
                if (aliasName.startsWith('$')) throw IllegalStateException("alias $aliasName must not start with dollar symbol")
                if (aliasName.contains('/')) throw IllegalStateException("alias $aliasName must not contain slashes, but underscores")
                if (aliasName.contains('(')) throw IllegalStateException("alias $aliasName must not contain slashes, but underscores")
                val previous = hIndex.methodAliases[aliasName]
                if (previous != null && previous != sig) throw IllegalStateException("Cannot replace $aliasName -> $previous with -> $sig")
                hIndex.methodAliases[aliasName] = sig
            }
        }
        val revAlias = annotations.firstOrNull { it.clazz == "annotations/RevAlias" }
        if (revAlias != null) {
            val aliasName = revAlias.properties["name"] as String
            if (aliasName.startsWith('$')) throw IllegalStateException("alias $aliasName must not start with dollar symbol")
            if (aliasName.contains('/')) throw IllegalStateException("alias $aliasName must not contain slashes, but underscores")
            if (aliasName.contains('(')) throw IllegalStateException("alias $aliasName must not contain slashes, but underscores")
            val sig1 = nameToMethod0[aliasName]
            if (sig1 != null) hIndex.methodAliases[methodName(sig)] = sig1
            else println("Skipped $sig -> $aliasName, because unknown")
        }
    }

    if ("org_lwjgl_opengl_GL45C_glEnable_IV" !in hIndex.methodAliases)
        throw IllegalStateException()

    /**
     * collect all entry point methods
     * */
    val entryPoints = HashSet<MethodSig>()
    val entryClasses = HashSet<String>()
    fun entryClass(clazz: String) {
        entryClasses.add(clazz)
        val superClass = hIndex.superClass[clazz]
        if (superClass != null) entryClass(superClass)
        val interfaces = hIndex.interfaces[clazz]
        if (interfaces != null) for (interfaceI in interfaces)
            entryClass(interfaceI)
    }
    listEntryPoints({ clazz ->
        // all implemented methods
        entryPoints.addAll(hIndex.methods[clazz] ?: emptySet())
        entryClass(clazz)
    }) {
        if (it.name == "<init>")
            entryClass(it.clazz)
        entryPoints.add(it)
    }

    listEntryPoints(
        { hIndex.exportedMethods.addAll(hIndex.methods[it] ?: emptySet()) },
        { hIndex.exportedMethods.add(it) })

    for ((sig, a) in hIndex.annotations) {
        if (a.any { it.clazz == "annotations/Export" }) {
            hIndex.exportedMethods.add(sig)
        }
    }

    // unknown (because idk where [] is implemented), can cause confusion for my compiler
    hIndex.finalMethods.add(MethodSig.c("[]", "clone", "()Ljava/lang/Object;"))

    // replace dependencies to get rid of things
    val methodNameToSig = HashMap<String, MethodSig>()
    for (sig in hIndex.methods.map { it.value }.flatten()) {
        methodNameToSig[methodName2(sig.clazz, sig.name, sig.descriptor)] = sig
    }
    for ((src, dst) in hIndex.methodAliases) {
        val src1 = methodNameToSig[src] ?: continue
        dIndex.methodDependencies[src1] = dIndex.methodDependencies[dst] ?: HashSet()
        dIndex.fieldDependenciesR[src1] = dIndex.fieldDependenciesR[dst] ?: HashSet()
        dIndex.fieldDependenciesW[src1] = dIndex.fieldDependenciesW[dst] ?: HashSet()
        dIndex.constructorDependencies[src1] = dIndex.constructorDependencies[dst] ?: HashSet()
        dIndex.interfaceDependencies[src1] = dIndex.interfaceDependencies[dst] ?: HashSet()
    }

    // resolve a lot of methods for better dependencies
    dIndex.methodDependencies = HashMap(
        dIndex.methodDependencies
            .mapValues { (_, deps) ->
                deps.map {
                    resolvedMethods.getOrPut(it) {
                        val found = findMethod(it.clazz, it.name, it.descriptor, false) ?: it
                        if (found != it) hIndex.methodAliases[methodName(it)] = found
                        if ((found in hIndex.staticMethods) == (it in hIndex.staticMethods)) found else it
                    }
                }.toHashSet()
            }
    )

    for (clazz in hIndex.methods.keys) {
        val superClass = hIndex.superClass[clazz] ?: continue
        if (clazz !in (hIndex.childClasses[superClass] ?: emptySet()))
            throw IllegalStateException("Missing $clazz in $superClass")
    }

    println("// starting resolve")
    val t0 = System.nanoTime()
    dIndex.resolve(entryClasses, entryPoints, ::cannotUseClass)
    val t1 = System.nanoTime()
    println("// finished resolve in ${((t1 - t0) * 1e-9).f3()}s")

    // build synthetic methods
    for ((name, dlu) in DelayedLambdaUpdate.needingBridgeUpdate) {
        if (name in dIndex.constructableClasses)
            dlu.indexFields()
    }

    val testSig = MethodSig.c("java/lang/StackTraceElement", "equals", "(Ljava/lang/Object;)Z")
    if (testSig !in dIndex.usedMethods) {
        printUsed(testSig)
        throw IllegalStateException()
    }

    /**
     * calculate the field offsets right here :)
     * */
    val usedFields = HashSet(dIndex.usedFieldsR)
    if (fieldsRWRequired) {
        usedFields.retainAll(dIndex.usedFieldsW)
    } else {
        usedFields.addAll(dIndex.usedFieldsW)
    }
    val nativeClasses = listOf("I", "F", "B", "S", "Z", "J", "C", "D")
    val fieldsByClass = usedFields.groupBy { it.clazz }
    for (clazz in fieldsByClass.keys.sortedByTopology {
        listSuperClasses(it, HashSet())
    }) {
        for (field in fieldsByClass[clazz]!!
            .sortedWith { a, b ->
                storageSize(b.descriptor).compareTo(storageSize(a.descriptor))
                    .ifSame { (a.clazz in nativeClasses).toInt().compareTo((b.clazz in nativeClasses).toInt()) }
                    .ifSame { a.name.compareTo(b.name) }
            }) { // sort by size && is-ptr | later we even could respect alignment and fill gaps where possible :)
            gIndex.getFieldOffset(field.clazz, field.name, field.descriptor, field.static)
        }
    }
    gIndex.lockFields = true

    if ((gIndex.getFieldOffset("java/lang/System", "out", "Ljava/lang/Object", true) != null) !=
        (MethodSig.c("java/lang/System", "<clinit>", "()V") in dIndex.usedMethods)
    ) {
        println(gIndex.getFieldOffsets("java/lang/System", true))
        printUsed(MethodSig.c("java/lang/System", "<clinit>", "()V"))
        val fs = FieldSig("java/lang/System", "out", "Ljava/lang/Object", true)
        println(fs in dIndex.usedFieldsR)
        println(fs in dIndex.usedFieldsW)
        println(fs in usedFields)
        throw IllegalStateException()
    }

    gIndex.getFieldOffset("java/lang/reflect/Constructor", "clazz", "Ljava/lang/Class", false)

    for ((method, annotation, noinline) in hIndex.annotations.entries
        .mapNotNull { m ->
            val wasm = m.value.firstOrNull { it.clazz == "annotations/WASM" }
            if (wasm != null)
                Triple(m.key, wasm.properties["code"] as String,
                    m.value.any { it.clazz == "annotations/NoInline" })
            else null
        }) {
        if (noinline) {
            hIndex.wasmNative[method] = annotation
        } else {
            hIndex.inlined[method] = annotation
        }
    }

    headerPrinter.append("(module\n")

    importPrinter.import1("fcmpl", listOf(f32, f32), listOf(i32))
    importPrinter.import1("fcmpg", listOf(f32, f32), listOf(i32))
    importPrinter.import1("dcmpl", listOf(f64, f64), listOf(i32))
    importPrinter.import1("dcmpg", listOf(f64, f64), listOf(i32))

    // this should be empty, and replaced using functions with JavaScript-pseudo implementations
    val missingMethods = HashSet<MethodSig>(4096)
    importPrinter.append(";; forbidden\n")
    loop@ for (sig in dIndex.methodsWithForbiddenDependencies
        .filter { it in dIndex.usedMethods }
        .sortedBy { methodName(it) }) {
        val name = methodName(sig)
        var sig2: MethodSig
        while (true) {
            sig2 = hIndex.methodAliases[name] ?: sig
            if (sig2 != sig) {
                if (sig2 !in dIndex.usedMethods) {
                    throw NotImplementedError("$sig was used, but $sig2 wasn't")
                }
                continue@loop
            } else break
        }
        importPrinter.import2(sig)
        if (!missingMethods.add(sig))
            throw IllegalStateException()
    }

    // only now usedMethods is complete
    printMethodFieldStats()

    importPrinter.append(";; not implemented, not forbidden\n")
    for (sig in dIndex.usedMethods
        .filter {
            it in hIndex.hasSuperMaybeMethods &&
                    it !in dIndex.methodsWithForbiddenDependencies &&
                    it !in hIndex.jvmImplementedMethods
        }
        .sortedBy { methodName(it) }) {
        val superMethod = dIndex.findSuperMethod(sig.clazz, sig)
        if (superMethod != null) {
            if (sig != superMethod)
                hIndex.methodAliases[methodName(sig)] = superMethod
        } else {
            if (hIndex.classFlags[sig.clazz]?.hasFlag(ACC_INTERFACE) == true) {
                println("Skipping $sig")
                continue
            }
            importPrinter.import2(sig)
            if (!missingMethods.add(sig))
                throw IllegalStateException()
        }
    }

    for (sig in dIndex.usedMethods
        .filter { it in hIndex.nativeMethods }) {
        val annot = hIndex.annotations[sig] ?: continue
        if (annot.any { it.clazz == "annotations/JavaScript" || it.clazz == "annotations/WASM" }) {
            hIndex.notImplementedMethods.remove(sig)
        }
    }

    importPrinter.append(";; not implemented, native\n")
    for (func in dIndex.usedMethods
        .filter {
            it in hIndex.nativeMethods &&
                    it !in missingMethods &&
                    it !in hIndex.jvmImplementedMethods
        }
        .sortedBy { methodName(it) }) {
        importPrinter.import2(func)
        missingMethods.add(func)
    }

    // generate JavaScript file
    val jsImplemented = HashMap<String, Pair<MethodSig, String>>() // name -> sig, impl
    for (sig in dIndex.usedMethods) {
        val annot = hIndex.annotations[sig] ?: continue
        val js = annot.firstOrNull { it.clazz == "annotations/JavaScript" } ?: continue
        val code = js.properties["code"] as String
        missingMethods.remove(sig)
        for (name in methodNames(sig)) {
            jsImplemented[name] = Pair(sig, code)
            implementedMethods[name] = sig
        }
    }

    // create a pseudo-implementation for all imported methods without implementation
    val jsPseudoImplemented = HashMap<String, MethodSig>()
    for (sig in missingMethods) {
        if ((hIndex.annotations[sig] ?: emptyList()).any { it.clazz == "annotations/WASM" }) continue
        val name = methodName(sig)
        if (name in jsImplemented) continue
        jsPseudoImplemented[name] = sig
        implementedMethods[name] = sig
        // printer.import2(sig)
    }

    bodyPrinter.append(";; not implemented, abstract\n")
    for (func in dIndex.usedMethods
        .filter {
            it in hIndex.abstractMethods &&
                    it !in dIndex.methodsWithForbiddenDependencies &&
                    !(hIndex.classFlags[it.clazz] ?: 0).hasFlag(ACC_INTERFACE) &&
                    it !in missingMethods
        }
        .sortedBy { methodName(it) }
    ) {
        val canThrow = canThrowError(func)
        val desc = func.descriptor
        val dx = desc.lastIndexOf(')')
        // export? no, nobody should call these
        if(methodName(func) == "me_anno_io_ISaveableXCompanionXregisterCustomClassX2_invoke_Lme_anno_io_ISaveable")
            throw IllegalStateException("This function isn't not-implemented/abstract")
        bodyPrinter.append("(func $").append(methodName(func)).append(" (param")
        for (param in split1(desc.substring(1, dx))) {
            bodyPrinter.append(' ').append(jvm2wasm(param))
        }
        bodyPrinter.append(") (result")
        val hasReturnType = !desc.endsWith(")V")
        if (hasReturnType) bodyPrinter.append(' ').append(jvm2wasm1(desc[dx + 1]))
        if (canThrow) bodyPrinter.append(' ').append(ptrType)
        bodyPrinter.append(")")
        if (hasReturnType || canThrow) {
            if (hasReturnType) bodyPrinter.append(" ").append(jvm2wasm1(desc[dx + 1])).append(".const 0")
            if (canThrow) bodyPrinter.append(" call \$throwAME")
        }
        bodyPrinter.append(")\n")
    }

    for (sig in dIndex.usedMethods) {
        if (sig in hIndex.jvmImplementedMethods) {
            implementedMethods[methodName(sig)] = sig
        }
    }

    // 4811/15181 -> 3833/15181, 1906 unique
    findUniquelyImplemented(
        dIndex.usedMethods.filter { methodName(it) !in hIndex.methodAliases },
        implementedMethods.values.toSet()
    )

    /** translate method implementations */
    // find all aliases, that are being used
    val aliasedMethods = dIndex.usedMethods.mapNotNull {
        hIndex.methodAliases[methodName(it)]
    }

    fun filterClass(clazz: String): Boolean {
        return !clazz.startsWith("[")
    }

    val classesToLoad = (dIndex.usedMethods + aliasedMethods)
        .map { it.clazz }
        .filter { clazz ->
            clazz != "?" &&
                    clazz !in hIndex.syntheticClasses &&
                    clazz !in hIndex.missingClasses
        }
        .toSet() // filter duplicates
        // super classes and interfaces first
        .sortedByTopology {
            listSuperClasses(it, HashSet())
        }

    /**
     * create the dynamic index for the gIndex methods
     * */
    val dynIndex = HashMap<String, HashSet<MethodSig>>()
    for (clazz in classesToLoad) {
        if (filterClass(clazz)) try {
            ClassReader(clazz).accept(
                object : ClassVisitor(api) {
                    override fun visitMethod(
                        access: Int,
                        name: String,
                        descriptor: String,
                        signature: String?,
                        exceptions: Array<out String>?
                    ): MethodVisitor? {
                        val sig1 = MethodSig.c(clazz, name, descriptor)
                        val map = hIndex.methodAliases[methodName(sig1)]
                        return if (sig1 !in dIndex.methodsWithForbiddenDependencies && sig1 in dIndex.usedMethods &&
                            (map == null || map == sig1)
                        ) object : MethodVisitor(api) {

                            override fun visitInvokeDynamicInsn(
                                name: String?,
                                descriptor: String?,
                                bootstrapMethodHandle: Handle?,
                                vararg args: Any?
                            ) {
                                val dst = args[1] as Handle
                                val synthClassName = synthClassName(sig1, dst)
                                // println("lambda: $sig1 -> $synthClassName")
                                gIndex.getClassIndex(synthClassName) // add class to index
                                val calledMethod = MethodSig.c(dst.owner, dst.name, dst.desc)
                                dynIndex.getOrPut(calledMethod.clazz) { HashSet() }.add(calledMethod)
                            }

                            override fun visitTypeInsn(opcode: Int, type: String) {
                                gIndex.getClassIndex(reb(type)) // add class to index
                            }

                            override fun visitTryCatchBlock(start: Label, end: Label, handler: Label, type: String?) {
                                if (type != null) {
                                    gIndex.getClassIndex(reb(type)) // add class to index
                                }
                            }

                            override fun visitLdcInsn(value: Any?) {
                                if (value is Type) { // add class to index
                                    gIndex.getClassIndex(reb(single(value.descriptor)))
                                }
                            }

                            override fun visitMethodInsn(
                                opcode: Int,
                                owner0: String,
                                name: String,
                                descriptor: String,
                                isInterface: Boolean
                            ) {
                                if (opcode == 0xb6) { // invoke virtual
                                    val owner = reb(owner0)
                                    val sig0 = MethodSig.c(owner, name, descriptor)
                                    // just for checking if abstract
                                    // val sig2 = hIndex.methodAliases[utils.methodName(sig0)] ?: sig0
                                    if (sig0 !in hIndex.finalMethods) {
                                        // check if method is defined in parent class
                                        fun add(clazz: String) {
                                            val superClass = hIndex.superClass[clazz]
                                            if (superClass != null &&
                                                MethodSig.c(
                                                    superClass,
                                                    name, descriptor
                                                ) in hIndex.methods[superClass]!!
                                            ) {
                                                add(superClass)
                                            } else {
                                                dynIndex.getOrPut(clazz) { HashSet() }.add(sig0)
                                            }
                                        }
                                        add(owner)
                                    }
                                }
                            }
                        } else null
                    }
                }, 0 // no stack-frames are needed
            )
        } catch (e: IOException) {
            throw IOException(clazz, e)
        }
    }

    // going from parent classes to children, index all methods in gIndex
    for (clazz in classesToLoad) {
        gIndex.getDynMethodIdx(clazz)
        val idx1 = dynIndex[clazz] ?: continue
        for (sig in idx1) {
            gIndex.getDynMethodIdx(clazz, sig.name, sig.descriptor)
        }
    }

    for (clazz in predefinedClasses) {
        if (!(hIndex.classFlags[clazz] ?: 0).hasFlag(ACC_INTERFACE))
            gIndex.getDynMethodIdx(clazz)
    }

    gIndex.lockedDynIndex = true

    for (clazz in dIndex.constructableClasses) {
        val interfaces = hIndex.interfaces[clazz] ?: continue
        for (interfaceI in interfaces) {
            gIndex.getClassIndex(interfaceI)
        }
    }

    // ensure all interfaces and super classes have their dedicated index
    var i = 0
    while (i < gIndex.classNames.size) {
        val clazzName = gIndex.classNames[i++]
        val superClass = hIndex.superClass[clazzName]
        if (superClass != null) gIndex.getClassIndex(superClass)
        val interfaces = hIndex.interfaces[clazzName]
        if (interfaces != null) for (interfaceI in interfaces) {
            gIndex.getClassIndex(interfaceI)
        }
    }

    /**
     * calculate static layout and then set string start
     * */
    var ptr = dataStart
    val numClasses = gIndex.classIndex.size
    gIndex.lockClasses = true
    ptr = appendStaticInstanceTable(dataPrinter, ptr, numClasses)
    stringStart = ptr

    /**
     * translate all required methods
     * */
    for (clazz in classesToLoad) {
        if (filterClass(clazz)) try {
            ClassReader(clazz).accept(
                ClassTranslator(clazz),
                ClassReader.EXPAND_FRAMES
            )
        } catch (e: ClassTranslator.FoundBetterReader) {
            try {
                e.reader.accept(
                    ClassTranslator(clazz),
                    ClassReader.EXPAND_FRAMES
                )
            } catch (e: IOException) {
                throw IOException(clazz, e)
            }
        } catch (e: IOException) {
            throw IOException(clazz, e)
        }
    }

    // build synthetic methods
    for ((name, dlu) in DelayedLambdaUpdate.needingBridgeUpdate) {
        if (name in dIndex.constructableClasses)
            dlu.generateSyntheticMethod()
    }

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

    for (desc in gIndex.nthMethods.values
        .map { it.descriptor }.toSortedSet()) {
        bodyPrinter.append(desc)
    }

    listEntryPoints({
        for (sig in hIndex.methods[it]!!) {
            gIndex.actuallyUsed.add(entrySig, methodName(sig))
        }
    }, { sig ->
        gIndex.actuallyUsed.add(entrySig, methodName(sig))
    })

    bodyPrinter.ensureExtra(gIndex.translatedMethods.values.sumOf { it.length })
    val resolved = ActuallyUsedIndex.resolve()

    usedButNotImplemented.retainAll(resolved)
    if (usedButNotImplemented.isNotEmpty()) {
        println("\nMissing functions:\n")
        val nameToMethod = nameToMethod
        for (name in usedButNotImplemented) {
            println(name)
            println(name in resolved)
            val sig = hIndex.methodAliases[name] ?: nameToMethod[name]
            if (sig != null) {
                println(hIndex.methodAliases[name])
                println(nameToMethod[name])
                println(sig in gIndex.translatedMethods)
                printUsed(sig)
            }
        }
        throw IllegalStateException("Missing functions")
    }

    for ((sig, impl) in gIndex.translatedMethods.entries.sortedBy { it.value }) {
        val name = methodName(sig)
        // not truly used, even tho marked as such...
        if (name in resolved) {
            bodyPrinter.append(impl)
        } else if (!name.startsWith("new_") && !name.startsWith("static_") &&
            sig !in hIndex.getterMethods && sig !in hIndex.setterMethods
        ) {
            println("Not actually used: $name")
        }// else we don't care we didn't use it
    }

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

    println("total size (with comments): ${headerPrinter.length.toLong().formatFileSize(1024)}")

    /*for (it in gIndex.interfaceIndex.entries.sortedBy { it.value }) {
        println("${it.value}: ${it.key}")
    }*/

    println("setter/getter: ${hIndex.setterMethods.size}/${hIndex.getterMethods.size}")

    compileToWASM(headerPrinter)

    println("${dIndex.constructableClasses.size}/${gIndex.classNames.size} classes are constructable")

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
