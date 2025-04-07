package graphing

import translator.MethodTranslator
import translator.MethodTranslator.Companion.comments
import utils.Builder

object LoadStoreStack {
    fun loadStackPrepend(inputs: List<String>, printer: Builder, mt: MethodTranslator, offset: Int = 0) {
        if (inputs.isEmpty()) return
        val pre = Builder(inputs.size)
        if (comments) pre.comment("load stack $inputs")
        for (idx in inputs.indices) {
            val jvmType = inputs[idx]
            pre.append(mt.variables.getStackVarName(idx+offset, jvmType).getter)
        }
        printer.prepend(pre)
    }

    fun storeStackAppend(outputs: List<String>, printer: Builder, mt: MethodTranslator, offset: Int = 0) {
        if (outputs.isEmpty()) return
        if (comments) printer.comment("store stack $outputs")
        for ((idx, jvmType) in outputs.withIndex().reversed()) {
            printer.append(mt.variables.getStackVarName(idx+offset, jvmType).setter)
        }
    }
}