package wasm2cpp.instr

import utils.MethodSig
import wasm2cpp.StackElement
import wasm2js.shortName

class UnresolvedCallAssignment(
    val self: StackElement?, val sig: MethodSig, val isSpecial: Boolean,
    params: List<StackElement>, resultTypes: List<String>,
    resultName: String?, resultType: String?
) : CallAssignment(shortName(sig), params, resultTypes, resultName, resultType) {

    override fun withResult(resultName: String?, resultType: String?): CallAssignment {
        return UnresolvedCallAssignment(
            self, sig, isSpecial,
            params, resultTypes, resultName, resultType
        )
    }
}