package wasm.instr

import utils.WASMTypes.i32
import wasm.writer.Opcode

abstract class CompareInstr(
    name: String, operator: BinaryOperator, val type: String,
    val castType: String?, opcode: Opcode
) : BinaryInstruction(name, type, i32, operator, opcode) {

    val impl = comparators[operator]!!

    val flipped: BinaryOperator
        get() = when (operator) {
            BinaryOperator.GREATER_EQUAL -> BinaryOperator.LESS_EQUAL
            BinaryOperator.LESS_EQUAL -> BinaryOperator.GREATER_EQUAL
            BinaryOperator.GREATER -> BinaryOperator.LESS
            BinaryOperator.LESS -> BinaryOperator.GREATER
            BinaryOperator.EQUAL, BinaryOperator.NOT_EQUAL -> operator
            else -> throw NotImplementedError(operator.symbol)
        }

    companion object {
        private val comparators = mapOf<BinaryOperator, (Int) -> Boolean>(
            BinaryOperator.GREATER_EQUAL to { it >= 0 },
            BinaryOperator.LESS_EQUAL to { it <= 0 },
            BinaryOperator.GREATER to { it > 0 },
            BinaryOperator.LESS to { it < 0 },
            BinaryOperator.NOT_EQUAL to { it != 0 },
            BinaryOperator.EQUAL to { it == 0 }
        )
    }
}