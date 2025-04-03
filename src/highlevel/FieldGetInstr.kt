package highlevel

import alwaysUseFieldCalls
import highlevel.FieldSetInstr.Companion.getFieldAddr
import interpreter.WASMEngine
import utils.FieldSig
import utils.is32Bits
import wasm.instr.Call
import wasm.instr.Const.Companion.i32Const
import wasm.instr.Const.Companion.ptrConst
import wasm.instr.Instruction
import wasm.instr.Instructions.I32Add
import wasm.instr.Instructions.I64Add
import wasm.instr.LoadInstr

class FieldGetInstr(
    val fieldSig: FieldSig,
    val loadInstr: LoadInstr,
    val loadCall: Call
) : HighLevelInstruction {
    override fun execute(engine: WASMEngine): String? {
        val self = if (fieldSig.isStatic) null else engine.pop()
        val addr = getFieldAddr(self, fieldSig)
        loadInstr.load(engine, addr)
        return null
    }

    override fun toLowLevel(): List<Instruction> {
        val offset = getFieldAddr(if (fieldSig.isStatic) null else 0, fieldSig)
        return when {
            alwaysUseFieldCalls -> listOf(i32Const(offset), loadCall)
            fieldSig.isStatic -> listOf(ptrConst(offset), loadInstr)
            else -> listOf(ptrConst(offset), if (is32Bits) I32Add else I64Add, loadInstr)
        }
    }
}