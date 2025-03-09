package graphing

import canThrowError
import graphing.StructuralAnalysis.Companion.printState
import hierarchy.HierarchyIndex
import hierarchy.HierarchyIndex.methodAliases
import implementedMethods
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertFail
import me.anno.utils.assertions.assertTrue
import org.apache.logging.log4j.LogManager
import translator.GeneratorIndex
import translator.LocalVariableOrParam
import translator.MethodTranslator
import useWASMExceptions
import utils.Builder
import utils.MethodSig
import utils.WASMTypes.*
import utils.helperFunctions
import utils.ptrType
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
        val hasErrorRetType = canThrowError(sig) && !useWASMExceptions
        return sig.descriptor.getResultWASMTypes(hasErrorRetType)
    }

    private fun validateLocalVariables(localVarsWithParams: List<LocalVariableOrParam>):
            Pair<Map<String, String>, List<String>> {
        val localVars = HashMap<String, String>(localVarsWithParams.size)
        val params = ArrayList<String>()
        for (v in localVarsWithParams) {
            if (v.getter is LocalGet) {
                assertEquals(null, localVars.put(v.wasmName, v.wasmType)) {
                    "Duplicate variable ${v.wasmName}, ${v.wasmType}, ${v.descriptor}, ${v.index}"
                }
            } else {
                val index = (v.getter as ParamGet).index
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

    private fun ArrayList<String>.pop(value: String, context: String): ArrayList<String> {
        assertEquals(value, removeLastOrNull()) { "Error dropping $context" }
        return this
    }

    /**
     * relatively expensive validation function to check whether all stack-operations add up
     * */
    fun validateStack(nodes: List<GraphingNode>, mt: MethodTranslator) {
        val returnTypes = getReturnTypes(mt.sig)
        // println("Validating stack ${mt.sig} -> $returnTypes")
        for (node in nodes) {
            validateStack2(
                mt.sig, node.printer, node.inputStack,
                when (node) {
                    is ReturnNode -> returnTypes
                    is BranchNode -> node.outputStack + listOf(i32) // i32 = condition
                    else -> node.outputStack
                }, returnTypes,
                mt.variables.localVarsWithParams
            )
        }
    }

    fun validateStack2(
        sig: MethodSig, printer: Builder, params: List<String>,
        normalResults: List<String>, returnResults: List<String>,
        localVarsWithParams: List<LocalVariableOrParam>
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
        val print = false
        if (print) println("Validating stack $sig/$params -> $normalResults/$returnResults, $localVarTypes")
        val stack = ArrayList(params)
        for (i in instructions) {
            if (print && i !is IfBranch && i !is LoopInstr && i !is SwitchCase) {
                println("  $stack, $i")
            }
            when (i) {
                is LocalGet -> stack.push(
                    localVarTypes[i.name]
                        ?: throw IllegalStateException("Missing $i")
                )
                is LocalSet -> stack.pop(
                    localVarTypes[i.name]
                        ?: throw IllegalStateException("Missing $i"),
                    i.name
                )
                is ParamGet -> stack.push(paramsTypes[i.index])
                is ParamSet -> stack.pop(paramsTypes[i.index])
                is GlobalGet -> stack.push(i32)
                is GlobalSet -> stack.pop(i32)
                Return -> {
                    assertTrue(stack.endsWith(returnResults), "Stack incorrect, $returnResults vs $stack")
                    return // done :)
                }
                Unreachable -> {
                    return // done :)
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
                    val canThrow = !useWASMExceptions && canThrowError1(func)
                    val retTypes = getRetType(func, canThrow)
                    if (print) println("call ${i.name} -> $func -> $retTypes")
                    stack.addAll(retTypes)
                }
                is CallIndirect -> {
                    stack.pop(i32) // method pointer
                    val params1 = i.type.params
                    for (j in params1.lastIndex downTo 0) {
                        stack.pop(params1[j])
                    }
                    stack.addAll(i.type.results)
                }
                is BlockInstr -> {
                    // to do confirm Jump[If](BlockInstr) has correct stack
                    assertTrue(stack.endsWith(params))
                    validateStack3(
                        sig, i.body, stack, i.results, returnResults,
                        localVarTypes, paramsTypes
                    )
                    if (i.isReturning()) return // done
                }
                is LoopInstr -> {
                    validateStack3(
                        sig, i.body, stack, i.results, returnResults,
                        localVarTypes, paramsTypes
                    )
                    if (i.isReturning()) return // done
                }
                is Jump -> {
                    // todo check results match stack
                    return
                }
                is JumpIf -> {
                    stack.pop(i32)
                    // todo check results match stack
                }
                I32Load8U, I32Load8S, I32Load16U, I32Load16S, I32Load -> stack.pop(ptrType).push(i32)
                I64Load -> stack.pop(ptrType).push(i64)
                F32Load -> stack.pop(ptrType).push(f32)
                F64Load -> stack.pop(ptrType).push(f64)
                I32Store8, I32Store16, I32Store -> stack.pop(i32).pop(i32)
                I64Store -> stack.pop(i64).pop(ptrType)
                F32Store -> stack.pop(f32).pop(ptrType)
                F64Store -> stack.pop(f64).pop(ptrType)
                is SwitchCase -> {
                    assertTrue(stack.isEmpty())
                    for (case in i.cases) {
                        validateStack3(
                            sig, case, emptyList(), emptyList(), returnResults,
                            localVarTypes, paramsTypes
                        )
                    }
                }
                else -> throw NotImplementedError(i.toString())
            }
        }
        assertTrue(stack.endsWith(normalResults), "Stack incorrect, $normalResults vs $stack")
    }

    private fun getCallParams(func: Any): List<String> {
        return when (func) {
            is FunctionImpl -> func.params // helper function
            is MethodSig -> func.descriptor.wasmParams
            else -> throw NotImplementedError()
        }
    }

    private fun getRetType(func: Any, canThrow: Boolean): List<String> {
        return when (func) {
            is FunctionImpl -> func.results // helper function
            is MethodSig -> func.descriptor.getResultWASMTypes(canThrow)
            else -> assertFail()
        }
    }

    private fun hasSelfParam(func: Any): Boolean {
        return when (func) {
            is FunctionImpl -> false // helper function
            is MethodSig -> func !in HierarchyIndex.staticMethods
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

    private fun <V> List<V>.endsWith(end: List<V>): Boolean {
        val offset = size - end.size
        if (offset < 0) return false
        for (i in end.indices) {
            if (end[i] != this[i + offset]) return false
        }
        return true
    }

    fun validateInputOutputStacks(nodes: List<GraphingNode>, sig: MethodSig) {
        // check input- and output stacks
        val illegals = ArrayList<String>()
        for (node in nodes) {
            for (next in node.outputs) {
                val outputStack = node.outputStack
                if (next.inputStack != outputStack) {
                    illegals += ("$outputStack != ${next.inputStack}, ${node.index} -> ${next.index}")
                }
            }
        }
        if (illegals.isNotEmpty()) {
            printState(nodes, "Illegal")
            for (ill in illegals) {
                System.err.println(ill)
            }
            throw IllegalStateException("Illegal node in $sig")
        }
    }
}