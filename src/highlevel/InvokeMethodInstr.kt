package highlevel

import utils.MethodSig
import wasm.instr.Call
import wasm.instr.CallIndirect
import wasm.instr.Const.Companion.i32Const
import wasm.instr.FuncType
import wasm.instr.Instruction

abstract class InvokeMethodInstr(
    val original: MethodSig,
    val stackPushId: Int
) : HighLevelInstruction()