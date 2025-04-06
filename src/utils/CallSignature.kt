package utils

import canThrowError
import crashOnAllExceptions
import hierarchy.HierarchyIndex.isStatic
import me.anno.utils.assertions.assertFail
import me.anno.utils.types.Booleans.toInt
import wasm.instr.FuncType

/**
 * used for implementing runtime java.lang.reflect.Method.invoke()
 * */
data class CallSignature(val params: List<CallType>, val returnType: CallType?, val canThrow: Boolean) {

    fun toFuncType(): FuncType {
        val results = if (returnType != null) listOf(returnType.toWASM()) else emptyList()
        val throws = if (canThrow) listOf(ptrTypeI) else emptyList()
        return FuncType(params.map { it.toWASM() }, results + throws)
    }

    fun format(): String {
        val builder = StringBuilder2(params.size + 1 + canThrow.toInt())
        for (i in params.indices) {
            builder.append(params[i].symbol)
        }
        builder.append(returnType?.symbol ?: 'V')
        if (canThrow) builder.append('X')
        return builder.toString()
    }

    companion object {
        fun c(method: MethodSig, removeLastParam: Boolean = false): CallSignature {
            val isStatic = isStatic(method)
            val self = if (!isStatic) listOf(CallType.POINTER) else emptyList()
            val canThrow = !crashOnAllExceptions && canThrowError(method)
            val descriptor = method.descriptor
            val numParams = descriptor.params.size - removeLastParam.toInt()
            val params = descriptor.params.subList(0, numParams).map(::getCallType)
            val returnType = if (descriptor.returnType != null) getCallType(descriptor.returnType) else null
            return CallSignature(self + params, returnType, canThrow)
        }

        fun getCallType(clazz: String): CallType {
            return when (clazz) {
                "boolean", "byte", "short", "int", "char" -> CallType.I32
                "long" -> CallType.I64
                "float" -> CallType.F32
                "double" -> CallType.F64
                "void" -> assertFail()
                else -> CallType.POINTER
            }
        }
    }
}
