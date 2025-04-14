package highlevel

import utils.MethodSig
import wasm.instr.Call
import wasm.instr.FuncType

class InvokeInterfaceInstr(
    original: MethodSig, resolvedMethods: Set<MethodSig>, funcType: FuncType,
    stackPushId: Int, val interfaceId: Int,
) : UnresolvedMethodInstr(original, resolvedMethods, funcType, stackPushId) {
    override fun getResolutionCall(): Call {
        return Call.resolveInterface
    }

    override fun getResolutionId(): Int {
        return interfaceId
    }
}