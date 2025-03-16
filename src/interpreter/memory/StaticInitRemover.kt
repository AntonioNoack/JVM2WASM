package interpreter.memory

import gIndex
import utils.MethodSig
import utils.STATIC_INIT
import wasm.instr.*
import wasm.instr.Instructions.Return
import wasm.parser.FunctionImpl

object StaticInitRemover {

    private val initSig = MethodSig.c("jvm/JVMShared", "init", "()V")
    private val emptyInitBody = listOf(Return)

    fun removeStaticInit() {
        // todo it would be nice to completely remove them;
        //  for that, we need to remove them from the dynamic index (replace them with a dummy),
        //  or remap all dynamic index data (nah, maybe later)
        removeInitFunction()
        removeStaticInitFromFunctionBodies()
    }

    private fun removeInitFunction() {
        gIndex.translatedMethods[initSig] = FunctionImpl(
            "init", emptyList(), emptyList(),
            emptyList(), emptyInitBody, true
        )
    }

    private fun removeStaticInitFromFunctionBodies() {
        for ((sig, func) in gIndex.translatedMethods) {
            if (sig.name == STATIC_INIT || func.funcName.startsWith("static_")) {
                func.body = emptyInitBody
                func.locals = emptyList()
            } else {
                func.body = removeStaticInit(func.body)
            }
        }
    }

    private fun removeStaticInit(instr0: List<Instruction>): List<Instruction> {
        val instr1 = instr0 as? MutableList<Instruction> ?: ArrayList(instr0)
        var i = 0
        while (i < instr1.size) {
            when (val instr = instr1[i]) {
                is Call -> {
                    if (instr.name.startsWith("static_")) {
                        val prevInstr2 = instr1.getOrNull(i - 2)
                        val prevInstr = instr1.getOrNull(i - 1)
                        val nextInstr = instr1.getOrNull(i + 1)
                        if (prevInstr is Call && prevInstr.name == "stackPush" &&
                            nextInstr is Call && nextInstr.name == "stackPop" &&
                            prevInstr2 is Const
                        ) {
                            instr1.subList(i - 2, i + 1).clear()
                            i -= 3 // -2 from prevInstr2, -1 from i++
                        } else {
                            instr1.removeAt(i)
                            i--
                        }
                    }
                }
                is IfBranch -> {
                    instr.ifTrue = removeStaticInit(instr.ifTrue)
                    instr.ifFalse = removeStaticInit(instr.ifFalse)
                }
                is LoopInstr -> {
                    instr.body = removeStaticInit(instr.body)
                }
                is SwitchCase -> {
                    instr.cases = instr.cases.map { caseI ->
                        removeStaticInit(caseI)
                    }
                }
            }
            i++
        }

        return instr1
    }

}