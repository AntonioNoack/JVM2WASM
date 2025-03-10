package wasm.instr

import interpreter.WASMEngine
import me.anno.utils.structures.lists.Lists.createList
import utils.Param.Companion.names

class ParamGet(val index: Int, var name: String) : Instruction {

    override fun toString(): String = "local.get $index"

    override fun hashCode(): Int {
        return index.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is ParamGet && other.index == index
    }

    override fun execute(engine: WASMEngine): String? {
        engine.push(engine.getParam(index))
        return null
    }

    companion object {
        private val values = createList(100) { ParamGet(it, names[it]) }
        operator fun get(idx: Int) = values[idx]
    }
}