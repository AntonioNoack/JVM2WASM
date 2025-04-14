package highlevel

import utils.MethodSig
import utils.methodName
import wasm.instr.Call
import wasm.instr.Const.Companion.i32Const
import wasm.instr.Instruction

abstract class ResolvedMethodInstr(
    original: MethodSig,
    val resolved: MethodSig,
    stackPushId: Int
) : InvokeMethodInstr(original, stackPushId) {

    val callName get() = methodName(resolved)

    override fun toLowLevel(): List<Instruction> {
        val call = Call(callName)
        return if (stackPushId >= 0) {
            listOf(
                i32Const(stackPushId), Call.stackPush,
                call, Call.stackPop
            )
        } else listOf(call)
    }
}