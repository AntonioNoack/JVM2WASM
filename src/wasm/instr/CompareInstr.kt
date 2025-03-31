package wasm.instr

import utils.WASMTypes.i32
import wasm.writer.Opcode

abstract class CompareInstr(
    name: String, val operator: String, val type: String,
    val castType: String?, opcode: Opcode
) : BinaryInstruction(name, type, i32, operator, opcode) {

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