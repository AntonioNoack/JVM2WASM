package utils

import me.anno.utils.structures.lists.Lists.createList
import translator.JavaTypes.convertTypeToWASM
import utils.WASMTypes.isWASMType

class Param(val name: String, val jvmType: String, val wasmType: WASMType) {

    override fun toString(): String {
        return "Param('$name', $jvmType, $wasmType)"
    }

    companion object {
        val names = createList(100) { "p$it" }

        fun getSampleJVMType(type: WASMType): String {
            return when (type) {
                WASMType.I32 -> "int"
                WASMType.I64 -> "long"
                WASMType.F32 -> "float"
                WASMType.F64 -> "double"
            }
        }

        fun List<WASMType>.toParams2(): List<Param> {
            return mapIndexed { i, type ->
                Param(names[i], getSampleJVMType(type), type)
            }
        }

        fun List<String>.toParams(): List<Param> {
            return mapIndexed { i, type ->
                if (isWASMType(type)) {
                    val wasmType = WASMType.find(type)
                    Param(names[i], getSampleJVMType(wasmType), wasmType)
                } else Param(names[i], type, convertTypeToWASM(type))
            }
        }
    }
}