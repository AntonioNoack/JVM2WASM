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
                append(" i32.const 0")
            } else {
                append(" call \$panic")
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
        printer.append(";; single for $sig0 -> $sigJ\n")
        ActuallyUsedIndex.add(this.sig, sigJ)
        printer.append("  call \$").append(methodName(sigJ))
        printer.fixThrowable(calledCanThrow, sigJ)
        printer.append("\n")
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

            fun printCallPyramid(printer: Builder) {

                val checkForInvalidClasses = false

                printer.append(";; tree for $sig0 -> $options\n")
                if (groupedByClass.size > 1 || checkForInvalidClasses) {
                    getCaller(printer)
                    printer.append(" call \$readClass ")
                        .append("local.set ").append(tmpI32).append("\n")
                }

                val jMax = groupedByClass.size - (!checkForInvalidClasses).toInt()
                for (j in 0 until jMax) {
                    val (toBeCalled, classes2) = groupedByClass[j]
                    for (k in classes2.indices) {
                        printer
                            .append("local.get ").append(tmpI32)
                            .append(" i32.const ").append(gIndex.getClassIndex(classes2[k]))
                            .append(" i32.eq")
                        if (k > 0) printer.append(" i32.or")
                        printer.append(" ;; ").append(classes2[k]).append("\n  ")
                    }
                    // write params and result
                    printer.append("(if (param i32") // first i32 is for 'this' for call
                    for (argI in splitArgs) printer.append(" ").append(argI)
                    printer.append(") (result")
                    if (ret != "V") printer.append(" ").append(jvm2wasm(ret))
                    if (calledCanThrow) printer.append(" i32")
                    printer.append(") (then\n    ")
                    printer.append("call \$").append(methodName(toBeCalled)).append("\n  ")
                    printer.fixThrowable(calledCanThrow, toBeCalled)
                    printer.append(") (else\n")
                }
                if (checkForInvalidClasses) {
                    printer.append("    call \$jvm_JVM32_throwJs_V\n")
                    printer.append("    unreachable\n")
                } else {
                    val sigJ = groupedByClass.last().first
                    printer.append("    call \$").append(methodName(sigJ))
                    printer.fixThrowable(calledCanThrow, sigJ)
                    printer.append("\n  ")
                }
                for (j in 0 until jMax) {
                    printer.append("))")
                }
                printer.append("\n")
            }

            stackPush()
            checkNotNull0(owner, name, getCaller)

            if (numTests < 3) {
                printCallPyramid(printer)
            } else {
                val helperName = "tree_${sig0.toString().escapeChars()}"
                helperFunctions.getOrPut(helperName) {
                    val printer = StringBuilder2()
                    printer.append("(func \$").append(helperName)
                        .append(" (param ").append(ptrType) // 'this'
                    for (arg in splitArgs) printer.append(' ').append(arg)
                    printer.append(") (result")
                    if (ret != "V") printer.append(' ').append(jvm2wasm(ret))
                    if (canThrowError) printer.append(' ').append(ptrType)
                    printer.append(")\n")
                    // local variable for dupi32
                    printer.append("  (local ").append(tmpI32).append(' ').append(ptrType).append(")\n")
                    // load all parameters onto the stack
                    for (k in 0 until splitArgs.size + 1) {
                        printer.append("  local.get ").append(k).append('\n')
                    }
                    printCallPyramid(printer)
                    printer.append("  return\n)\n")
                    printer
                }
                printer.append("  call \$").append(helperName).append("\n")
            }

            pop(splitArgs, false, ret)
            stackPop()

            return true
        } else {
            // if there is too many tests, use resolveIndirect
            return false
        }
    }

}