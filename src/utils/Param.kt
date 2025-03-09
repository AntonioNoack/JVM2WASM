package utils

import me.anno.utils.structures.lists.Lists.createList

class Param(val wasmType: String, val name: String) {

    constructor(wasmType: String, index: Int) : this(wasmType, names[index])

    override fun equals(other: Any?): Boolean {
        return other is Param && other.wasmType == wasmType
    }

    override fun hashCode(): Int {
        return wasmType.hashCode()
    }

    companion object {
        val names = createList(100) { "p$it" }
        fun List<String>.toParams(): List<Param> {
            return mapIndexed { i, type -> Param(type, i) }
        }
    }
}