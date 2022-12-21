package hierarchy

import utils.FieldSig
import utils.GenericSig
import utils.MethodSig
import utils.methodName

object HierarchyIndex {

    private val cap = 4096
    private val cap2 = 256

    val superClass = HashMap<String, String>(cap)
    val childClasses = HashMap<String, HashSet<String>>(cap)
    val interfaces = HashMap<String, Array<String>>(cap)
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
    val abstractMethods = HashSet<MethodSig>(cap)
    val nativeMethods = HashSet<MethodSig>(cap)
    val hasSuperMaybeMethods = HashSet<MethodSig>(cap2)
    val annotations = HashMap<MethodSig, List<Annota>>(cap2)

    val methodAliases = HashMap<String, MethodSig>(cap)

    fun alias(sig: MethodSig): MethodSig {
        val sig1 = methodAliases[methodName(sig)] ?: sig
        return if (sig1 != sig) alias(sig1) else sig
    }

    val inlined = HashMap<MethodSig, String>(cap2)
    val wasmNative = HashMap<MethodSig, String>(cap2)

    val syntheticClasses = HashSet<String>(cap2)

    val doneClasses = HashSet<String>(cap)
    val missingClasses = HashSet<String>(cap2)

    val finalFields = HashMap<FieldSig, Any>(cap)

    val exportedMethods = HashSet<MethodSig>(cap)
    val usesSelf = HashMap<MethodSig, Boolean>(cap)

    // todo support static methods as well :3
    val setterMethods = HashMap<MethodSig, FieldSig>(cap2)
    val getterMethods = HashMap<MethodSig, FieldSig>(cap2)
    val emptyMethods = HashSet<MethodSig>(cap2) // todo use this to skip invocations
    // todo inline functions, which consist of a constant only

}