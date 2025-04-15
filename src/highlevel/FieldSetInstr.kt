package highlevel

import alwaysUseFieldCalls
import interpreter.WASMEngine
import jvm.JVMFlags.is32Bits
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertTrue
import translator.GeneratorIndex
import utils.FieldSig
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
    val storeCall: Call,
    val reversed: Boolean
) : HighLevelInstruction() {

    companion object {
        fun getFieldAddr(self: Number?, fieldSig: FieldSig): Int {
            assertEquals(self == null, fieldSig.isStatic)
            val fieldOffset = GeneratorIndex.getFieldOffset(fieldSig)
                ?: throw IllegalStateException("Missing field offset for $fieldSig")
            return if (self == null) {
                lookupStaticVariable(fieldSig.clazz, fieldOffset)
            } else {
                self.toInt() + fieldOffset
            }
        }
    }

    init {
        // reversed is meaningless for static fields, so rather not set it at all
        assertTrue(!reversed || !fieldSig.isStatic)
    }

    override fun execute(engine: WASMEngine): String? {
        if (reversed) {
            val self = engine.pop()
            val value = engine.pop()
            val addr = getFieldAddr(self, fieldSig)
            storeInstr.store(engine, addr, value)
            return null
        } else {
            val value = engine.pop()
            val self = if (fieldSig.isStatic) null else engine.pop()
            val addr = getFieldAddr(self, fieldSig)
            storeInstr.store(engine, addr, value)
            return null
        }
    }

    override fun toLowLevel(): List<Instruction> {
        val offset = getFieldAddr(if (fieldSig.isStatic) null else 0, fieldSig)
        return when {
            alwaysUseFieldCalls -> listOf(i32Const(offset), storeCall)
            fieldSig.isStatic -> listOf(ptrConst(offset), storeInstr)
            else -> {
                if (!reversed) throw NotImplementedError("Must swap arguments before and after adding offset")
                listOf(ptrConst(offset), if (is32Bits) I32Add else I64Add, storeInstr)
            }
        }
    }
}