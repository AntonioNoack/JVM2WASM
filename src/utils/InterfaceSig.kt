package utils

@Suppress("DataClassPrivateConstructor")
data class InterfaceSig private constructor(val name: String, val descriptor: String) {
    constructor(sig: MethodSig) : this(sig.name, sig.descriptor)

    override fun toString() = "$name/$descriptor"

    companion object {
        fun c(name: String, descriptor: String): InterfaceSig {
            return InterfaceSig(name, MethodSig.validateDescriptor(descriptor))
        }
    }
}