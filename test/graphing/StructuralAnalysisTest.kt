package graphing

import graphing.StackValidator.validateInputOutputStacks
import graphing.StackValidator.validateStack
import me.anno.utils.assertions.assertNotEquals
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.lists.Lists.createList
import org.junit.jupiter.api.Test
import translator.MethodTranslator
import translator.TranslatorNode
import utils.Descriptor
import utils.Param
import utils.WASMType
import utils.helperMethods
import wasm.instr.Call
import wasm.instr.Const.Companion.i32Const0
import wasm.instr.Instructions.Return
import wasm.parser.FunctionImpl

class StructuralAnalysisTest {

    @Test
    fun testStructuralAnalysisSupportsAllCases() {

        // todo why is this failing???

        helperMethods.put(
            "panic",
            FunctionImpl(
                "panic", listOf(Param("i", "java/lang/Object", WASMType.I32)), emptyList(),
                emptyList(), arrayListOf(Return), false
            )
        )

        val numNodes = 5
        val maxNumConnections = numNodes * (numNodes - 1)
        assertTrue(maxNumConnections < 64)

        val translator = MethodTranslator(0, "Class", "name", Descriptor.voidDescriptor)
        val varGetter = translator.variables.addLocalVariable("tmp", WASMType.I32, "boolean")

        graphs@ for (connectionBits in 0 until (1L shl maxNumConnections)) {
            translator.isLookingAtSpecial = connectionBits >= 5600
            if (translator.isLookingAtSpecial) {
                println("Bits: ${connectionBits.toString(2)} ($connectionBits)")
            }

            val nodes0 = createList(numNodes) {
                TranslatorNode(it).apply {
                    printer.append(i32Const0)
                    printer.append(Call.panic)
                }
            }

            var bits = connectionBits
            while (bits != 0L) {
                val highestBit = bits.countTrailingZeroBits()
                assertNotEquals(64, highestBit)

                val from = highestBit % numNodes
                val to0 = highestBit / numNodes
                val to = if (to0 < from) to0 else to0 + 1

                val fromNode = nodes0[from]
                if (fromNode.ifTrue == -1) {
                    fromNode.ifTrue = to
                    fromNode.isAlwaysTrue = true
                } else {
                    if (fromNode.ifFalse == null) {
                        fromNode.printer.append(varGetter.getter)
                    } else {
                        // isomorphic graph was probably already processed
                        continue@graphs
                    }
                    fromNode.isAlwaysTrue = false
                    fromNode.ifFalse = nodes0[to]
                }

                bits = bits xor (1L shl highestBit)
            }

            for (node in nodes0) {
                if (node.next == -1) {
                    node.isReturn = true
                    node.printer.append(Return)
                }
            }

            val nodes1 = TranslatorNode.convertNodes(nodes0)
            validateInputOutputStacks(nodes1, translator.sig)
            validateStack(nodes1, translator)

            StructuralAnalysis(translator, nodes1).joinNodes()

        }
    }

}