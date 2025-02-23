package wasm.instr

import me.anno.utils.structures.lists.Lists.createList

class ParamSet private constructor(val index: Int) : Instruction {
    val name = "p$index"
    override fun toString(): String = "local.set $index"

    companion object {
        private val values = createList(100) { ParamSet(it) }
        operator fun get(idx: Int) = values[idx]
    }
}