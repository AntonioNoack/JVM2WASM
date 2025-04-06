package translator

import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertFalse
import org.apache.logging.log4j.LogManager
import utils.WASMType
import utils.WASMTypes
import utils.is32Bits

object JavaTypes {

    private val LOGGER = LogManager.getLogger(JavaTypes::class)

    val useJavaTypes = true

    val i32 = if (useJavaTypes) "int" else WASMTypes.i32
    val i64 = if (useJavaTypes) "long" else WASMTypes.i64
    val f32 = if (useJavaTypes) "float" else WASMTypes.f32
    val f64 = if (useJavaTypes) "double" else WASMTypes.f64
    val ptrType = if (useJavaTypes) "java/lang/Object" else utils.ptrType

    fun convertTypeToWASM(type: String): WASMType {
        return when (type) {
            WASMTypes.i32, WASMTypes.i64, WASMTypes.f32, WASMTypes.f64 -> {
                if (useJavaTypes) LOGGER.warn("Type is already WASM $type")
                return WASMType.find(type)
            }
            "int", "byte", "boolean", "char", "short" -> WASMType.I32
            "long" -> WASMType.I64
            "float" -> WASMType.F32
            "double" -> WASMType.F64
            else -> if (is32Bits) WASMType.I32 else WASMType.I64
        }
    }

    fun ArrayList<String>.poppushType(expectedType: String): ArrayList<String> {
        // ensure we got the correct type
        assertFalse(isEmpty()) { "Expected $expectedType, but stack was empty" }
        assertEquals(convertTypeToWASM(expectedType), convertTypeToWASM(last()))
        return this
    }

    fun ArrayList<String>.popType(expectedType: String): ArrayList<String> {
        // ensure we got the correct type
        assertFalse(isEmpty()) { "Expected $expectedType, but stack was empty" }
        assertEquals(convertTypeToWASM(expectedType), convertTypeToWASM(removeLast()))
        return this
    }

    fun ArrayList<String>.pushType(type: String): ArrayList<String> {
        val isWASMType = type == "i32" || type == "i64" || type == "f32" || type == "f64"
        if (useJavaTypes == isWASMType) {
            LOGGER.warn(type)
        }
        add(type)
        return this
    }

    fun typeListEquals(a: List<String>, b: List<String>): Boolean {
        return a.size == b.size &&
                a.indices.all { convertTypeToWASM(a[it]) == convertTypeToWASM(b[it]) }
    }
}