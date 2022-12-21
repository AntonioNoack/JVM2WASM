package insn

class LocalGet(val name: String) {
    override fun toString(): String {
        return " local.get $name"
    }
}