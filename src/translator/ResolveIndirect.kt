package translator

import canThrowError
import dIndex
import dependency.ActuallyUsedIndex
import gIndex
import hIndex
import me.anno.utils.algorithms.Recursion
import me.anno.utils.types.Booleans.toInt
import org.apache.logging.log4j.LogManager
import translator.MethodTranslator.Companion.comments
import utils.*
import utils.MethodResolver.resolveMethod
import utils.Param.Companion.toParams
import utils.WASMTypes.i32
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

    private const val maxOptionsInTree = 16

    private fun findAllConstructableChildren(clazz0: String): Collection<String> {
        return Recursion.collectRecursive(clazz0) { clazz, remaining ->
            val children = hIndex.childClasses[clazz]
            if (children != null) for (child in children) {
                if (child in dIndex.constructableClasses) {
                    remaining.add(child)
                }
            }
        }.filter { !hIndex.isAbstractClass(it) }
    }

    private fun Builder.fixThrowable(calledCanThrow: Boolean, sigJ: MethodSig) {
        if (calledCanThrow != canThrowError(sigJ)) {
            append(if (calledCanThrow) i32Const0 else Call.panic)
        }
    }

    private fun MethodTranslator.callSingleOption(sigJ: MethodSig, sig0: MethodSig, calledCanThrow: Boolean) {
        if (comments) printer.comment("single for $sig0 -> $sigJ")
        ActuallyUsedIndex.add(this.sig, sigJ)
        printer.append(Call(methodName(sigJ)))
        printer.fixThrowable(calledCanThrow, sigJ)
    }

    fun MethodTranslator.resolveIndirect(
        sig0: MethodSig, splitArgs: List<String>, ret: String?,
        getCaller: (Builder) -> Unit, calledCanThrow: Boolean
    ): Boolean {
        val options = findConstructableChildImplementations(sig0)
        if (options.size == 1) {
            callSingleOption(options.first(), sig0, calledCanThrow)
            return true
        } else if (options.size < maxOptionsInTree) {
            return resolveIndirectTree(sig0, splitArgs, ret, options, getCaller, calledCanThrow)
        } else return false
    }

    private fun createPyramidCondition(classes2: List<String>, tmpI32: LocalVariableOrParam): ArrayList<Instruction> {
        val result = ArrayList<Instruction>(classes2.size * 5)
        for (k in classes2.indices) {
            result.add(tmpI32.getter)
            result.add(i32Const(gIndex.getClassId(classes2[k])))
            result.add(I32EQ)
            if (k > 0) result.add(I32Or)
            if (comments) result.add(Comment(classes2[k]))
        }
        return result
    }

    private fun getArgs(splitArgs: List<String>): List<String> {
        // first ptrType is for 'this' for call
        return listOf(ptrType) + splitArgs
    }

    private fun getResult(ret: String?, calledCanThrow: Boolean): List<String> {
        val result = ArrayList<String>(2)
        if (ret != null) result.add(jvm2wasmTyped(ret))
        if (calledCanThrow) result.add(ptrType)
        return result
    }

    private fun createBody(sigJ: MethodSig, calledCanThrow: Boolean, returnAtEnd: Boolean): ArrayList<Instruction> {
        val printer = Builder(3)
        printer.append(Call(methodName(sigJ)))
        printer.fixThrowable(calledCanThrow, sigJ)
        if (returnAtEnd) printer.append(Return)
        return printer.instrs
    }

    /**
     * find all viable children
     * group them by implementation
     * */
    private fun findMethodsGroupedByClass(sig0: MethodSig, sig: MethodSig): List<Pair<MethodSig, List<String>>>? {

        val allChildren = findAllConstructableChildren(sig0.clazz).map { clazz ->
            clazz to resolveMethod(sig0.withClass(clazz), true)!!
        }

        if (allChildren.isEmpty()) {
            // not constructable
            return null
        }

        val groupedByClass = allChildren
            .groupBy { it.second }
            .map { (impl, classes) ->
                impl to classes.map { (name, _) -> name }
            }.sortedBy { it.second.size } // biggest case last

        if (groupedByClass.isEmpty()) {
            // printUsed(sig0)
            LOGGER.warn("$sig0 has no implementations? By $sig, children: $allChildren")
            // else ignore warning, because we don't support annotation classes yet
            return null
        }
        return groupedByClass
    }

    private fun countTests(groupedByClass: List<Pair<MethodSig, List<String>>>): Int {
        return (0 until groupedByClass.lastIndex)
            .sumOf { groupedByClass[it].second.size }
    }

    private val groupedByClassCache = HashMap<MethodSig, List<Pair<MethodSig, List<String>>>?>(1 shl 16)

    private fun MethodTranslator.resolveIndirectTree(
        sig0: MethodSig, splitArgs: List<String>, ret: String?,
        options: Set<MethodSig>, getCaller: (Builder) -> Unit,
        calledCanThrow: Boolean
    ): Boolean {

        val groupedByClass =
            groupedByClassCache.getOrPut(sig0) {
                findMethodsGroupedByClass(sig0, sig)
            } ?: return false

        // if there is too many tests, use resolveIndirect
        val numTests = countTests(groupedByClass)
        if (numTests >= maxOptionsInTree) return false

        if (numTests < 3) {
            if (comments) printer.comment("small pyramid")
            val tmpI32 = variables.defineLocalVar("classId", i32, "int")
            printCallPyramid(
                sig0, splitArgs, ret, options, getCaller, calledCanThrow,
                printer, groupedByClass, tmpI32, false
            )
        } else {
            val treeCall = getOrPutTree(
                sig0, splitArgs, ret, options,
                calledCanThrow, true, groupedByClass
            )
            printer.append(treeCall)
        }

        return true
    }

    private fun printCallPyramid(
        sig0: MethodSig, splitArgs: List<String>, ret: String?,
        options: Set<MethodSig>, getCaller: (Builder) -> Unit,
        calledCanThrow: Boolean, printer: Builder,
        groupedByClass: List<Pair<MethodSig, List<String>>>,
        classIdLocal: LocalVariableOrParam,
        returnInBranch: Boolean,
    ) {

        val checkForInvalidClasses = false

        if (comments) printer.comment("tree for $sig0 -> $options")
        if (groupedByClass.size > 1 || checkForInvalidClasses) {
            getCaller(printer)
            printer.append(Call.readClass)
                .append(classIdLocal.setter)
        }

        var lastBranch: ArrayList<Instruction> =
            if (checkForInvalidClasses) arrayListOf(Call("jvm_JVM32_throwJs_V"), Unreachable)
            else createBody(groupedByClass.last().first, calledCanThrow, returnInBranch)

        val jMax = groupedByClass.size - (!checkForInvalidClasses).toInt()
        for (j in jMax - 1 downTo 0) {
            val (toBeCalled, classes2) = groupedByClass[j]
            val nextBranch = createPyramidCondition(classes2, classIdLocal)
            nextBranch.add(
                IfBranch(
                    createBody(toBeCalled, calledCanThrow, returnInBranch),
                    lastBranch, getArgs(splitArgs), getResult(ret, calledCanThrow)
                )
            )
            lastBranch = nextBranch
        }
        printer.append(lastBranch)
    }

    private val treeGetSelf = { printerI: Builder -> printerI.append(ParamGet[0]); Unit }
    private fun MethodTranslator.getOrPutTree(
        sig0: MethodSig, splitArgs: List<String>, ret: String?,
        options: Set<MethodSig>, calledCanThrow: Boolean,
        returnInBranch: Boolean, groupedByClass: List<Pair<MethodSig, List<String>>>,
    ): Call {
        val helperName = "tree_${sig0.toString().escapeChars()}"
        helperFunctions.getOrPut(helperName) {
            val results = ArrayList<String>(2)
            if (ret != null) results.add(jvm2wasmTyped(ret))
            if (canThrowError) results.add(ptrType)

            // local variable for dupi32
            val printer = Builder()
            // load all parameters onto the stack
            for (k in 0 until splitArgs.size + 1) {
                printer.append(ParamGet[k])
            }
            val classIdLocal = LocalVariableOrParam("int", i32, "classId", 0, false)
            printCallPyramid(
                sig0, splitArgs, ret, options, treeGetSelf,
                calledCanThrow, printer, groupedByClass, classIdLocal, returnInBranch
            )
            printer.append(Return)

            FunctionImpl(
                helperName, (listOf(ptrType) + splitArgs).toParams(), results,
                listOf(LocalVariable(classIdLocal.name, i32)),
                printer.instrs, false,
            )
        }
        return Call(helperName)
    }

}