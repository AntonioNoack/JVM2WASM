package graphing

import canThrowError
import hierarchy.HierarchyIndex
import hierarchy.HierarchyIndex.methodAliases
import implementedMethods
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertNull
import me.anno.utils.assertions.assertTrue
import org.apache.logging.log4j.LogManager
import translator.GeneratorIndex
import translator.LocalVar
import translator.MethodTranslator
import useWASMExceptions
import utils.*
import wasm.instr.*
import wasm.instr.Instructions.Drop
import wasm.instr.Instructions.F32Load
import wasm.instr.Instructions.F32Store
import wasm.instr.Instructions.F64Load
import wasm.instr.Instructions.F64Store
import wasm.instr.Instructions.I32EQZ
import wasm.instr.Instructions.I32Load
import wasm.instr.Instructions.I32Load16S
import wasm.instr.Instructions.I32Load16U
import wasm.instr.Instructions.I32Load8S
import wasm.instr.Instructions.I32Load8U
import wasm.instr.Instructions.I32Store
import wasm.instr.Instructions.I32Store16
import wasm.instr.Instructions.I32Store8
import wasm.instr.Instructions.I64EQZ
import wasm.instr.Instructions.I64Load
import wasm.instr.Instructions.I64Store
import wasm.instr.Instructions.Return
import wasm.instr.Instructions.Unreachable
import wasm.parser.FunctionImpl

object StackValidator {

    private val LOGGER = LogManager.getLogger(StackValidator::class)

    fun getReturnTypes(sig: MethodSig): List<String> {
        val descriptor = sig.descriptor
        val ix = descriptor.lastIndexOf(')')
        val valueRetType = if (!descriptor.endsWith(")V")) {
            listOf(jvm2wasm1(descriptor[ix + 1]))
        } else emptyList()
        val hasErrorRetType = canThrowError(sig) && !useWASMExceptions
        val errorRetType = if (hasErrorRetType) listOf(ptrType) else emptyList()
        // println("ret params[$sig]: $valueRetType + $hasErrorRetType")
        return valueRetType + errorRetType
    }

    private fun validateLocalVariables(localVarsWithParams: List<LocalVar>):
            Pair<Map<String, String>, List<String>> {
        val localVars = HashMap<String, String>(localVarsWithParams.size)
        val params = ArrayList<String>()
        for (v in localVarsWithParams) {
            if (v.localGet is LocalGet) {
                assertNull(localVars.put(v.wasmName, v.wasmType))
            } else {
                val index = (v.localGet as ParamGet).index
                assertEquals(params.size, index)
                params.add(v.wasmType)
            }
        }
        return localVars to params
    }

    private fun ArrayList<String>.push(value: String): ArrayList<String> {
        add(value)
        return this
    }

    private fun ArrayList<String>.pop(value: String): ArrayList<String> {
        assertEquals(value, removeLastOrNull())
        return this
    }

    fun validateStack(nodes: List<Node>, sa: MethodTranslator) {
        val returnTypes = getReturnTypes(sa.sig)
        // println("Validating stack ${sa.sig} -> $returnTypes")
        for (node in nodes) {
            validateStack2(
                sa.sig, node.printer, node.inputStack,
                when {
                    node.isReturn -> returnTypes
                    node.isBranch -> node.outputStack + listOf(i32)
                    else -> node.outputStack
                }, returnTypes,
                sa.localVarsWithParams
            )
        }
    }

    fun validateStack2(
        sig: MethodSig, printer: Builder, params: List<String>,
        normalResults: List<String>, returnResults: List<String>,
        localVarsWithParams: List<LocalVar>
    ) {
        val (localVarTypes, paramsTypes) = validateLocalVariables(localVarsWithParams)
        validateStack3(sig, printer.instrs, params, normalResults, returnResults, localVarTypes, paramsTypes)
    }

    private fun validateStack3(
        sig: MethodSig,
        instructions: List<Instruction>, params: List<String>,
        normalResults: List<String>, returnResults: List<String>,
        localVarTypes: Map<String, String>, paramsTypes: List<String>,
    ) {
        // println("Validating stack ${sig.name}/$params -> $normalResults/$returnResults")
        val stack = ArrayList(params)
        for (i in instructions) {
            // println("  $stack, $i")
            when (i) {
                is LocalGet -> stack.push(
                    localVarTypes[i.name]
                        ?: throw IllegalStateException("Missing $i")
                )
                is LocalSet -> stack.pop(
                    localVarTypes[i.name]
                        ?: throw IllegalStateException("Missing $i")
                )
                is ParamGet -> stack.push(paramsTypes[i.index])
                is ParamSet -> stack.pop(paramsTypes[i.index])
                is GlobalGet -> stack.push(i32)
                is GlobalSet -> stack.pop(i32)
                Return -> {
                    assertTrue(stack.endsWith(returnResults), "Stack incorrect, $returnResults vs $stack")
                    // println()
                    // done :)
                    return
                }
                Unreachable -> {
                    // println()
                    // done :)
                    return
                }
                is IfBranch -> {
                    stack.pop(i32)
                    for (param in i.params.reversed()) {
                        stack.pop(param)
                    }
                    validateStack3(sig, i.ifTrue, i.params, i.results, returnResults, localVarTypes, paramsTypes)
                    validateStack3(sig, i.ifFalse, i.params, i.results, returnResults, localVarTypes, paramsTypes)
                    if (i.isReturning()) {
                        // done :)
                        return
                    } else {
                        stack.addAll(i.results)
                    }
                }
                is Const -> stack.push(i.type.wasmType)
                is Comment -> {} // ignored
                is UnaryInstruction -> stack.pop(i.type).push(i.type)
                is BinaryInstruction -> stack.pop(i.type).pop(i.type).push(i.type)
                is CompareInstr -> stack.pop(i.type).pop(i.type).push(i32)
                is ShiftInstr -> stack.pop(i.type).pop(i.type).push(i.type)
                I32EQZ -> stack.pop(i32).push(i32)
                I64EQZ -> stack.pop(i64).push(i32)
                Drop -> stack.removeLast()
                is NumberCastInstruction -> stack.pop(i.popType).push(i.pushType)
                is Call -> {
                    // println("Calling $i on $stack")
                    val func =
                        methodAliases[i.name]
                            ?: implementedMethods[i.name]
                            ?: helperFunctions[i.name]
                            ?: GeneratorIndex.nthGetterMethods.values.firstOrNull { it.funcName == i.name }
                    if (func == null) {
                        LOGGER.warn("Missing ${i.name}, skipping validation")
                        return
                    }
                    // drop all arguments in reverse order
                    val callParams = getCallParams(func)
                    for (j in callParams.lastIndex downTo 0) { // last one is return type
                        stack.pop(callParams[j])
                    }
                    // drop "self"
                    if (hasSelfParam(func)) {
                        stack.pop(ptrType)
                    }
                    // push return values
                    stack.addAll(getRetType(func))
                    // push error
                    if (!useWASMExceptions && canThrowError1(func)) {
                        stack.push(ptrType)
                    }
                }
                is CallIndirect -> {
                    stack.pop(i32) // method pointer
                    val params1 = i.type.params
                    for (j in params1.lastIndex downTo 0) {
                        stack.pop(params1[j])
                    }
                    stack.addAll(i.type.results)
                }
                is LoopInstr -> {
                    validateStack3(
                        sig, i.body, stack, i.results, returnResults,
                        localVarTypes, paramsTypes
                    )
                    if (i.isReturning()) return // done
                }
                is Jump -> return
                is JumpIf -> stack.pop(i32)
                I32Load8U, I32Load8S, I32Load16U, I32Load16S, I32Load -> stack.pop(ptrType).push(i32)
                I64Load -> stack.pop(ptrType).push(i64)
                F32Load -> stack.pop(ptrType).push(f32)
                F64Load -> stack.pop(ptrType).push(f64)
                I32Store8, I32Store16, I32Store -> stack.pop(i32).pop(i32)
                I64Store -> stack.pop(i64).pop(ptrType)
                F32Store -> stack.pop(f32).pop(ptrType)
                F64Store -> stack.pop(f64).pop(ptrType)
                else -> throw NotImplementedError(i.toString())
            }
        }
        assertTrue(stack.endsWith(normalResults), "Stack incorrect, $normalResults vs $stack")
        // println()
    }

    private fun getCallParams(func: Any): List<String> {
        return when (func) {
            is FunctionImpl -> func.params // helper function
            is MethodSig -> split3(func.descriptor).map { jvm2wasm(it) }
            else -> throw NotImplementedError()
        }
    }

    private fun getRetType(func: Any): List<String> {
        return when (func) {
            is FunctionImpl -> func.results // helper function
            is MethodSig -> {
                val descriptor = func.descriptor
                val ix = descriptor.lastIndexOf(')')
                if (!descriptor.endsWith(")V")) {
                    listOf(jvm2wasm1(descriptor[ix + 1]))
                } else emptyList()
            }
            else -> throw NotImplementedError()
        }
    }

    private fun hasSelfParam(func: Any): Boolean {
        return when (func) {
            is FunctionImpl -> false // helper function
            is MethodSig -> func !in HierarchyIndex.staticMethods
            else -> throw NotImplementedError()
        }
    }

    private fun canThrowError1(func: Any): Boolean {
        return when (func) {
            is FunctionImpl -> false // helper function
            is MethodSig -> canThrowError(func)
            else -> throw NotImplementedError()
        }
    }

    private fun <V> List<V>.endsWith(end: List<V>): Boolean {
        val offset = size - end.size
        if (offset < 0) return false
        for (i in end.indices) {
            if (end[i] != this[i + offset]) return false
        }
        return true
    }
}