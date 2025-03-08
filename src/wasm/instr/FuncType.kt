package wasm.instr

import me.anno.utils.assertions.assertFail
import utils.*

data class FuncType(val params: List<String>, val results: List<String>) {

    companion object {
        fun parse(type: String): FuncType {
            val i = type.indexOf('X')
            return FuncType(
                (0 until i).map { getCharType(type[it], type) },
                (i + 1 until type.length).map { getCharType(type[it], type) }
            )
        }

        private fun getCharType(type: Char, typeStr: String): String {
            return when (type) {
                'i' -> i32
                'l' -> i64
                'f' -> f32
                'd' -> f64
                else -> assertFail(typeStr)
            }
        }

        fun getTypeChar(type: String): Char {
            return when (type) {
                i32 -> 'i'
                i64 -> 'l'
                f32 -> 'f'
                f64 -> 'd'
                else -> assertFail(type)
            }
        }
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