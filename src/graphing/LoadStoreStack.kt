package graphing

import translator.MethodTranslator
import translator.MethodTranslator.Companion.comments
import utils.Builder

object LoadStoreStack {

    fun loadStackPrepend(node: GraphingNode, mt: MethodTranslator) {
        loadStackPrepend(node.inputStack, node.printer, mt)
    }

    fun loadStackPrepend(inputs: List<String>, printer: Builder, mt: MethodTranslator) {
        if (inputs.isEmpty()) return
        val pre = Builder()
        if (comments) pre.comment("load stack $inputs")
        for (idx in inputs.indices) {
            val jvmType = inputs[idx]
            pre.append(mt.variables.getStackVarName(idx, jvmType).getter)
        }
        printer.prepend(pre)
    }

    fun storeStackAppend(node: GraphingNode, mt: MethodTranslator) {
        storeStackAppend(node.outputStack, node.printer, mt)
    }

    fun storeStackAppend(outputs: List<String>, printer: Builder, mt: MethodTranslator) {
        if (outputs.isEmpty()) return
        if (comments) printer.comment("store stack $outputs")
        for ((idx, jvmType) in outputs.withIndex().reversed()) {
            printer.append(mt.variables.getStackVarName(idx, jvmType).setter)
        }
    }
}