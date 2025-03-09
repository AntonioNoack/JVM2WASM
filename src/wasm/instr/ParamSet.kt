package wasm.instr

import me.anno.utils.structures.lists.Lists.createList
import utils.Param.Companion.names

class ParamSet(val index: Int, var name: String) : Instruction {

    override fun toString(): String = "local.set $index"

    override fun hashCode(): Int {
        return index.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is ParamSet && other.index == index
    }

    companion object {
        private val values = createList(100) { ParamSet(it, names[it]) }
        operator fun get(idx: Int) = values[idx]
    }
}