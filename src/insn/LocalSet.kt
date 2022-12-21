package insn

class LocalSet(val name: String) {
    override fun toString(): String {
        return " local.set $name"
    }
}