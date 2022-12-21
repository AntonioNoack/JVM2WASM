package utils

data class GenericSig(val name: String, val descriptor: String) {
    constructor(sig: MethodSig) : this(sig.name, sig.descriptor)

    override fun toString() = "$name/$descriptor"
}