package utils

@Suppress("DataClassPrivateConstructor")
data class InterfaceSig private constructor(val name: String, val descriptor: Descriptor) {
    constructor(sig: MethodSig) : this(sig.name, sig.descriptor)

    override fun toString() = "$name/$descriptor"

    fun withClass(clazz: String): MethodSig {
        return MethodSig.c(clazz, name, descriptor, false)
    }

    companion object {
        fun c(name: String, descriptor: Descriptor): InterfaceSig {
            return InterfaceSig(name, descriptor)
        }

        fun c(name: String, descriptor: String): InterfaceSig {
            return c(name, Descriptor.c(descriptor))
        }
    }
}