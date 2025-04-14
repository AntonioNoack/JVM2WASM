package wasm2cpp.instr

import wasm2cpp.StackElement

open class CallAssignment(
    funcName: String,
    params: List<StackElement>,
    val resultTypes: List<String>,
    val resultName: String?,
    val resultType: String?
) : ExprCall(funcName, params) {

    companion object {
        const val RETURN_TYPE = "<return>"
    }

    val isReturn get() = resultType == RETURN_TYPE

    open fun withResult(resultName: String?, resultType: String?): CallAssignment {
        return CallAssignment(funcName, params, resultTypes, resultName, resultType)
    }

}