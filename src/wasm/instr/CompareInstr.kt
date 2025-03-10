package wasm.instr

abstract class CompareInstr(name: String, val operator: String, val type: String, val castType: String?) :
    SimpleInstr(name) {

    val impl = comparators[operator]!!

    val flipped: String
        get() = when (operator) {
            ">=" -> "<="
            "<=" -> ">="
            ">" -> "<"
            "<" -> ">"
            "==", "!=" -> operator
            else -> throw NotImplementedError(operator)
        }

    companion object {
        val comparators = mapOf<String, (Int) -> Boolean>(
            ">=" to { it >= 0 },
            "<=" to { it <= 0 },
            ">" to { it > 0 },
            "<" to { it < 0 },
            "!=" to { it != 0 },
            "==" to { it == 0 }
        )
    }
}