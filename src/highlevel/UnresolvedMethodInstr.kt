package highlevel

import utils.MethodSig
import wasm.instr.Call
import wasm.instr.CallIndirect
import wasm.instr.Const.Companion.i32Const
import wasm.instr.FuncType
import wasm.instr.Instruction

abstract class UnresolvedMethodInstr(
    original: MethodSig,
    val resolvedMethods: Set<MethodSig>,
    val funcType: FuncType,
    stackPushId: Int
) : InvokeMethodInstr(original, stackPushId) {

    abstract fun getResolutionCall(): Call
    abstract fun getResolutionId(): Int

    override fun toLowLevel(): List<Instruction> {
        return if (stackPushId >= 0) {
            listOf(
                i32Const(stackPushId), Call.stackPush,
                i32Const(getResolutionId()), getResolutionCall(),
                CallIndirect(funcType, resolvedMethods),
                Call.stackPop
            )
        } else {
            listOf(
                i32Const(getResolutionId()), getResolutionCall(),
                CallIndirect(funcType, resolvedMethods),
            )
        }
    }
}