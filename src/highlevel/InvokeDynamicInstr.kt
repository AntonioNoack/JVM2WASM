package highlevel

import utils.MethodSig
import wasm.instr.Call
import wasm.instr.FuncType

class InvokeDynamicInstr(
    original: MethodSig, resolvedMethods: Set<MethodSig>,
    funcType: FuncType, stackPushId: Int, val dynMethodIdOffset: Int
) : UnresolvedMethodInstr(original, resolvedMethods, funcType, stackPushId) {
    override fun getResolutionCall(): Call {
        return Call.resolveIndirect
    }

    override fun getResolutionId(): Int {
        return dynMethodIdOffset
    }
}