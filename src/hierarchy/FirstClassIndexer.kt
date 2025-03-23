package hierarchy

import api
import hIndex
import hierarchy.HierarchyIndex.methodFlags
import me.anno.utils.types.Booleans.hasFlag
import org.apache.logging.log4j.LogManager
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import replaceClass
import replaceClassNullable
import utils.Descriptor
import utils.FieldSig
import utils.MethodSig
import utils.STATIC_INIT
import java.io.IOException

/**
 * find super classes, interfaces and annotations for all types
 * */
class FirstClassIndexer(val clazz: String) : ClassVisitor(api) {

    init {
        if (replaceClass(clazz) != clazz) throw IllegalStateException("Forgot to resolve $clazz")
    }

    val index get() = hIndex

    companion object {

        private val LOGGER = LogManager.getLogger(FirstClassIndexer::class)

        private fun String.readType0(i: Int): Int {
            val j = indexOf(';', i)
            val k = indexOf('<', i)
            if (k !in i until j) return j + 1 // no generics -> easy :)
            // wait until we are at the end of it -> numOf(<) == numOf(>)
            var idx = k + 1
            var count = 1
            while (count > 0) {
                when (this[idx]) {
                    '<' -> count++
                    '>' -> count--
                }
                idx++
            }
            return idx + 1 // go to ; instead of >
        }

        private fun String.readType1(i: Int): Int {
            val k = readType0(i)
            return if (this[k - 1] == '.') readType1(k) else k
        }

        fun String.readType(i: Int): Int {
            if (this[i] in "[A*") return readType(i + 1)
            when (this[i]) {
                'L', 'T' -> {} // fine :)
                'Z', 'B', 'S', 'C', 'I', 'J', 'F', 'D' -> return i + 1 // native types or arrays
                else -> throw IllegalArgumentException("Unexpected type start in $this, $i: ${this[i]}")
            }
            return readType1(i)
        }

        fun dep(next0: String?, self: String) {
            when (next0) {
                "Z", "B", "S", "C", "I", "J", "F", "D", "V",
                "boolean", "byte", "short", "char", "int", "long", "float", "double", "void", null -> {
                    // class cannot be read
                }
                else -> {
                    val next = replaceClass(next0)
                    if (!next.startsWith("[") && hIndex.doneClasses.add(next)) {
                        try {
                            ClassReader(next)
                                .accept(FirstClassIndexer(next), 0)
                        } catch (e: IOException) {
                            LOGGER.warn("Missing $next by $self")
                            hIndex.missingClasses.add(next)
                        }
                    }
                }
            }
        }

    }

    fun dep(owner: String?) = Companion.dep(owner, clazz)

    var isInterface = false
    var isFinal = false

    override fun visit(
        version: Int,
        access: Int,
        name0: String,
        signature: String?,
        superName0: String?,
        interfaces0: Array<String>
    ) {

        val name = replaceClass(name0)
        val interfaces = interfaces0.map { replaceClass(it) }

        if (name != name0) {
            // ensure we visit the replaced class, too
            dep(name)
        }

        // if (signature != null && !clazz.startsWith("sun/") && !clazz.startsWith("jdk/"))
        //     println("[C] $name ($signature): $superName, ${interfaces.joinToString()}")

        hIndex.classFlags[name] = access
        isInterface = access.hasFlag(ACC_INTERFACE)
        isFinal = access.hasFlag(ACC_FINAL)

        if (isInterface) {
            hIndex.interfaceClasses.add(name)
        }

        var superName = replaceClassNullable(superName0)
        if (superName == null && name != "java/lang/Object") superName = "java/lang/Object"
        if (superName != null) {
            index.registerSuperClass(name, superName)
        }

        index.interfaces[name] = interfaces
        for (interfaceI in interfaces) {
            index.childClasses.getOrPut(interfaceI, ::HashSet).add(name)
            dep(interfaceI)
        }

        if (signature != null) {
            // class/super-class/interface contains generics-parameters
            findGenericSuperTypes(signature, interfaces)
        }
    }

    private fun findGenericSuperTypes(signature: String, interfaces: List<String>) {
        var i = 0
        // println("$clazz: $signature")
        if (signature.startsWith("<")) {
            // parse custom generic parameters
            // T:superType;V:nextType;
            i = 1
            val genericParams = ArrayList<GenericSig>() // name -> type
            while (signature[i] != '>') {
                val j = signature.indexOf(':', i)
                if (j < 0) throw IllegalStateException("$i has no further ':'s")
                val k0 = if (signature[j + 1] == ':') j + 2 else j + 1
                val k = signature.readType(k0) // double colon... why ever...
                if (signature[k - 1] != ';') throw IllegalStateException("end is ${signature[k - 1]}")
                val genericsName = signature.substring(i, j)
                val genericsSuperType = signature.substring(k0, k)
                genericParams.add(GenericSig("T$genericsName;", genericsSuperType))
                i = k
            }
            i++ // skip '>'
            if (genericParams.isNotEmpty()) {
                index.genericsByClass[clazz] = genericParams
                // println("  >> setting generics of $clazz to $genericParams")
            }
        }
        // read all super types
        // they might contain generics as well
        // format: Type1Type2Type3
        val superTypes = ArrayList<String>(interfaces.size + 1)
        while (i < signature.length) {
            val k = signature.readType(i)
            if (signature[k - 1] != ';') throw IllegalStateException("end is ${signature[k - 1]}")
            // find whether it has generics
            val hasGenerics = signature.indexOf('<', i) in i..k
            if (hasGenerics) {
                superTypes.add(signature.substring(i, k))
            }
            i = k
        }
        hIndex.genericSuperTypes[clazz] = superTypes
        // println("  > $clazz -> $genericParams : $superTypes")
    }

    override fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) {
        // might be important...
        dep(name)
        // println("inner class $clazz -> $name, $outerName, $innerName, $access")
        // if the class isn't static, it probably transfers generics :)
        if (!access.hasFlag(ACC_STATIC)) {
            val g0 = index.genericsByClass[name]
            val g1 = index.genericsByClass[clazz]
            if (g0 != null || g1 != null) {
                index.genericsByClass[name] = (g0 ?: emptyList()) + (g1 ?: emptyList())
            }
        }
    }

    // find all methods and fields that we need to discover
    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<String>?
    ): MethodVisitor {

        val isAbstract = access.hasFlag(ACC_ABSTRACT)
        val isNative = access.hasFlag(ACC_NATIVE)
        val isFinal = access.hasFlag(ACC_FINAL) or this.isFinal
        val isStatic = access.hasFlag(ACC_STATIC) || name == STATIC_INIT

        val sig = MethodSig.c(clazz, name, descriptor)
        if (signature != null) {
            hIndex.genericMethodSignatures[sig] = signature
        }

        HierarchyIndex.registerMethod(sig)
        methodFlags[sig] = access

        if (isStatic) {
            index.staticMethods.add(sig)
        }

        if (isFinal || isStatic) {
            index.finalMethods.add(sig)
        }

        when {
            isAbstract -> {
                index.abstractMethods.add(sig)
                index.notImplementedMethods.add(sig)
            }
            isNative -> {
                index.nativeMethods.add(sig)
            }
            else -> {
                index.jvmImplementedMethods.add(sig)
            }
        }

        if (isInterface && name != STATIC_INIT && !isAbstract) {
            hIndex.interfaceDefaults.getOrPut(sig, ::HashSet).add(sig)
        }

        for (type in sig.descriptor.params) {
            dep(type)
        }

        return FirstMethodIndexer(sig, this, isStatic)
    }

    override fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        value: Any?
    ): FieldVisitor? {

        // if field is final, just create a map, and don't even try to access those fields : make them "virtual"
        //  just hardcode them in the assembly :)
        val type = Descriptor.parseType(descriptor)
        val sig = FieldSig(clazz, name, type, access.hasFlag(ACC_STATIC))
        if (value != null && access.hasFlag(ACC_FINAL)) {
            hIndex.finalFields[sig] = value
        }

        if (signature != null) {
            hIndex.genericFieldSignatures[sig] = signature
        }

        // if (signature != null && !clazz.startsWith("sun/") && !clazz.startsWith("jdk/"))
        //    println("[F] $clazz $name $descriptor $signature")
        // TE -> +TE = ? extends TE, child classes
        // TE -> -TE = ? super TE, super classes
        // todo assign start value to field, if field is being used
        // todo this needs to be executed at the start of <clinit> / <init>
        //if (value != null && !clazz.startsWith("sun/") && !clazz.startsWith("jdk/"))
        //    println("todo: assign $clazz $name $descriptor $signature to \"$value\" (static? ${access.hasFlag(ACC_STATIC)})")
        dep(type)
        return null
    }

    override fun visitEnd() {
    }

}