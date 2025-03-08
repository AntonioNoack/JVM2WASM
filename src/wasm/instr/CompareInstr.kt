package wasm.instr

class CompareInstr(name: String, val operator: String, val castType: String? = null) : SimpleInstr(name) {
    val type = name.substring(0, 3)

    val flipped: String
        get() = when (operator) {
            ">=" -> "<="
            "<=" -> ">="
            ">" -> "<"
            "<" -> ">"
            "==", "!=" -> operator
            else -> throw NotImplementedError(operator)
        }
}