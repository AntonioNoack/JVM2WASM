package hierarchy

import me.anno.utils.structures.lists.Lists.firstOrNull2
import me.anno.utils.types.Booleans.hasFlag
import org.objectweb.asm.Opcodes.*
import utils.*
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
    val methodByName = HashMap<String, MethodSig>(cap)

    val genericsByClass = HashMap<String, List<GenericSig>>(cap) // name, superType
    val genericSuperTypes = HashMap<String, List<String>>(cap)
    val genericMethodSignatures = HashMap<MethodSig, String>(cap)

    val interfaceDefaults = HashMap<MethodSig, HashSet<MethodSig>>()

    val staticMethods = HashSet<MethodSig>(cap)
    val finalMethods = HashSet<MethodSig>(cap)
    val notImplementedMethods = HashSet<MethodSig>(cap)
    val jvmImplementedMethods = HashSet<MethodSig>(cap)
    val customImplementedMethods = HashSet<MethodSig>(cap2)
    val abstractMethods = HashSet<MethodSig>(cap)
    val nativeMethods = HashSet<MethodSig>(cap)
    val hasSuperMaybeMethods = HashSet<MethodSig>(cap2)
    val annotations = HashMap<MethodSig, ArrayList<Annota>>(cap2)

    val methodAliases = HashMap<String, MethodSig>(cap)

    val implementedCallSignatures = HashSet<CallSignature>()

    fun addAnnotation(sig: MethodSig, annotation: Annota) {
        annotations.getOrPut(sig, ::ArrayList).add(annotation)
    }

    fun isStatic(method: MethodSig): Boolean {
        return method.name == STATIC_INIT || method in staticMethods
    }

    fun isFinal(method: MethodSig): Boolean {
        return method in finalMethods
    }

    fun isNative(method: MethodSig): Boolean {
        return method in nativeMethods
    }

    fun registerMethod(method: MethodSig): Boolean {
        methodByName[methodName(method)] = method
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
    val genericFieldSignatures = HashMap<FieldSig, String>(cap)

    val exportedMethods = HashSet<MethodSig>(cap)
    val usesSelf = HashMap<MethodSig, Boolean>(cap)

    val emptyFunctions = HashSet<MethodSig>(cap)

    // todo support static setter/getter methods as well :3
    val setterMethods = HashMap<MethodSig, FieldSig>(cap2)
    val getterMethods = HashMap<MethodSig, FieldSig>(cap2)
    // todo inline functions, which consist of a constant only

    fun isAbstract(sig: MethodSig): Boolean = sig in abstractMethods

    fun isAbstractClass(clazz: String): Boolean {
        val flags = classFlags.getOrDefault(clazz, 0)
        return flags.hasFlag(ACC_ABSTRACT)
    }

    fun isInterfaceClass(clazz: String): Boolean {
        val flags = classFlags.getOrDefault(clazz, 0)
        return flags.hasFlag(ACC_INTERFACE)
    }

    fun isEnumClass(clazz: String): Boolean {
        val flags = classFlags.getOrDefault(clazz, 0)
        return flags.hasFlag(ACC_ENUM)
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