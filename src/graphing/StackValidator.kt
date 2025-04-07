package graphing

import canThrowError
import graphing.StackCallUtils.findCallByName
import graphing.StackCallUtils.getCallParams
import graphing.StackCallUtils.getCallResults
import graphing.StackCallUtils.hasSelfParam
import graphing.StructuralAnalysis.Companion.printState
import graphing.StructuralAnalysis.Companion.printState2
import highlevel.HighLevelInstruction
import highlevel.PtrDupInstr
import me.anno.utils.OS
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertTrue
import org.apache.logging.log4j.LogManager
import translator.JavaTypes.convertTypeToWASM
import translator.JavaTypes.i32
import translator.JavaTypes.popType
import translator.JavaTypes.ptrType
import translator.JavaTypes.pushType
import translator.JavaTypes.typeListEquals
import translator.LocalVariableOrParam
import translator.MethodTranslator
import translator.MethodTranslator.Companion.isLookingAtSpecial
import useWASMExceptions
import utils.Builder
import utils.MethodSig
import utils.StringBuilder2
import utils.WASMType
import wasm.instr.*
import wasm.instr.Instructions.Return
import wasm.instr.Instructions.Unreachable

object StackValidator {

    private val LOGGER = LogManager.getLogger(StackValidator::class)

    fun getReturnTypes(sig: MethodSig): List<String> {
        val hasErrorRetType = canThrowError(sig) && !useWASMExceptions
        return sig.descriptor.getResultTypes(hasErrorRetType)
    }

    private fun validateLocalVariables(localVarsWithParams: List<LocalVariableOrParam>):
            Pair<Map<String, String>, List<String>> {
        val localVars = HashMap<String, String>(localVarsWithParams.size)
        val params = ArrayList<String>()
        for (v in localVarsWithParams) {
            if (v.isParam) {
                val index = (v.getter as ParamGet).index
                assertEquals(params.size, index)
                params.add(v.jvmType)
            } else {
                assertEquals(null, localVars.put(v.name, v.jvmType)) {
                    "Duplicate variable ${v.name}, ${v.wasmType}, ${v.jvmType}, ${v.index}"
                }
            }
        }
        return localVars to params
    }

    private fun ArrayList<String>.push(type: String): ArrayList<String> {
        pushType(type)
        return this
    }

    private fun ArrayList<String>.pop(expectedType: String): ArrayList<String> {
        popType(expectedType)
        return this
    }

    private fun ArrayList<String>.pop(expectedType: WASMType): ArrayList<String> {
        popType(expectedType.wasmName)
        return this
    }

    /**
     * relatively expensive validation function
     * */
    fun validateNodes1(nodes: List<GraphingNode>, mt: MethodTranslator) {
        validateInputs(nodes)
        validateInputOutputStacks(nodes, mt.sig)
        validateStack(nodes, mt)
    }

    /**
     * relatively expensive validation function to check whether all stack-operations add up
     * */
    fun validateStack(nodes: List<GraphingNode>, mt: MethodTranslator) {
        val returnTypes = getReturnTypes(mt.sig)
        // println("Validating stack ${mt.sig} -> $returnTypes")
        for (node in nodes) {
            try {
                validateStack2(
                    mt.sig, node.printer, node.inputStack,
                    when (node) {
                        is ReturnNode -> returnTypes
                        is BranchNode -> node.outputStack + listOf(i32) // i32 = condition
                        else -> node.outputStack
                    }, returnTypes,
                    mt.variables.localVarsAndParams
                )
            } catch (e: IllegalStateException) {
                val builder = StringBuilder2()
                printState2(nodes) { line -> builder.append(line).append('\n') }
                OS.desktop.getChild("stack-crash.txt")
                    .writeText(builder.toString())
                throw IllegalStateException("Error in $node", e)
            }
        }
    }

    fun validateStack2(
        sig: MethodSig, printer: Builder, params: List<String>,
        normalResults: List<String>, returnResults: List<String>,
        localVarsWithParams: List<LocalVariableOrParam>
    ) {
        val (localVarTypes, paramsTypes) = validateLocalVariables(localVarsWithParams)
        validateStack3(sig, printer.instrs, params, normalResults, returnResults, localVarTypes, paramsTypes, 0)
    }

    private fun validateStack3(
        sig: MethodSig,
        instructions: List<Instruction>, params: List<String>,
        normalResults: List<String>, returnResults: List<String>,
        localVarTypes: Map<String, String>, paramsTypes: List<String>,
        depth: Int,
    ) {
        try {
            validateStack3Impl(
                sig, instructions, params, normalResults, returnResults,
                localVarTypes, paramsTypes, depth, false
            )
        } catch (e: IllegalStateException) {
            try {
                // print where we crash
                validateStack3Impl(
                    sig, instructions, params, normalResults, returnResults,
                    localVarTypes, paramsTypes, depth, true
                )
            } catch (ignored: IllegalStateException) {
            }

            val prefix = "ValidateStack3"
            if ((e.message ?: "").startsWith(prefix)) throw e
            val builder = StringBuilder2()
            builder.append(instructions.joinToString("\n"))
            OS.desktop.getChild("stack-crash-instr-$depth.txt")
                .writeText(builder.toString())
            throw IllegalStateException("${e.message}[$params -> $normalResults/$returnResults]", e)
        }
    }

    private fun validateStack3Impl(
        sig: MethodSig,
        instructions: List<Instruction>, params: List<String>,
        normalResults: List<String>, returnResults: List<String>,
        localVarTypes: Map<String, String>, paramsTypes: List<String>,
        depth: Int, allowPrinting: Boolean
    ) {
        val print = allowPrinting && isLookingAtSpecial(sig)
        if (print) println("${"  ".repeat(depth)}Validating stack[${instructions.size}] $sig/$params -> $normalResults/$returnResults, $localVarTypes")
        val stack = ArrayList(params)
        for (i in instructions.indices) {
            val instr = instructions[i]
            if (print) {
                when (instr) {
                    is IfBranch -> println("${"  ".repeat(depth)}  [$i] $stack, if(${instr.params}) -> ${instr.results}")
                    is LoopInstr -> println("${"  ".repeat(depth)}  [$i] $stack, loop(${instr.params}) -> ${instr.results}")
                    else -> println("${"  ".repeat(depth)}  [$i] $stack, $instr")
                }
            }
            try {
                val hasReturned = validateStackStep(
                    sig, params, returnResults,
                    localVarTypes, paramsTypes,
                    stack, print, i, instr, depth
                )
                if (hasReturned) return
            } catch (e: Exception) {
                throw IllegalStateException("Exception at [$i]", e)
            }
        }
        // assertEquals(normalResults, stack,  "Stack incorrect, $normalResults vs $stack")
        assertTrue(stack.endsWith(normalResults), "Stack incorrect, $normalResults vs $stack")
    }

    private fun validateStackStep(
        sig: MethodSig, params: List<String>, returnResults: List<String>,
        localVarTypes: Map<String, String>, paramsTypes: List<String>,
        stack: ArrayList<String>, print: Boolean, i: Int, instr: Instruction, depth: Int,
    ): Boolean {
        when (instr) {
            is LocalGet -> stack.push(
                localVarTypes[instr.name]
                    ?: throw IllegalStateException("Missing $instr")
            )
            is LocalSet -> stack.pop(
                localVarTypes[instr.name]
                    ?: throw IllegalStateException("Missing $instr")
            )
            is ParamGet -> stack.push(paramsTypes[instr.index])
            is ParamSet -> stack.pop(paramsTypes[instr.index])
            is GlobalGet -> stack.push(i32)
            is GlobalSet -> stack.pop(i32)
            Return -> {
                assertTrue(stack.endsWith(returnResults), "Stack incorrect, $returnResults vs $stack")
                return true// done :)
            }
            Unreachable -> {
                return true// done :)
            }
            is IfBranch -> {
                stack.pop(i32)
                for (param in instr.params.reversed()) {
                    stack.pop(param)
                }
                validateStack3(
                    sig, instr.ifTrue, instr.params, instr.results,
                    returnResults, localVarTypes, paramsTypes, depth + 1
                )
                validateStack3(
                    sig, instr.ifFalse, instr.params, instr.results,
                    returnResults, localVarTypes, paramsTypes, depth + 1
                )
                if (instr.isReturning()) {
                    // done :)
                    return true
                } else {
                    stack.addAll(instr.results)
                }
            }
            is Const -> stack.push(instr.type.wasmName)
            is Comment -> {} // ignored
            is UnaryInstruction -> stack.pop(instr.popType).push(instr.pushType)
            is BinaryInstruction -> stack.pop(instr.popType).pop(instr.popType).push(instr.pushType)
            Drop -> stack.removeLast()
            is Call -> {
                // println("Calling $i on $stack")
                val func = findCallByName(instr.name)
                if (func == null) {
                    LOGGER.warn("Missing ${instr.name}, skipping validation")
                    return true
                }
                // drop all arguments in reverse order
                val callParams = getCallParams(func)
                for (callParam in callParams.reversed()) { // last one is return type
                    stack.pop(callParam)
                }
                // drop "self"
                if (hasSelfParam(func)) {
                    stack.pop(ptrType)
                }
                // push return values
                val retTypes = getCallResults(func)
                if (print) println("${"  ".repeat(depth + 2)}call(${callParams.joinToString()}) -> $retTypes")
                stack.addAll(retTypes)
            }
            is CallIndirect -> {
                stack.pop(i32) // pop method pointer
                val params1 = instr.type.params
                for (k in params1.lastIndex downTo 0) {
                    stack.pop(params1[k])
                }
                for (resultType in instr.type.results) {
                    stack.add(resultType.wasmName)
                }
            }
            is LoopInstr -> {
                assertTrue(instr.params.isEmpty())
                validateStack3(
                    sig, instr.body, stack, instr.results, returnResults,
                    localVarTypes, paramsTypes, depth + 1
                )
                stack.addAll(instr.results)
                if (instr.isReturning()) return true// done
            }
            is Jump -> {
                // todo check results match stack
                return true
            }
            is JumpIf -> {
                stack.pop(i32)
                // todo check results match stack
            }
            is StoreInstr -> stack.pop(instr.wasmType).pop(ptrType)
            PtrDupInstr -> stack.pop(ptrType).push(ptrType).push(ptrType)
            is HighLevelInstruction -> {
                for (lowInstr in instr.toLowLevel()) {
                    val returning = validateStackStep(
                        sig, params, returnResults, localVarTypes,
                        paramsTypes, stack, print, i, lowInstr, depth
                    )
                    if (returning) return true
                }
            }
            else -> throw NotImplementedError(instr.toString())
        }
        return false
    }

    fun List<String>.endsWith(end: List<String>): Boolean {
        val offset = size - end.size
        if (offset < 0) return false
        for (i in end.indices) {
            if (convertTypeToWASM(end[i]) != convertTypeToWASM(this[i + offset])) return false
        }
        return true
    }

    fun validateInputOutputStacks(nodes: List<GraphingNode>, sig: MethodSig) {
        // check input- and output stacks
        val illegals = ArrayList<String>()
        for (node in nodes) {
            for (next in node.outputs) {
                val outputStack = node.outputStack
                if (!typeListEquals(next.inputStack, outputStack)) {
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

    private fun validateInputs(nodes: List<GraphingNode>) {
        for (node in nodes) {
            for (output in node.outputs) {
                assertTrue(node in output.inputs) {
                    "Missing $node in $output.inputs"
                }
            }
        }
    }
}