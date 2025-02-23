package wasm.instr

import me.anno.utils.structures.lists.Lists.createList

class ParamGet private constructor(val index: Int) : Instruction {
    val name = "p$index"
    override fun toString(): String = "local.get $index"

    companion object {
        private val values = createList(100) { ParamGet(it) }
        operator fun get(idx: Int) = values[idx]
    }
}