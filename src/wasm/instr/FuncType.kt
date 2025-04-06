package wasm.instr

import me.anno.utils.assertions.assertFail
import translator.JavaTypes
import utils.StringBuilder2
import utils.WASMType
import utils.WASMTypes.*

data class FuncType(val params: List<WASMType>, val results: List<WASMType>) {

    constructor(params: List<String>, results: List<String>, unused: Unit) :
            this(
                params.map { JavaTypes.convertTypeToWASM(it) },
                results.map { JavaTypes.convertTypeToWASM(it) })

    companion object {
        fun parse(type: String): FuncType {
            val i = type.indexOf('X')
            return FuncType(
                (0 until i).map { getCharType(type[it], type) },
                (i + 1 until type.length).map { getCharType(type[it], type) }
            )
        }

        private fun getCharType(type: Char, typeStr: String): WASMType {
            return when (type) {
                'i' -> WASMType.I32
                'l' -> WASMType.I64
                'f' -> WASMType.F32
                'd' -> WASMType.F64
                else -> assertFail(typeStr)
            }
        }

        fun getTypeChar(type: WASMType): Char {
            return when (type) {
                WASMType.I32 -> 'i'
                WASMType.I64 -> 'l'
                WASMType.F32 -> 'f'
                WASMType.F64 -> 'd'
                else -> assertFail(type.wasmName)
            }
        }
    }

    override fun toString(): String {
        val tmp = StringBuilder2()
        toString(tmp)
        return tmp.toString()
    }

    fun toString(dst: StringBuilder2) {
        for (param in params) {
            dst.append(getTypeChar(param))
        }
        dst.append('X')
        for (result in results) {
            dst.append(getTypeChar(result))
        }
    }
}