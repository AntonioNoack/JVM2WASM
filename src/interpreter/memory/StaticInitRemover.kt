package interpreter.memory

import gIndex
import me.anno.utils.structures.maps.Maps.removeIf
import utils.Descriptor.Companion.voidDescriptor
import utils.MethodSig
import utils.STATIC_INIT
import utils.functionTable
import wasm.instr.*
import wasm.instr.Instructions.Return
import wasm.parser.FunctionImpl

object StaticInitRemover {

    private val initSig = MethodSig.c("jvm/JVMShared", "init", voidDescriptor)
    private val emptyInitBody = arrayListOf<Instruction>(Return)

    fun removeStaticInit() {
        removeInitFunction()
        removeStaticInitFromFunctionBodies()
        removeStaticInitFromFunctionTable()

        // todo remove functions, that are no longer references, e.g., GC-init-helpers
    }

    private fun removeInitFunction() {
        gIndex.translatedMethods[initSig] = FunctionImpl(
            "init", emptyList(), emptyList(),
            emptyList(), emptyInitBody, true
        )
    }

    private fun removeStaticInitFromFunctionTable() {
        // to do it would be nice to completely remove them;
        //  for that, we need to remap all dynamic index data (nah, maybe later)
        functionTable.replaceAll { funcName ->
            if (funcName.startsWith("static_")) "init"
            else funcName
        }
    }

    private fun removeStaticInitFromFunctionBodies() {
        gIndex.translatedMethods.removeIf { (sig, func) ->
            sig.name == STATIC_INIT || func.funcName.startsWith("static_")
        }
        for (func in gIndex.translatedMethods.values) {
            removeStaticInit(func.body)
        }
    }

    private fun removeStaticInit(instr1: ArrayList<Instruction>) {
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
                            instr1.subList(i - 2, i + 2).clear()
                            i -= 3 // -2 from prevInstr2, -1 from i++
                        } else {
                            instr1.removeAt(i)
                            i--
                        }
                    }
                }
                is IfBranch -> {
                    removeStaticInit(instr.ifTrue)
                    removeStaticInit(instr.ifFalse)
                }
                is LoopInstr -> {
                    removeStaticInit(instr.body)
                }
                is SwitchCase -> {
                    instr.cases.forEach { caseI ->
                        removeStaticInit(caseI)
                    }
                }
            }
            i++
        }
    }

}