package utils

import me.anno.utils.assertions.assertEquals
import wasm.instr.*
import wasm.instr.Const.Companion.i32Const0
import wasm.instr.Const.Companion.i64Const0
import wasm.instr.Instructions.F32EQ
import wasm.instr.Instructions.F32GE
import wasm.instr.Instructions.F32GT
import wasm.instr.Instructions.F32LE
import wasm.instr.Instructions.F32LT
import wasm.instr.Instructions.F32_DEMOTE_F64
import wasm.instr.Instructions.F32_SQRT
import wasm.instr.Instructions.F64EQ
import wasm.instr.Instructions.F64GE
import wasm.instr.Instructions.F64GT
import wasm.instr.Instructions.F64LE
import wasm.instr.Instructions.F64LT
import wasm.instr.Instructions.F64_PROMOTE_F32
import wasm.instr.Instructions.F64_SQRT
import wasm.instr.Instructions.I32EQ
import wasm.instr.Instructions.I32EQZ
import wasm.instr.Instructions.I32GES
import wasm.instr.Instructions.I32GTS
import wasm.instr.Instructions.I32LES
import wasm.instr.Instructions.I32LTS
import wasm.instr.Instructions.I32NE
import wasm.instr.Instructions.I64GES
import wasm.instr.Instructions.I64GTS
import wasm.instr.Instructions.I64LES
import wasm.instr.Instructions.I64LTS
import wasm.instr.Instructions.Return
import kotlin.math.min

object ReplaceOptimizer {

    private val ptrConst0 = if (is32Bits) i32Const0 else i64Const0

    private val replacements = listOf(
        listOf(I32EQ, I32EQZ) to listOf(I32NE),
        listOf(
            LocalSet("l0"),
            LocalGet("l0"),
            IfBranch(
                listOf(i32Const0, LocalGet("l0"), Return),
                emptyList(), emptyList(), emptyList()
            ),
            ptrConst0, Return
        ) to listOf(Return),
        listOf(
            LocalSet("l0"),
            LocalGet("l0"),
            IfBranch(
                listOf(LocalGet("l0"), Return),
                emptyList(), emptyList(), emptyList()
            ),
            ptrConst0, Return
        ) to listOf(Return),

        listOf(Call.lcmp, i32Const0, I32LES) to listOf(I64LES),
        listOf(Call.lcmp, i32Const0, I32LTS) to listOf(I64LTS),
        listOf(Call.lcmp, i32Const0, I32GES) to listOf(I64GES),
        listOf(Call.lcmp, i32Const0, I32GTS) to listOf(I64GTS),

        listOf(
            F64_PROMOTE_F32, F64_SQRT,
            Comment("static-inlined jvm/JavaLang/java_lang_StrictMath_sqrt_DD(D)D"),
            F32_DEMOTE_F64
        ) to listOf(F32_SQRT),

        // NaN = -1 X <= -> would return true for NaN -> must be negated
        listOf(Call.fcmpl, i32Const0, I32LES) to listOf(F32GT, I32EQZ),
        listOf(Call.fcmpl, i32Const0, I32LTS) to listOf(F32GE, I32EQZ),
        listOf(Call.fcmpl, i32Const0, I32GTS) to listOf(F32GT), // ok like that
        listOf(Call.fcmpl, i32Const0, I32GES) to listOf(F32GE),

        // NaN = -1 X <= -> would return true for NaN -> must be negated
        listOf(Call.dcmpl, i32Const0, I32LES) to listOf(F64GT, I32EQZ),
        listOf(Call.dcmpl, i32Const0, I32LTS) to listOf(F64GE, I32EQZ),
        listOf(Call.dcmpl, i32Const0, I32GTS) to listOf(F64GT), // ok like that
        listOf(Call.dcmpl, i32Const0, I32GES) to listOf(F64GE),

        // NaN = +1 X >= -> would return true for NaN -> must be negated
        listOf(Call.fcmpg, i32Const0, I32GES) to listOf(F32LT, I32EQZ),
        listOf(Call.fcmpg, i32Const0, I32GTS) to listOf(F32LE, I32EQZ),
        listOf(Call.fcmpg, i32Const0, I32LTS) to listOf(F32LT), // ok like that
        listOf(Call.fcmpg, i32Const0, I32LES) to listOf(F32LE),

        // NaN = +1 X >= -> would return true for NaN -> must be negated
        listOf(Call.dcmpg, i32Const0, I32GES) to listOf(F64LT, I32EQZ),
        listOf(Call.dcmpg, i32Const0, I32GTS) to listOf(F64LE, I32EQZ),
        listOf(Call.dcmpg, i32Const0, I32LTS) to listOf(F64LT), // ok like that
        listOf(Call.dcmpg, i32Const0, I32LES) to listOf(F64LE),
    )

    fun optimizeUsingReplacements(printer: Builder) {
        optimizeUsingReplacements2(printer.instrs)
    }

    private fun optimizeUsingReplacements2(instructions0: List<Instruction>): List<Instruction> {
        if (instructions0.isEmpty()) return instructions0
        var instructions = instructions0
        fun makeMutable(): ArrayList<Instruction> {
            if (instructions !is ArrayList) {
                instructions = ArrayList(instructions)
            }
            return (instructions as ArrayList)
        }
        for ((old, new) in replacements) {
            var offset = if (old.last().isReturning()) instructions.size - old.size else 0
            while (offset <= instructions.size - old.size) {
                if (instructions.startsWith(old, offset)) {
                    makeMutable().replace(old, new, offset)
                } else offset++
            }
        }
        var i = 0
        while (i < instructions.size) {
            when (val instr = instructions[i]) {
                is IfBranch -> {
                    fun optimizeBranches() {
                        instr.ifTrue = optimizeUsingReplacements2(instr.ifTrue)
                        instr.ifFalse = optimizeUsingReplacements2(instr.ifFalse)
                    }
                    if (instr.ifFalse.isNotEmpty()) {
                        val prevIdx = instructions.prevIndex(i)
                        val prevInstr = instructions.getOrNull(prevIdx)
                        fun swap() {
                            val tmp = instr.ifTrue
                            instr.ifTrue = instr.ifFalse
                            instr.ifFalse = tmp
                        }
                        when (prevInstr) {
                            I32EQZ -> {
                                makeMutable().removeAt(prevIdx)
                                swap()
                                i--
                            }
                            Call.fcmpl, Call.fcmpg -> {
                                // NaN-signedness doesn't matter here
                                makeMutable()[prevIdx] = F32EQ
                                swap()
                            }
                            Call.dcmpl, Call.dcmpg -> {
                                // NaN-signedness doesn't matter here
                                makeMutable()[prevIdx] = F64EQ
                                swap()
                            }
                        }
                    }
                    optimizeBranches()
                }
                is LoopInstr -> {
                    instr.body = optimizeUsingReplacements2(instr.body)
                }
                is SwitchCase -> {
                    instr.cases = instr.cases.map { instructions1 ->
                        optimizeUsingReplacements2(instructions1)
                    }
                }
            }
            i++
        }
        return instructions
    }

    private fun List<Instruction>.prevIndex(i0: Int): Int {
        var i = i0 - 1
        while (i >= 0 && this[i] is Comment) i--
        return i
    }

    private fun <V> ArrayList<V>.replace(old: List<V>, new: List<V>, offset: Int) {
        val prevSize = size
        val common = min(old.size, new.size)
        for (i in 0 until common) {
            this[offset + i] = new[i]
        }
        if (old.size > common) {
            // shortening
            subList(offset + common, offset + old.size).clear()
        } else {
            // making it longer
            addAll(offset + common, new.subList(common, new.size))
        }
        assertEquals(size, prevSize + new.size - old.size)
    }

    private fun <V> List<V>.startsWith(start: List<V>, offset: Int): Boolean {
        if (offset < 0 || offset + start.size > size) return false
        for (i in start.indices) {
            if (start[i] != this[i + offset]) return false
        }
        return true
    }

}