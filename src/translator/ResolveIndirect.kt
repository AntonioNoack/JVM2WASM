package translator

import canThrowError
import dIndex
import dependency.ActuallyUsedIndex
import gIndex
import hIndex
import ignoreNonCriticalNullPointers
import me.anno.utils.types.Booleans.toInt
import org.apache.logging.log4j.LogManager
import utils.*
import wasm.instr.*
import wasm.instr.Const.Companion.i32Const
import wasm.instr.Const.Companion.i32Const0
import wasm.instr.Instructions.I32EQ
import wasm.instr.Instructions.I32Or
import wasm.instr.Instructions.Return
import wasm.instr.Instructions.Unreachable
import wasm.parser.FunctionImpl
import wasm.parser.LocalVariable

object ResolveIndirect {

    private val LOGGER = LogManager.getLogger(ResolveIndirect::class)

    private const val maxOptions = 16

    private fun findAllConstructableChildren(clazz0: String): HashSet<String> {
        val allChildren = HashSet<String>()
        fun addChildren(clazz: String) {
            if (!hIndex.isAbstractClass(clazz)) {
                allChildren.add(clazz)
            }
            val children = hIndex.childClasses[clazz] ?: return
            for (child in children) {
                if (child in dIndex.constructableClasses) {
                    addChildren(child)
                }
            }
        }
        addChildren(clazz0)
        return allChildren
    }

    private fun Builder.fixThrowable(calledCanThrow: Boolean, sigJ: MethodSig) {
        if (calledCanThrow != canThrowError(sigJ)) {
            if (calledCanThrow) {
                append(i32Const0)
            } else {
                append(Call("panic"))
            }
        }
    }

    private fun MethodTranslator.callSingleOption(
        sigJ: MethodSig, splitArgs: List<String>, ret: String,
        owner: String, getCaller: (Builder) -> Unit,
        sig0: MethodSig, calledCanThrow: Boolean
    ) {
        stackPush()
        if (!ignoreNonCriticalNullPointers) {
            checkNotNull0(owner, name, getCaller)
        }
        printer.comment("single for $sig0 -> $sigJ")
        ActuallyUsedIndex.add(this.sig, sigJ)
        printer.append(Call(methodName(sigJ)))
        printer.fixThrowable(calledCanThrow, sigJ)
        pop(splitArgs, false, ret)
        stackPop()
    }

    fun MethodTranslator.resolveIndirect(
        sig0: MethodSig, splitArgs: List<String>, ret: String,
        options: Set<MethodSig>, getCaller: (Builder) -> Unit,
        calledCanThrow: Boolean,
        owner: String
    ): Boolean {
        if (options.size == 1) {
            callSingleOption(options.first(), splitArgs, ret, owner, getCaller, sig0, calledCanThrow)
            return true
        } else if (options.size < maxOptions) {
            return resolveIndirectTree(sig0, splitArgs, ret, options, getCaller, calledCanThrow, owner)
        } else return false
    }

    private fun MethodTranslator.resolveIndirectTree(
        sig0: MethodSig, splitArgs: List<String>, ret: String,
        options: Set<MethodSig>, getCaller: (Builder) -> Unit,
        calledCanThrow: Boolean,
        owner: String
    ): Boolean {

        // find all viable children
        // group them by implementation
        val allChildren = findAllConstructableChildren(sig0.clazz)

        val groupedByClass = allChildren
            .groupBy { hIndex.getAlias(sig0.withClass(it)) }
            .map { it.key to it.value }
            .filter { it.second.isNotEmpty() }
            .sortedBy { it.second.size } // biggest case last

        if (groupedByClass.isEmpty()) {
            // printUsed(sig0)
            LOGGER.warn("$sig0 has no implementations? By $sig")
            return false
        }

        val numTests = (0 until groupedByClass.lastIndex)
            .sumOf { groupedByClass[it].second.size }

        if (numTests < maxOptions) {

            fun createPyramidCondition(classes2: List<String>): ArrayList<Instruction> {
                val result = ArrayList<Instruction>(classes2.size * 5)
                for (k in classes2.indices) {
                    result.add(LocalGet(tmpI32))
                    result.add(i32Const(gIndex.getClassIndex(classes2[k])))
                    result.add(I32EQ)
                    if (k > 0) result.add(I32Or)
                    result.add(Comment(classes2[k]))
                }
                return result
            }

            fun getArgs(): List<String> {
                // first ptrType is for 'this' for call
                return listOf(ptrType) + splitArgs
            }

            fun getResult(): List<String> {
                val result = ArrayList<String>(2)
                if (ret != "V") result.add(jvm2wasm(ret))
                if (calledCanThrow) result.add(ptrType)
                return result
            }

            fun createBody(sigJ: MethodSig): List<Instruction> {
                val printer = Builder()
                printer.append(Call(methodName(sigJ)))
                printer.fixThrowable(calledCanThrow, sigJ)
                return printer.instrs
            }

            fun printCallPyramid(printer: Builder) {

                val checkForInvalidClasses = false

                printer.comment("tree for $sig0 -> $options")
                if (groupedByClass.size > 1 || checkForInvalidClasses) {
                    getCaller(printer)
                    printer.append(Call("readClass"))
                        .append(LocalSet(tmpI32))
                }

                var lastBranch: List<Instruction> =
                    if (checkForInvalidClasses) listOf(Call("jvm_JVM32_throwJs_V"), Unreachable)
                    else createBody(groupedByClass.last().first)

                val jMax = groupedByClass.size - (!checkForInvalidClasses).toInt()
                for (j in jMax - 1 downTo 0) {
                    val (toBeCalled, classes2) = groupedByClass[j]
                    val nextBranch = createPyramidCondition(classes2)
                    nextBranch.add(IfBranch(createBody(toBeCalled), lastBranch, getArgs(), getResult()))
                    lastBranch = nextBranch
                }
                printer.append(lastBranch)
            }

            stackPush()
            checkNotNull0(owner, name, getCaller)

            if (numTests < 3) {
                printCallPyramid(printer)
            } else {
                val helperName = "tree_${sig0.toString().escapeChars()}"
                helperFunctions.getOrPut(helperName) {
                    val results = ArrayList<String>(2)
                    if (ret != "V") results.add(jvm2wasm(ret))
                    if (canThrowError) results.add(ptrType)

                    // local variable for dupi32
                    val printer = Builder()
                    // load all parameters onto the stack
                    for (k in 0 until splitArgs.size + 1) {
                        printer.append(ParamGet(k))
                    }
                    printCallPyramid(printer)
                    printer.append(Return)

                    FunctionImpl(
                        helperName, listOf(ptrType) + splitArgs,
                        results, listOf(LocalVariable(tmpI32, ptrType)),
                        printer.instrs, false,
                    )
                }
                printer.append(Call(helperName))
            }

            pop(splitArgs, false, ret)
            stackPop()

            return true
        } else return false // if there is too many tests, use resolveIndirect
    }

}