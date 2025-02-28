package hierarchy

import me.anno.utils.types.Booleans.hasFlag
import org.objectweb.asm.Opcodes.*
import utils.FieldSig
import utils.MethodSig
import utils.methodName
import wasm.instr.Instruction

object HierarchyIndex {

    private const val cap = 4096
    private const val cap2 = 256

    val superClass = HashMap<String, String>(cap)
    val childClasses = HashMap<String, HashSet<String>>(cap)
    val interfaces = HashMap<String, List<String>>(cap)
    val interfaceClasses = HashSet<String>()

    val methods = HashMap<String, HashSet<MethodSig>>(cap)
    val classFlags = HashMap<String, Int>(cap)

    val generics = HashMap<String, List<GenericSig>>(cap) // name, superType
    val genericSuperTypes = HashMap<String, List<String>>(cap)
    val genericMethodSigs = HashMap<MethodSig, String>(cap)

    val interfaceDefaults = HashMap<MethodSig, HashSet<MethodSig>>()

    val staticMethods = HashSet<MethodSig>(cap)
    val finalMethods = HashSet<MethodSig>(cap)
    val notImplementedMethods = HashSet<MethodSig>(cap)
    val jvmImplementedMethods = HashSet<MethodSig>(cap)
    val customImplementedMethods = HashSet<MethodSig>(cap2)
    val abstractMethods = HashSet<MethodSig>(cap)
    val nativeMethods = HashSet<MethodSig>(cap)
    val hasSuperMaybeMethods = HashSet<MethodSig>(cap2)
    val annotations = HashMap<MethodSig, List<Annota>>(cap2)

    val methodAliases = HashMap<String, MethodSig>(cap)

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

    // todo support static setter/getter methods as well :3
    val setterMethods = HashMap<MethodSig, FieldSig>(cap2)
    val getterMethods = HashMap<MethodSig, FieldSig>(cap2)
    // todo inline functions, which consist of a constant only

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

}