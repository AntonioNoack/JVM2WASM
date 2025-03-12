package utils

import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.lists.Lists.none2
import utils.WASMTypes.i32
import wasm.instr.*
import wasm.instr.Const.Companion.i32Const0
import wasm.instr.Const.Companion.i32Const1
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
import wasm.instr.Instructions.Unreachable
import wasm.parser.WATParser
import kotlin.math.min

object ReplaceOptimizer {

    private val ptrConst0 = if (is32Bits) i32Const0 else i64Const0

    private val replacements = listOf(

        listOf(I32EQ, I32EQZ) to listOf(I32NE),
        listOf(I32NE, I32EQZ) to listOf(I32EQ),
        listOf(I32GTS, I32EQZ) to listOf(I32LES),
        listOf(I32GES, I32EQZ) to listOf(I32LTS),
        listOf(I32LTS, I32EQZ) to listOf(I32GES),
        listOf(I32LES, I32EQZ) to listOf(I32GTS),
        listOf(I32EQZ, I32EQZ, I32EQZ) to listOf(I32EQZ),

        listOf(Call.lcmp, i32Const0, I32LES) to listOf(I64LES),
        listOf(Call.lcmp, i32Const0, I32LTS) to listOf(I64LTS),
        listOf(Call.lcmp, i32Const0, I32GES) to listOf(I64GES),
        listOf(Call.lcmp, i32Const0, I32GTS) to listOf(I64GTS),

        listOf(F64_PROMOTE_F32, F64_SQRT, F32_DEMOTE_F64) to listOf(F32_SQRT),

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

        // prefer I32EQZ over == 0, so we have fewer combinations in total
        listOf(i32Const0, I32EQ) to listOf(I32EQZ),
        listOf(i32Const0, I32NE) to listOf(I32EQZ, I32EQZ),

        // NaN isn't 0, and == returns false for NaN, so this is correct
        listOf(Call.fcmpg, I32EQZ) to listOf(F32EQ),
        listOf(Call.fcmpl, I32EQZ) to listOf(F32EQ),
        listOf(Call.dcmpg, I32EQZ) to listOf(F64EQ),
        listOf(Call.dcmpl, I32EQZ) to listOf(F64EQ),
    )

    fun optimizeUsingReplacements(printer: Builder) {
        optimizeUsingReplacements2(printer.instrs)
    }

    private fun optimizeUsingReplacements2(instructions0: List<Instruction>): List<Instruction> {
        if (instructions0.isEmpty()) return instructions0
        var instructions = instructions0

        var changed: Boolean
        fun makeMutable(): ArrayList<Instruction> {
            changed = true
            if (instructions !is ArrayList) {
                instructions = ArrayList(instructions)
            }
            return (instructions as ArrayList)
        }

        fun runReplacements() {
            for ((old, new) in replacements) {
                var offset = 0
                while (offset <= instructions.size - old.size) {
                    val li = instructions.startsWith(old, offset)
                    if (li >= 0) {
                        makeMutable().replace(new, offset, li)
                    } else offset++
                }
            }
        }

        do {
            changed = false
            runReplacements()
            var i = 0
            while (i < instructions.size) {
                when (val instr = instructions[i]) {
                    is IfBranch -> {
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
                            if (matchContents(instr.ifTrue, i32Const0) &&
                                matchContents(instr.ifFalse, i32Const1)
                            ) {
                                // pure negation, remove if-branch
                                makeMutable()[i] = I32EQZ
                            } else if (
                                matchContents(instr.ifTrue, i32Const1) &&
                                matchContents(instr.ifFalse, i32Const0)
                            ) {
                                // int to bool, remove if-branch
                                val mutable = makeMutable()
                                mutable[i] = I32EQZ
                                mutable.add(i, I32EQZ)
                                i--
                            } else if (
                                matchContents(instr.ifTrue, listOf(i32Const0, Return)) &&
                                matchContents(instr.ifFalse, listOf(i32Const1, Return))
                            ) {
                                // return reverse
                                val mutable = makeMutable()
                                mutable[i] = Return
                                mutable.add(i, I32EQZ)
                                i--
                            }
                        }
                        instr.ifTrue = optimizeUsingReplacements2(instr.ifTrue)
                        instr.ifFalse = optimizeUsingReplacements2(instr.ifFalse)
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
        } while (changed)

        return instructions
    }

    private fun matchContents(a: List<Instruction>, b: List<Instruction>): Boolean {
        assertTrue(b.none2 { it is Comment })
        var j = 0
        for (i in a.indices) {
            val ai = a[i]
            if (ai is Comment) continue
            val bi = b.getOrNull(j++)
            if (ai != bi) return false
        }
        return j == b.size
    }

    private fun matchContents(a: List<Instruction>, b: Instruction): Boolean {
        assertTrue(b !is Comment)
        var j = 0
        for (i in a.indices) {
            val ai = a[i]
            if (ai is Comment) continue
            val bi = if (j++ == 0) b else null
            if (ai != bi) return false
        }
        return j == 1
    }

    private fun List<Instruction>.prevIndex(i0: Int): Int {
        var i = i0 - 1
        while (i >= 0 && this[i] is Comment) i--
        return i
    }

    private fun <V> ArrayList<V>.replace(new: List<V>, i0: Int, i1: Int) {
        val prevSize = size
        val oldSize = i1 - i0
        val common = min(oldSize, new.size)
        for (i in 0 until common) {
            this[i0 + i] = new[i]
        }
        if (oldSize > common) {
            // shortening
            subList(i0 + common, i0 + oldSize).clear()
        } else {
            // making it longer
            addAll(i0 + common, new.subList(common, new.size))
        }
        assertEquals(size, prevSize + new.size - oldSize)
    }

    private fun <V> List<V>.startsWith(start: List<V>, offset: Int): Int {
        if (offset < 0 || offset + start.size > size) return -1 // quick-path
        if (this[offset] is Comment) return -1
        var si = 0
        var li = offset
        while (li < size && si < start.size) {
            val self = this[li++]
            if (self is Comment) continue
            if (start.getOrNull(si++) != self) return -1
        }
        if (si != start.size) return -1
        // li is the end index, exclusive
        while (getOrNull(li - 1) is Comment) li--
        return li
    }

    private fun testOptimize(expected: List<Instruction>, input: List<Instruction>) {
        val printer = Builder()
        printer.append(input)
        optimizeUsingReplacements(printer)
        assertEquals(expected, printer.instrs)
    }

    private fun runTests() {

        if (false) {
            testOptimize(listOf(I32NE), listOf(I32EQ, Comment("test"), I32EQZ))
            testOptimize(listOf(I32EQ), listOf(I32NE, Comment("test"), I32EQZ))
            testOptimize(listOf(I32NE, Comment("x")), listOf(I32EQ, Comment("test"), I32EQZ, Comment("x")))
            testOptimize(
                listOf(Comment("a"), I32NE, Comment("x")),
                listOf(Comment("a"), I32EQ, Comment("test"), I32EQZ, Comment("x"))
            )
            testOptimize(
                listOf(I32EQZ, I32EQZ),
                listOf(I32EQZ, IfBranch(listOf(i32Const0), listOf(i32Const1), emptyList(), listOf(i32)))
            )
            testOptimize(
                listOf(I32EQZ),
                listOf(I32EQZ, I32EQZ, IfBranch(listOf(i32Const0), listOf(i32Const1), emptyList(), listOf(i32)))
            )
            testOptimize(
                listOf(I32NE),
                listOf(I32EQ, IfBranch(listOf(i32Const0), listOf(i32Const1), emptyList(), listOf(i32)))
            )
            testOptimize(
                listOf(I32EQ),
                listOf(I32NE, IfBranch(listOf(i32Const0), listOf(i32Const1), emptyList(), listOf(i32)))
            )
            testOptimize(listOf(Return), listOf(Return, Unreachable))
            testOptimize(listOf(I32LTS), listOf(I32GES, I32EQZ))
        }

        val a = Comment("a")
        assertEquals(3, listOf(I32LTS, a, I32EQZ, IfBranch(emptyList())).startsWith(listOf(I32LTS, I32EQZ), 0))
        assertEquals(2, listOf(I32LES, I32EQZ).startsWith(listOf(I32LES, I32EQZ), 0))
        assertEquals(3, listOf(a, I32LES, I32EQZ).startsWith(listOf(I32LES, I32EQZ), 1))

        val testInput = "(func \$org_joml_AABBd_testAABB_DDDDDDZ (param i32 f64 f64 f64 f64 f64 f64) (result i32)\n" +
                "  i32.lt_s\n" +
                "  ;; jump iflt -> [L2], stack: []\n" +
                "  i32.eqz\n" +
                "  (if (then\n" +
                "    i32.const 1\n" +
                "    return\n" +
                "  ))\n" +
                ")"

        val testExpected =
            "(func \$org_joml_AABBd_testAABB_DDDDDDZ (param i32 f64 f64 f64 f64 f64 f64) (result i32)\n" +
                    "  i32.ge_s\n" +
                    "  (if (then\n" +
                    "    i32.const 1\n" +
                    "    return\n" +
                    "  ))\n" +
                    ")"

        testOptimize(parse(testExpected), parse(testInput))
        // todo bug: java_lang_Character_equals_Ljava_lang_ObjectZ uses double-negation at the end for no obvious reason
    }

    fun parse(code: String): List<Instruction> {
        val parser = WATParser()
        parser.parse(code)
        assertEquals(1, parser.functions.size)
        return parser.functions.first().body
    }

    @JvmStatic
    fun main(args: Array<String>) {
        runTests()
    }
}