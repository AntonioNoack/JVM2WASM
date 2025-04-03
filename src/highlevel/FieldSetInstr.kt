package highlevel

import alwaysUseFieldCalls
import interpreter.WASMEngine
import me.anno.utils.assertions.assertEquals
import translator.GeneratorIndex
import utils.FieldSig
import utils.is32Bits
import utils.lookupStaticVariable
import wasm.instr.Call
import wasm.instr.Const.Companion.i32Const
import wasm.instr.Const.Companion.ptrConst
import wasm.instr.Instruction
import wasm.instr.Instructions.I32Add
import wasm.instr.Instructions.I64Add
import wasm.instr.StoreInstr

class FieldSetInstr(
    val fieldSig: FieldSig,
    val storeInstr: StoreInstr,
    val storeCall: Call
) : HighLevelInstruction {

    companion object {
        fun getFieldAddr(self: Number?, fieldSig: FieldSig): Int {
            assertEquals(self == null, fieldSig.isStatic)
            val fieldOffset = GeneratorIndex.getFieldOffset(fieldSig)!!
            return if (self == null) {
                lookupStaticVariable(fieldSig.clazz, fieldOffset)
            } else {
                self.toInt() + fieldOffset
            }
        }
    }

    override fun execute(engine: WASMEngine): String? {
        val value = engine.pop()
        val self = if (fieldSig.isStatic) null else engine.pop()
        val addr = getFieldAddr(self, fieldSig)
        storeInstr.store(engine, addr, value)
        return null
    }

    override fun toLowLevel(): List<Instruction> {
        val offset = getFieldAddr(if(fieldSig.isStatic) null else 0, fieldSig)
        return when {
            alwaysUseFieldCalls -> listOf(i32Const(offset), storeCall)
            fieldSig.isStatic -> listOf(ptrConst(offset), storeInstr)
            else -> listOf(ptrConst(offset), if (is32Bits) I32Add else I64Add, storeInstr)
        }
    }
}