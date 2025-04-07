package graphing

import canThrowError
import hIndex
import hierarchy.HierarchyIndex.methodAliases
import implementedMethods
import me.anno.utils.assertions.assertFail
import translator.GeneratorIndex
import useWASMExceptions
import utils.MethodSig
import utils.helperFunctions
import wasm.parser.FunctionImpl

object StackCallUtils {
    fun findCallByName(name: String): Any? {
        return methodAliases[name]
            ?: implementedMethods[name]
            ?: helperFunctions[name]
            ?: GeneratorIndex.nthGetterMethods.values.firstOrNull { it.funcName == name }
    }

    fun getCallParams(func: Any): List<String> {
        return when (func) {
            is FunctionImpl -> func.params.map { it.jvmType } // helper function
            is MethodSig -> func.descriptor.params
            else -> throw NotImplementedError()
        }
    }

    fun hasSelfParam(func: Any): Boolean {
        return when (func) {
            is FunctionImpl -> false // helper function
            is MethodSig -> !hIndex.isStatic(func)
            else -> assertFail()
        }
    }

    fun getCallResults(func: Any): List<String> {
        val canThrow = !useWASMExceptions && canThrowError1(func)
        return when (func) {
            is FunctionImpl -> func.results // helper function
            is MethodSig -> func.descriptor.getResultTypes(canThrow)
            else -> assertFail()
        }
    }

    private fun canThrowError1(func: Any): Boolean {
        return when (func) {
            is FunctionImpl -> false // helper function
            is MethodSig -> canThrowError(func)
            else -> assertFail()
        }
    }
}