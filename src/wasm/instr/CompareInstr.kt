package wasm.instr

import utils.WASMTypes.i32

abstract class CompareInstr(
    name: String, val operator: String, val type: String, val castType: String?
) : BinaryInstruction(name, type, i32, operator) {

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