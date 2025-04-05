package wasm2cpp.instr

import wasm2cpp.StackElement

class ExprCall(
    val funcName: String,
    val params: List<StackElement>,
    val resultTypes: List<String>,
    val resultName: String?,
    val resultType: String?
) : CppInstruction {

    companion object {
        const val RETURN_TYPE = "<return>"
    }

    val isReturn get() = resultType == RETURN_TYPE

}