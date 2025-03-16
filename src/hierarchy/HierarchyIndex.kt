package hierarchy

import me.anno.utils.structures.lists.Lists.firstOrNull2
import me.anno.utils.types.Booleans.hasFlag
import org.apache.logging.log4j.LogManager
import org.objectweb.asm.Opcodes.*
import utils.CallSignature
import utils.FieldSig
import utils.MethodSig
import utils.methodName
import wasm.instr.Instruction

object HierarchyIndex {

    private const val cap = 1 shl 14
    private const val cap2 = 1 shl 10

    val superClass = HashMap<String, String>(cap)
    val childClasses = HashMap<String, HashSet<String>>(cap)
    val interfaces = HashMap<String, List<String>>(cap)
    val interfaceClasses = HashSet<String>()

    val methodsByClass = HashMap<String, HashSet<MethodSig>>(cap)
    val classFlags = HashMap<String, Int>(cap)
    val methodFlags = HashMap<MethodSig, Int>(cap)

    val genericsByClass = HashMap<String, List<GenericSig>>(cap) // name, superType
    val genericSuperTypes = HashMap<String, List<String>>(cap)
    val genericMethodSignatures = HashMap<MethodSig, String>(cap)

    val interfaceDefaults = HashMap<MethodSig, HashSet<MethodSig>>()

    val notImplementedMethods = HashSet<MethodSig>(cap)
    val jvmImplementedMethods = HashSet<MethodSig>(cap)
    val customImplementedMethods = HashSet<MethodSig>(cap2)
    val annotations = HashMap<MethodSig, List<Annota>>(cap2)

    val methodAliases = HashMap<String, MethodSig>(cap)

    val implementedCallSignatures = HashSet<CallSignature>()

    fun addFinalMethod(method: MethodSig) {
        methodFlags[method] = getMethodFlags(method) or ACC_FINAL
    }

    fun countFinalMethods(): Int {
        return methodFlags.values.count { it.hasFlag(ACC_FINAL) }
    }

    fun isFinal(method: MethodSig): Boolean {
        return getMethodFlags(method).hasFlag(ACC_FINAL)
    }

    fun isNative(method: MethodSig): Boolean {
        return getMethodFlags(method).hasFlag(ACC_NATIVE)
    }

    fun isAbstract(method: MethodSig): Boolean {
        return getMethodFlags(method).hasFlag(ACC_ABSTRACT)
    }

    fun isStatic(method: MethodSig): Boolean {
        return getMethodFlags(method).hasFlag(ACC_STATIC)
    }

    fun registerMethod(method: MethodSig): Boolean {
        return methodsByClass.getOrPut(method.clazz, ::HashSet).add(method)
    }

    fun getAnnotations(sig: MethodSig): List<Annota> {
        return annotations[sig] ?: emptyList()
    }

    fun getAnnotation(sig: MethodSig, clazz: String): Annota? {
        return getAnnotations(sig).firstOrNull2 { it.clazz == clazz }
    }

    fun hasAnnotation(sig: MethodSig, clazz: String): Boolean {
        return getAnnotation(sig, clazz) != null
    }

    fun getAlias(sig: MethodSig): MethodSig {
        val sig1 = methodAliases[methodName(sig)] ?: sig
        return if (sig1 != sig) getAlias(sig1) else sig
    }

    fun getAlias(methodName: String): MethodSig? {
        val sig1 = methodAliases[methodName] ?: return null
        return getAlias(sig1)
    }

    fun setAlias(from: MethodSig, to: MethodSig) {
        setAlias(methodName(from), to)
    }

    fun setAlias(from: String, to: MethodSig) {
        methodAliases[from] = to
    }

    fun registerSuperClass(clazz: String, superClazz: String) {
        superClass[clazz] = superClazz
        childClasses.getOrPut(superClazz, ::HashSet).add(clazz)
    }

    val inlined = HashMap<MethodSig, List<Instruction>>(cap2)
    val wasmNative = HashMap<MethodSig, List<Instruction>>(cap2)

    val syntheticClasses = HashSet<String>(cap2)

    val doneClasses = HashSet<String>(cap)
    val missingClasses = HashSet<String>(cap2)

    val finalFields = HashMap<FieldSig, Any>(cap)

    val exportedMethods = HashSet<MethodSig>(cap)
    val usesSelf = HashMap<MethodSig, Boolean>(cap)

    val emptyFunctions = HashSet<MethodSig>(cap)

    // todo support static setter/getter methods as well :3
    val setterMethods = HashMap<MethodSig, FieldSig>(cap2)
    val getterMethods = HashMap<MethodSig, FieldSig>(cap2)
    // todo inline functions, which consist of a constant only

    fun getMethodFlags(sig: MethodSig): Int {
        return methodFlags.getOrDefault(sig, 0)
    }

    fun getClassFlags(clazz: String): Int {
        return classFlags.getOrDefault(clazz, 0)
    }

    fun isAbstractClass(clazz: String): Boolean {
        return getClassFlags(clazz).hasFlag(ACC_ABSTRACT)
    }

    fun isInterfaceClass(clazz: String): Boolean {
        return getClassFlags(clazz).hasFlag(ACC_INTERFACE)
    }

    fun isEnumClass(clazz: String): Boolean {
        return getClassFlags(clazz).hasFlag(ACC_ENUM)
    }

    fun isChildClassOfInterface(child: String, parent: String): Boolean {
        if (child == parent) return true
        if (parent in (interfaces[child] ?: emptyList())) {
            return true
        }
        val parent1 = superClass[child] ?: return false
        return isChildClassOfInterface(parent1, parent)
    }

    fun isChildClassOfSuper(child: String, parent: String): Boolean {
        if (child == parent) return true
        val parent1 = superClass[child] ?: return false
        return isChildClassOfInterface(parent1, parent)
    }

    fun isChildClassOf(child: String, parent: String): Boolean {
        return if (isInterfaceClass(parent)) {
            isChildClassOfInterface(child, parent)
        } else {
            isChildClassOfSuper(child, parent)
        }
    }
}