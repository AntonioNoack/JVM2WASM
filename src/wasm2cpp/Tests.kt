package wasm2cpp

import me.anno.utils.assertions.assertEquals
import utils.Param.Companion.toParams
import utils.WASMTypes.i32
import wasm.instr.*
import wasm.instr.Const.Companion.i32Const0
import wasm.instr.Const.Companion.i32Const1
import wasm.instr.Const.Companion.i32Const2
import wasm.instr.Const.Companion.i32ConstM1
import wasm.instr.Instruction.Companion.emptyArrayList
import wasm.parser.FunctionImpl
import wasm.parser.Import
import wasm.parser.LocalVariable
import wasm.parser.WATParser

fun main() {
    // the generated C++ shall be identical
    //  - Loop[SwitchCase] VS Loop[SwitchCase] -> Text -> Loop[SwitchCase]
    val func0 = testFunction()
    println(func0)
    testWATParser(func0)
    testCppWriter(func0)
}

fun testCppWriter(func0: FunctionImpl) {
    assertEquals(0, writer.length)
    FunctionWriter(
        emptyMap(), mapOf(
            "AfterSwitch" to Import("AfterSwitch", emptyList(), emptyList()),
            "AfterLoop" to Import("AfterLoop", emptyList(), emptyList())
        )
    ).write(func0)
    println(writer.toString())
    // confirm everything correct
    assertEquals(
        "i32 testFunc(i32 p0, i32 p1) {\n" +
                "  loop: while (true) {\n" +
                "    case0: {\n" +
                "      goto case1;\n" +
                "    }\n" +
                "    case1: {\n" +
                "      goto case2;\n" +
                "    }\n" +
                "    case2: {\n" +
                "    }\n" +
                "    AfterSwitch();\n" +
                "    break;\n" +
                "  }\n" +
                "  AfterLoop();\n" +
                "  return -1;\n" +
                "}\n", writer.toString()
    )
    writer.clear()
}

fun testWATParser(func0: FunctionImpl) {
    val parser = WATParser()
    parser.parse(func0.toString())
    val func1 = parser.functions.apply {
        assertEquals(1, size)
    }[0]
    assertEquals(func0.toString(), func1.toString())
}

fun testFunction(): FunctionImpl {
    val label = "lbl"
    val loopName = "loop"
    val loopInstr = LoopInstr(
        loopName, emptyArrayList,
        emptyList(), emptyList()
    )
    loopInstr.body = arrayListOf(
        SwitchCase(
            label,
            listOf(
                arrayListOf(i32Const1, LocalSet(label), Jump(loopInstr)),
                arrayListOf(i32Const2, LocalSet(label), Jump(loopInstr)),
                emptyArrayList
            ), emptyList(), emptyList()
        ),
        Call("AfterSwitch")
    )
    return FunctionImpl(
        "testFunc", listOf(i32, i32).toParams(), listOf(i32),
        listOf(LocalVariable(label, i32)),
        arrayListOf(
            i32Const0, LocalSet(label),
            loopInstr,
            Call("AfterLoop"),
            i32ConstM1, // return value
        ),
        false
    )
}