package translator

import alwaysUseFieldCalls
import annotations.Boring
import annotations.NotCalled
import anyMethodThrows
import api
import canThrowError
import checkArrayAccess
import checkClassCasts
import checkIntDivisions
import checkNullPointers
import dIndex
import dependency.ActuallyUsedIndex
import enableTracing
import exportAll
import gIndex
import graphing.StackValidator
import graphing.StackValidator.validateInputOutputStacks
import graphing.StructuralAnalysis
import hIndex
import hierarchy.DelayedLambdaUpdate
import hierarchy.DelayedLambdaUpdate.Companion.synthClassName
import ignoreNonCriticalNullPointers
import me.anno.io.Streams.writeLE32
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertFail
import me.anno.utils.assertions.assertFalse
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.lists.Lists.pop
import me.anno.utils.types.Booleans.hasFlag
import me.anno.utils.types.Strings.shorten
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*
import replaceClass1
import translator.ResolveIndirect.resolveIndirect
import useWASMExceptions
import utils.*
import utils.ReplaceOptimizer.optimizeUsingReplacements
import wasm.instr.*
import wasm.instr.Const.Companion.f32Const
import wasm.instr.Const.Companion.f32Const0
import wasm.instr.Const.Companion.f32Const1
import wasm.instr.Const.Companion.f32Const2
import wasm.instr.Const.Companion.f64Const
import wasm.instr.Const.Companion.f64Const0
import wasm.instr.Const.Companion.f64Const1
import wasm.instr.Const.Companion.i32Const
import wasm.instr.Const.Companion.i32Const0
import wasm.instr.Const.Companion.i32Const1
import wasm.instr.Const.Companion.i32Const2
import wasm.instr.Const.Companion.i32Const3
import wasm.instr.Const.Companion.i32Const4
import wasm.instr.Const.Companion.i32Const5
import wasm.instr.Const.Companion.i32ConstM1
import wasm.instr.Const.Companion.i64Const
import wasm.instr.Const.Companion.i64Const0
import wasm.instr.Const.Companion.i64Const1
import wasm.instr.Instructions.Drop
import wasm.instr.Instructions.F32Add
import wasm.instr.Instructions.F32Div
import wasm.instr.Instructions.F32Load
import wasm.instr.Instructions.F32Mul
import wasm.instr.Instructions.F32Store
import wasm.instr.Instructions.F32Sub
import wasm.instr.Instructions.F32_CONVERT_I32S
import wasm.instr.Instructions.F32_CONVERT_I64S
import wasm.instr.Instructions.F32_DEMOTE_F64
import wasm.instr.Instructions.F32_NEG
import wasm.instr.Instructions.F32_REINTERPRET_I32
import wasm.instr.Instructions.F64Add
import wasm.instr.Instructions.F64Div
import wasm.instr.Instructions.F64Load
import wasm.instr.Instructions.F64Mul
import wasm.instr.Instructions.F64Store
import wasm.instr.Instructions.F64Sub
import wasm.instr.Instructions.F64_CONVERT_I32S
import wasm.instr.Instructions.F64_CONVERT_I64S
import wasm.instr.Instructions.F64_NEG
import wasm.instr.Instructions.F64_PROMOTE_F32
import wasm.instr.Instructions.F64_REINTERPRET_I64
import wasm.instr.Instructions.I32Add
import wasm.instr.Instructions.I32And
import wasm.instr.Instructions.I32EQ
import wasm.instr.Instructions.I32EQZ
import wasm.instr.Instructions.I32GES
import wasm.instr.Instructions.I32GTS
import wasm.instr.Instructions.I32LES
import wasm.instr.Instructions.I32LTS
import wasm.instr.Instructions.I32Load
import wasm.instr.Instructions.I32Load16S
import wasm.instr.Instructions.I32Load16U
import wasm.instr.Instructions.I32Load8S
import wasm.instr.Instructions.I32Mul
import wasm.instr.Instructions.I32NE
import wasm.instr.Instructions.I32Or
import wasm.instr.Instructions.I32Shl
import wasm.instr.Instructions.I32ShrS
import wasm.instr.Instructions.I32ShrU
import wasm.instr.Instructions.I32Store
import wasm.instr.Instructions.I32Store16
import wasm.instr.Instructions.I32Store8
import wasm.instr.Instructions.I32Sub
import wasm.instr.Instructions.I32XOr
import wasm.instr.Instructions.I32_DIVS
import wasm.instr.Instructions.I32_REM_S
import wasm.instr.Instructions.I32_WRAP_I64
import wasm.instr.Instructions.I64Add
import wasm.instr.Instructions.I64And
import wasm.instr.Instructions.I64EQ
import wasm.instr.Instructions.I64Load
import wasm.instr.Instructions.I64Mul
import wasm.instr.Instructions.I64NE
import wasm.instr.Instructions.I64Or
import wasm.instr.Instructions.I64Shl
import wasm.instr.Instructions.I64ShrS
import wasm.instr.Instructions.I64ShrU
import wasm.instr.Instructions.I64Store
import wasm.instr.Instructions.I64Sub
import wasm.instr.Instructions.I64XOr
import wasm.instr.Instructions.I64_DIVS
import wasm.instr.Instructions.I64_EXTEND_I32S
import wasm.instr.Instructions.I64_REM_S
import wasm.instr.Instructions.Return
import wasm.instr.Instructions.Unreachable
import wasm.parser.FunctionImpl
import wasm.parser.LocalVariable
import kotlin.collections.set

/**
 * convert instructions from JVM into WASM
 * */
class MethodTranslator(
    val access: Int,
    val clazz: String,
    val name: String,
    val descriptor: String,
) : MethodVisitor(api) {

    private var isAbstract = false
    private val stack = ArrayList<String>()
    private val argsMapping = HashMap<Int, Int>()

    val localVariables1 = ArrayList<LocalVariable>()
    val localVarsWithParams = ArrayList<LocalVar>()

    private var resultType = 'V'

    private val startLabel = Label()
    private val nodes = ArrayList<TranslatorNode>()
    private var currentNode = TranslatorNode(startLabel)
    var printer = currentNode.printer

    val sig = MethodSig.c(clazz, name, descriptor)
    val canThrowError = canThrowError(sig)
    private val canPush = enableTracing && canThrowError
    private val isStatic = access.hasFlag(ACC_STATIC)


    init {
        printOps = false // clazz == "kotlin/collections/CollectionsKt__ReversedViewsKt"
        if (printOps) println("Method-Translating $clazz.$name.$descriptor")

        nodes.add(currentNode)
        currentNode.inputStack = emptyList()

        if (access.hasFlag(ACC_NATIVE) || access.hasFlag(ACC_ABSTRACT)) {
            // if it has @WASM annotation, use that code
            val wasm = hIndex.wasmNative[sig]
            if (wasm != null) {
                printHeader()
                printer.append(wasm).append(Return)
            } else {
                // println("skipped ${utils.methodName(clazz, name, descriptor)}, because native|abstract")
                isAbstract = true
            }
        } else {
            if (printOps) {
                println("\n////////////////////////")
                println("// processing $clazz $name $descriptor")
                println("////////////////////////\n")
            }
            printHeader()
            if (name == "<clinit>") {
                // check whether this class was already inited
                val clazz1 = gIndex.getClassIndex(clazz)
                printer.append(i32Const(clazz1))
                    .append(Call("wasStaticInited"))
                    .append(
                        IfBranch(
                            if (canThrowError) listOf(i32Const0, Return)
                            else listOf(Return), emptyList(),
                            emptyList(), emptyList()
                        )
                    )
            }
        }
    }

    lateinit var args: List<String>
    private fun printHeader() {
        // convert descriptor into list of classes for arguments
        args = split1(descriptor.substring(1, descriptor.lastIndexOf(')')))
        // special rules in Java:
        // double and long use two slots in localVariables
        resultType = descriptor[descriptor.indexOf(')') + 1]
        val mapped = hIndex.getAlias(sig)
        assertEquals(mapped, sig) { "Must not translate $sig, because it is mapped to $mapped" }
        defineArgsMapping()
    }

    private fun defineArgsMapping() {
        var numArgs = 0
        var idx = 0
        if (!isStatic) {
            localVarsWithParams.add(LocalVar("", ptrType, "self", 0, true))
            argsMapping[numArgs++] = idx++
        }
        for (arg in args) {
            val type = jvm2wasm(arg)
            localVarsWithParams.add(LocalVar("", type, "param$idx", idx, true))
            argsMapping[numArgs] = idx++
            numArgs += when (arg) {
                "D", "J" -> 2
                else -> 1
            }
        }
    }

    private fun createFuncHead(): FunctionImpl {
        val name2 = methodName(sig)
        val exported = exportAll || sig in hIndex.exportedMethods
        val results = ArrayList<String>(2)
        if (resultType != 'V') results.add(jvm2wasm1(resultType))
        if (canThrowError) results.add(ptrType)
        return FunctionImpl(
            name2, (if (isStatic) emptyList() else listOf(ptrType)) + args.map { jvm2wasm(it) },
            results, localVariables1, emptyList(), exported
        )
    }

    override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
        // println("  annotation $descriptor, $visible")
        return null
    }

    @Boring
    override fun visitAnnotableParameterCount(parameterCount: Int, visible: Boolean) {
        // e.g. SDFCylinder$Companion
        // println(" annotable params count: $parameterCount, $visible")
    }

    @Boring
    override fun visitAnnotationDefault(): AnnotationVisitor? {
        return super.visitAnnotationDefault()
    }

    @Boring
    @NotCalled
    override fun visitAttribute(attribute: Attribute?) {
        // println("  attr $attribute")
    }

    override fun visitFrame(
        type: Int,
        numLocal: Int,
        local: Array<Any?>,
        numStack: Int,
        stack: Array<Any?>
    ) {
        // describes types of stack and local variables;
        // in Java >= 1.6, always before jump, so if/else is easier to create and verify :)
        val data = "  ;; frame ${
            when (type) {
                F_NEW -> "new"
                F_SAME -> "same"
                F_SAME1 -> "same1"
                F_APPEND -> "append"
                F_CHOP -> "chop"
                F_FULL -> "full"
                else -> "?$type"
            } // , local={$numLocal, [${local.joinToString { "\"$it\"" }}]}
        }=[${
            stack.toList().subList(0, numStack)
        }]"
        // if (printOps) println(data)
        if (type == F_NEW) {

            // replace stack with the given one
            if (printOps) println("old stack: ${this.stack}")
            this.stack.clear()
            for ((idx, si) in stack.withIndex()) {
                if (idx >= numStack) break
                this.stack.add(
                    when (si) {
                        INTEGER -> i32
                        LONG -> i64
                        FLOAT -> f32
                        DOUBLE -> f64
                        null -> break
                        else -> ptrType
                    }
                )
            }
            if (printOps) println("new stack: ${this.stack}")

            // if current label has incorrect stack, correct it
            // (but only, if it has no code -> frame always follows a label, so nothing to worry about, I think)

            // never happens
            // if (i32 in currentNode.printer) throw IllegalStateException()

            currentNode.inputStack = ArrayList(this.stack)

        } else throw NotImplementedError()
        if (printOps) printer.comment(data)
    }

    override fun visitIincInsn(varIndex: Int, increment: Int) {
        // increment the variable at that index
        if (printOps) println("  [$varIndex] += $increment")
        val type = findLocalVar(varIndex, i32)
        printer
            .append(type.localGet).append(i32Const(increment)).append(I32Add)
            .append(type.localSet)
    }

    @Boring
    @NotCalled
    override fun visitInsnAnnotation(
        typeRef: Int,
        typePath: TypePath?,
        descriptor: String?,
        visible: Boolean
    ): AnnotationVisitor? {
        // println("  instr $typeRef, $typePath, $descriptor, $visible")
        return null
    }

    @Boring
    override fun visitCode() {
    }

    private fun Builder.poppush(x: String): Builder {
        // ensure we got the correct type
        if (printOps) {
            println("// $stack = $x")
            printer.comment("$stack = $x")
        }
        assertFalse(stack.isEmpty()) { "Expected $x, but stack was empty; $clazz, $name, $descriptor" }
        assertEquals(x, stack.last())
        return this
    }

    private fun Builder.pop(x: String): Builder {
        // ensure we got the correct type
        if (printOps) {
            println("// $stack -= $x")
            printer.comment("$stack -= $x")
        }
        assertFalse(stack.isEmpty()) { "Expected $x, but stack was empty; $clazz, $name, $descriptor" }
        assertEquals(x, stack.pop())
        return this
    }

    private fun Builder.push(x: String): Builder {
        if (printOps) {
            println("// $stack += $x")
            printer.comment("$stack += $x")
        }
        stack.add(x)
        return this
    }

    override fun visitInsn(opcode: Int) {
        // https://github.com/AssemblyScript/assemblyscript/wiki/WebAssembly-to-TypeScript-Cheat-Sheet
        if (printOps) println("  [${OpCode[opcode]}]")
        when (opcode) {
            0x00 -> {} // nop

            // constant loading instructions
            0x01 -> printer.push(ptrType)
                .append(if (is32Bits) i32Const0 else i64Const0) // load null
            0x02 -> printer.push(i32).append(i32ConstM1)
            0x03 -> printer.push(i32).append(i32Const0)
            0x04 -> printer.push(i32).append(i32Const1)
            0x05 -> printer.push(i32).append(i32Const2)
            0x06 -> printer.push(i32).append(i32Const3)
            0x07 -> printer.push(i32).append(i32Const4)
            0x08 -> printer.push(i32).append(i32Const5)
            0x09 -> printer.push(i64).append(i64Const0)
            0x0a -> printer.push(i64).append(i64Const1)
            0x0b -> printer.push(f32).append(f32Const0)
            0x0c -> printer.push(f32).append(f32Const1)
            0x0d -> printer.push(f32).append(f32Const2)
            0x0e -> printer.push(f64).append(f64Const0)
            0x0f -> printer.push(f64).append(f64Const1)

            // in 0x15 .. 0x19 -> printer.append("  local.get [idx]")

            // load instructions
            0x2e -> {
                stackPush()
                printer.pop(ptrType).poppush(i32)
                    .append(if (checkArrayAccess) Call.i32ArrayLoad else Call.i32ArrayLoadU)
                stackPop()
                if (checkArrayAccess && anyMethodThrows) handleThrowable()
            }
            0x2f -> {
                stackPush()
                printer.pop(ptrType).pop(i32).push(i64)
                    .append(if (checkArrayAccess) Call.i64ArrayLoad else Call.i64ArrayLoadU)
                stackPop()
                if (checkArrayAccess && anyMethodThrows) handleThrowable()
            }
            0x30 -> {
                stackPush()
                printer.pop(ptrType).pop(i32).push(f32)
                    .append(if (checkArrayAccess) Call.f32ArrayLoad else Call.f32ArrayLoadU)
                stackPop()
                if (checkArrayAccess && anyMethodThrows) handleThrowable()
            }
            0x31 -> {
                stackPush()
                printer.pop(ptrType).pop(i32).push(f64)
                    .append(if (checkArrayAccess) Call.f64ArrayLoad else Call.f64ArrayLoadU)
                stackPop()
                if (checkArrayAccess && anyMethodThrows) handleThrowable()
            }
            0x32 -> {
                stackPush()
                printer.pop(ptrType).pop(i32).push(ptrType)
                    .append(
                        if (checkArrayAccess) if (is32Bits) Call.i32ArrayLoad else Call.i64ArrayLoad
                        else if (is32Bits) Call.i32ArrayLoadU else Call.i64ArrayLoadU
                    )
                stackPop()
                if (checkArrayAccess && anyMethodThrows) handleThrowable()
            }
            0x33 -> {
                stackPush()
                printer.pop(ptrType).poppush(i32)
                    .append(if (checkArrayAccess) Call.s8ArrayLoad else Call.s8ArrayLoadU)
                stackPop()
                if (checkArrayAccess && anyMethodThrows) handleThrowable()
            }
            0x34 -> {
                stackPush()
                printer.pop(ptrType).poppush(i32)
                    .append(if (checkArrayAccess) Call.u16ArrayLoad else Call.u16ArrayLoadU)
                stackPop()
                if (checkArrayAccess && anyMethodThrows) handleThrowable()
            }
            0x35 -> {
                stackPush()
                printer.pop(ptrType).poppush(i32)
                    .append(if (checkArrayAccess) Call.s16ArrayLoad else Call.s16ArrayLoadU)
                stackPop()
                if (checkArrayAccess && anyMethodThrows) handleThrowable()
            }
            // store instructions
            0x4f -> {
                stackPush()
                printer.pop(i32).pop(i32).pop(ptrType)
                    .append(if (checkArrayAccess) Call.i32ArrayStore else Call.i32ArrayStoreU)
                stackPop()
                if (checkArrayAccess && anyMethodThrows) handleThrowable()
            }
            0x50 -> {
                stackPush()
                printer.pop(i64).pop(i32).pop(ptrType)
                    .append(if (checkArrayAccess) Call.i64ArrayStore else Call.i64ArrayStoreU)
                stackPop()
                if (checkArrayAccess && anyMethodThrows) handleThrowable()
            }
            0x51 -> {
                stackPush()
                printer.pop(f32).pop(i32).pop(ptrType)
                    .append(if (checkArrayAccess) Call.f32ArrayStore else Call.f32ArrayStoreU)
                stackPop()
                if (checkArrayAccess && anyMethodThrows) handleThrowable()
            }
            0x52 -> {
                stackPush()
                printer.pop(f64).pop(i32).pop(ptrType)
                    .append(if (checkArrayAccess) Call.f64ArrayStore else Call.f64ArrayStoreU)
                stackPop()
                if (checkArrayAccess && anyMethodThrows) handleThrowable()
            }
            0x53 -> {
                stackPush()
                printer.pop(ptrType).pop(i32).pop(ptrType)
                    .append(
                        if (checkArrayAccess) if (is32Bits) Call.i32ArrayStore else Call.i64ArrayStore
                        else if (is32Bits) Call.i32ArrayStoreU else Call.i64ArrayStoreU
                    )
                stackPop()
                if (checkArrayAccess && anyMethodThrows) handleThrowable()
            }
            0x54 -> {
                stackPush()
                printer.pop(i32).pop(i32).pop(ptrType)
                    .append(if (checkArrayAccess) Call.i8ArrayStore else Call.i8ArrayStoreU)
                stackPop()
                if (checkArrayAccess && anyMethodThrows) handleThrowable()
            }
            0x55, 0x56 -> { // char/short-array store
                stackPush()
                printer.pop(i32).pop(i32).pop(ptrType)
                    .append(if (checkArrayAccess) Call.i16ArrayStore else Call.i16ArrayStoreU)
                stackPop()
                if (checkArrayAccess && anyMethodThrows) handleThrowable()
            }

            // returnx is important: it shows to cancel the flow = jump to end
            in 0xac..0xb1 -> {

                if (currentNode.ifTrue != null) {
                    // throw IllegalStateException("Branch cannot have return afterwards")
                    // we expected a label, but didn't get any -> create out own
                    visitLabel(Label())
                }

                // pop types from stack
                // we actually need the list of what was returned, I think
                // pop these things from the stack? :)

                if (opcode != 0xb1) {
                    val type = stack.last()
                    printer.pop(type)
                    currentNode.outputStack = listOf(type, ptrType)
                } else {
                    currentNode.outputStack = listOf(ptrType)
                }

                if (canThrowError(sig) && !useWASMExceptions) {
                    printer.append(if (is32Bits) i32Const0 else i64Const0) // no Throwable, normal exit
                }

                printer.append(Return)

                // marking this as the end
                // if (printOps) println("marking $currentNode as return")
                // if it is missing anywhere, we could call this:
                // nextNode(Label())
            }
            0x101 -> {
                assertTrue(canThrowError)
                printer.append(Return)
            }
            0x57 -> {
                val type1 = stack.last()
                printer.pop(type1).drop()
            }
            0x58 -> {
                val type = stack.last()
                printer.pop(type).drop()
                if (!is32Bits) throw NotImplementedError("We'd need to differentiate ptrType from i64 for this to work correctly")
                if (type == i32 || type == f32)
                    printer.pop(stack.last()).drop()
            }
            0x59 -> {// dup
                val type1 = stack.last()
                printer.push(type1)
                if (type1 == i32) {
                    printer.dupI32()
                } else {
                    printer.append(Call("dup$type1"))
                }
            }
            0x5a -> {
                val v0 = stack.pop()!!
                val v1 = stack.pop()!!
                stack.add(v0)
                stack.add(v1)
                stack.add(v0)
                printer.append(Call("dup_x1$v0$v1"))
                // value2, value1 →
                // value1, value2, value1
            }
            0x5b -> {
                val v0 = stack.pop()!!
                val v1 = stack.pop()!!
                val v2 = stack.pop()!!
                stack.add(v0)
                stack.add(v2)
                stack.add(v1)
                stack.add(v0)
                printer.append(Call("dup_x2$v0$v1$v2"))
            }
            0x5c -> {
                val v1 = stack[stack.size - 1]
                if (!is32Bits) throw NotImplementedError("We'd need to differentiate ptrType from i64 for this to work correctly")
                if (v1 == i32 || v1 == f32) {
                    // value2, value1 ->
                    // value2, value1, value2, value1
                    val v0 = stack[stack.size - 2]
                    stack.add(v0)
                    stack.add(v1)
                    printer.append(Call("dup2$v0$v1"))
                } else {
                    // dup
                    stack.add(v1)
                    printer.append(Call("dup$v1"))
                }
            }
            0x5d -> {
                // dup2_x1
                val v1 = stack[stack.size - 1]
                if (!is32Bits) throw NotImplementedError("We'd need to differentiate ptrType from i64 for this to work correctly")
                if (v1 == i32 || v1 == f32) {
                    // value3, value2, value1 ->
                    // value2, value1, value3, value2, value1
                    stack.pop()
                    val v2 = stack.pop()!!
                    val v3 = stack.pop()!!
                    stack.add(v2)
                    stack.add(v1)
                    stack.add(v3)
                    stack.add(v2)
                    stack.add(v1)
                    printer.append(Call("dup2_x1$v1$v2$v3"))
                } else {
                    // value2, value1 ->
                    // value1, value2, value1
                    // seems to be identical to 0x5a
                    visitInsn(0x5a)
                }
            }
            0x5e -> {
                printer.append(Call("dup2_x2"))
                // if (x != null) log("mouseX", x[0] = getMouseX());
                // if (y != null) log("mouseY", y[0] = getMouseY());
                TODO("Implement dup2_x2 instruction")
            }
            0x5f -> { // swap
                val type1 = stack.last()
                val type2 = stack[stack.size - 2]
                if (type1 != type2) printer.pop(type1).pop(type2).push(type1).push(type2)
                printer.append(Call("swap$type1$type2"))
            }
            0x60 -> printer.pop(i32).poppush(i32).append(I32Add)
            0x61 -> printer.pop(i64).poppush(i64).append(I64Add)
            0x62 -> printer.pop(f32).poppush(f32).append(F32Add)
            0x63 -> printer.pop(f64).poppush(f64).append(F64Add)
            0x64 -> printer.pop(i32).poppush(i32).append(I32Sub)
            0x65 -> printer.pop(i64).poppush(i64).append(I64Sub)
            0x66 -> printer.pop(f32).poppush(f32).append(F32Sub)
            0x67 -> printer.pop(f64).poppush(f64).append(F64Sub)
            // there are no signed/unsigned versions, because it only shows the last 32 bits
            0x68 -> printer.pop(i32).poppush(i32).append(I32Mul)
            0x69 -> printer.pop(i64).poppush(i64).append(I64Mul)
            0x6a -> printer.pop(f32).poppush(f32).append(F32Mul)
            0x6b -> printer.pop(f64).poppush(f64).append(F64Mul)
            0x6c -> {
                if (checkIntDivisions) {
                    stackPush()
                    printer.poppush(i32).append(Call("safeDiv32"))
                    printer.pop(i32).poppush(i32)
                    stackPop()
                    if (anyMethodThrows) handleThrowable()
                } else {
                    printer.pop(i32).poppush(i32).append(I32_DIVS)
                }
            }
            0x6d -> {
                if (checkIntDivisions) {
                    stackPush()
                    printer.poppush(i64).append(Call("safeDiv64"))
                    printer.pop(i64).poppush(i64)
                    stackPop()
                    if (anyMethodThrows) handleThrowable()
                } else {
                    printer.pop(i64).poppush(i64).append(I64_DIVS)
                }
            }
            0x6e -> printer.pop(f32).poppush(f32).append(F32Div)
            0x6f -> printer.pop(f64).poppush(f64).append(F64Div)
            0x70 -> {
                if (checkIntDivisions) {
                    stackPush()
                    printer.poppush(i32).append(Call("checkNonZero32"))
                    stackPop()
                    if (anyMethodThrows) handleThrowable()
                }
                printer.pop(i32).poppush(i32).append(I32_REM_S)
            }
            0x71 -> {
                if (checkIntDivisions) {
                    stackPush()
                    printer.poppush(i64).append(Call("checkNonZero64"))
                    stackPop()
                    if (anyMethodThrows) handleThrowable()
                }
                printer.pop(i64).poppush(i64)
                printer.append(I64_REM_S)
            }
            0x72 -> printer.pop(f32).poppush(f32).append(Call("f32rem"))
            0x73 -> printer.pop(f64).poppush(f64).append(Call("f64rem"))
            0x74 -> printer.poppush(i32).append(Call("i32neg"))
            0x75 -> printer.poppush(i64).append(Call("i64neg"))
            0x76 -> printer.poppush(f32).append(F32_NEG)
            0x77 -> printer.poppush(f64).append(F64_NEG)
            0x78 -> printer.pop(i32).poppush(i32).append(I32Shl)
            0x79 -> printer.pop(i32).poppush(i64).append(I64_EXTEND_I32S).append(I64Shl)
            0x7a -> printer.pop(i32).poppush(i32).append(I32ShrS)
            0x7b -> printer.pop(i32).poppush(i64).append(I64_EXTEND_I32S).append(I64ShrS)
            0x7c -> printer.pop(i32).poppush(i32).append(I32ShrU)
            0x7d -> printer.pop(i32).poppush(i64).append(I64_EXTEND_I32S).append(I64ShrU)
            0x7e -> printer.pop(i32).poppush(i32).append(I32And)
            0x7f -> printer.pop(i64).poppush(i64).append(I64And)
            0x80 -> printer.pop(i32).poppush(i32).append(I32Or)
            0x81 -> printer.pop(i64).poppush(i64).append(I64Or)
            0x82 -> printer.pop(i32).poppush(i32).append(I32XOr)
            0x83 -> printer.pop(i64).poppush(i64).append(I64XOr)
            // iinc, has a constant -> different function, i32.const <value>, i32.add
            0x85 -> printer.pop(i32).push(i64).append(I64_EXTEND_I32S) // i2l
            0x86 -> printer.pop(i32).push(f32).append(F32_CONVERT_I32S) // i2f
            0x87 -> printer.pop(i32).push(f64).append(F64_CONVERT_I32S) // i2d
            0x88 -> printer.pop(i64).push(i32).append(I32_WRAP_I64) // l2i
            0x89 -> printer.pop(i64).push(f32).append(F32_CONVERT_I64S) // l2f
            0x8a -> printer.pop(i64).push(f64).append(F64_CONVERT_I64S) // l2d
            0x8b -> printer.pop(f32).push(i32).append(Call("f2i")) // f2i
            0x8c -> printer.pop(f32).push(i64).append(Call("f2l")) // f2l
            0x8d -> printer.pop(f32).push(f64).append(F64_PROMOTE_F32) // f2d
            0x8e -> printer.pop(f64).push(i32).append(Call("d2i"))
            0x8f -> printer.pop(f64).push(i64).append(Call("d2l"))
            0x90 -> printer.pop(f64).push(f32).append(F32_DEMOTE_F64) // d2f

            0x91 -> printer.poppush(i32)
                .append(i32Const(24)).append(I32Shl)
                .append(i32Const(24)).append(I32ShrS)
            0x92 -> printer.poppush(i32)
                .append(i32Const(65535)).append(I32And)
            0x93 -> printer.poppush(i32)
                .append(i32Const(16)).append(I32Shl)
                .append(i32Const(16)).append(I32ShrS)

            0x94 -> printer.pop(i64).pop(i64).push(i32).append(Call.lcmp)
            0x95 -> printer.pop(f32).pop(f32).push(i32).append(Call.fcmpl) // -1 if NaN
            0x96 -> printer.pop(f32).pop(f32).push(i32).append(Call.fcmpg) // +1 if NaN
            0x97 -> printer.pop(f64).pop(f64).push(i32).append(Call.dcmpl) // -1 if NaN
            0x98 -> printer.pop(f64).pop(f64).push(i32).append(Call.dcmpg) // +1 if NaN

            0xbe -> {
                // array length
                stackPush()
                printer.pop(ptrType).push(i32)
                    .append(if (checkArrayAccess) Call.al else Call.alU)
                stackPop()
                if (checkArrayAccess) handleThrowable()
            }
            0xbf -> {// athrow, easy :3
                printer.pop(ptrType).push(ptrType)
                printer.dupI32() // todo why are we duplicating the error???
                handleThrowable(true)
                printer.pop(ptrType)
            }
            // 0xc2 -> printer.pop(ptrType).append("  call \$monitorEnter\n") // monitor enter
            // 0xc3 -> printer.pop(ptrType).append("  call \$monitorExit\n") // monitor exit
            0xc2 -> printer.pop(ptrType).drop() // monitor enter
            0xc3 -> printer.pop(ptrType).drop() // monitor exit
            else -> throw NotImplementedError("unknown op ${OpCode[opcode]}\n")
        }
    }

    override fun visitInvokeDynamicInsn(
        name: String, descriptor: String, method: Handle,
        vararg args: Any //  Integer, Float, Long, Double, String, Type, Handle or ConstantDynamic
    ) {
        // what is that? lambda and stuff
        // until now we were able to implement everything at comptime;
        // implement this at comptime as well, as far as we get :) -> new pseudo-classes
        // java.lang.CharSequence.chars()
        // https://www.baeldung.com/java-invoke-dynamic
        // https://github.com/openjdk/jdk/blob/a445b66e58a30577dee29cacb636d4c14f0574a2/src/java.base/share/classes/java/lang/invoke/LambdaMetafactory.java
        // https://asm.ow2.io/javadoc/org/objectweb/asm/MethodVisitor.html#visitInvokeDynamicInsn(java.lang.String,java.lang.String,org.objectweb.asm.Handle,java.lang.Object...)
        // DynPrinter.print(name, descriptor, method, args)
        if (printOps) {
            println("  [invoke dyn by $sig] $name, $descriptor, [${method.owner}, ${method.name}, tag: ${method.tag}, desc: ${method.desc}], [${args.joinToString()}]")
            printer.comment("invoke-dyn $name, $descriptor, $method, [${args.joinToString()}]")
        }

        val dst = args[1] as Handle

        val synthClassName = synthClassName(sig, dst)
        val dlu = DelayedLambdaUpdate.needingBridgeUpdate[synthClassName]!!
        val fields = dlu.fields

        // register new class (not visitable)
        printer.append(i32Const(gIndex.getClassIndex(synthClassName)))
        if (dlu.fields.isEmpty() && !dlu.usesSelf) {
            // no instance is needed 😁
            printer.push(ptrType).append(Call.getClassIndexPtr)
            if (comments) printer.comment(synthClassName)
        } else {
            stackPush()
            printer.push(ptrType).append(Call.createInstance)
            if (comments) printer.comment(synthClassName)
            stackPop()
            if (anyMethodThrows) handleThrowable()
        }

        if (fields.isNotEmpty()) {
            val createdInstance = findOrDefineLocalVar(-2, ptrType)
            printer.pop(ptrType)
            printer.append(createdInstance.localSet)

            ///////////////////////////////
            // implement the constructor //
            ///////////////////////////////
            // is this the correct order? should be :)
            for (i in fields.lastIndex downTo 0) {
                val arg = fields[i]
                printer.append(createdInstance.localGet) // instance
                val offset = gIndex.getFieldOffset(synthClassName, "f$i", arg, false)
                if (offset == null) {
                    printUsed(sig)
                    println("constructor-dependency? ${synthClassName in dIndex.constructorDependencies[sig]!!}")
                    throw NullPointerException("Missing $synthClassName.f$i ($arg), constructable? ${synthClassName in dIndex.constructableClasses}")
                }
                if (comments) printer.comment("set field #$i")
                if (alwaysUseFieldCalls) {
                    printer
                        .append(i32Const(offset))
                        .append(getVIOStoreCall(arg))
                } else {
                    printer
                        .append(if (is32Bits) i32Const(offset) else i64Const(offset.toLong()))
                        .append(if (is32Bits) I32Add else I64Add)
                        .append(Call("swap${jvm2wasm(arg)}$ptrType")) // swap ptr and value
                        .append(getStoreInstr2(arg))
                }
                printer.pop(jvm2wasm(arg))
            }

            // get the instance, ready to continue :)
            printer.push(ptrType)
            printer.append(createdInstance.localGet)
        }

    }

    override fun visitJumpInsn(opcode: Int, label: Label) {
        if (printOps) println("  [jump] ${OpCode[opcode]} -> $label")
        when (opcode) {
            // consume two args for comparison
            0x9f -> printer.pop(i32).pop(i32).append(I32EQ)
            0xa0 -> printer.pop(i32).pop(i32).append(I32NE)
            0xa1 -> printer.pop(i32).pop(i32).append(I32LTS)
            0xa2 -> printer.pop(i32).pop(i32).append(I32GES)
            0xa3 -> printer.pop(i32).pop(i32).append(I32GTS)
            0xa4 -> printer.pop(i32).pop(i32).append(I32LES)
            0xa5 -> printer.pop(ptrType).pop(ptrType).append(if (is32Bits) I32EQ else I64EQ)
            0xa6 -> printer.pop(ptrType).pop(ptrType).append(if (is32Bits) I32NE else I64NE)
            0x100 -> {
                // consume one arg for == null
                printer.pop(ptrType).append(I32EQZ)
            }
            0x102 -> printer.pop(ptrType) // consume one arg for != null
            0xc6 -> printer.pop(ptrType).append(I32EQZ) // is null
            0xc7 -> printer.pop(ptrType) // done automatically
            0xa7 -> {} // just goto -> stack doesn't change
            0x99 -> printer.pop(i32).append(I32EQZ) // == 0
            0x9a -> printer.pop(i32) // != 0; done automatically
            0x9b -> printer.pop(i32).append(i32Const0).append(I32LTS) // < 0
            0x9c -> printer.pop(i32).append(i32Const0).append(I32GES) // >= 0
            0x9d -> printer.pop(i32).append(i32Const0).append(I32GTS) // > 0
            0x9e -> printer.pop(i32).append(i32Const0).append(I32LES) // <= 0
            else -> assertFail(OpCode[opcode])
        }
        if (comments) printer.comment("jump ${OpCode[opcode]} -> $label, stack: $stack")
        afterJump(label, opcode == 0xa7)
    }

    private fun afterJump(ifTrue: Label, alwaysTrue: Boolean) {
        val oldNode = currentNode
        // if (printOps) println("setting ifTrue for $oldNode to $label")
        oldNode.ifTrue = ifTrue
        oldNode.isAlwaysTrue = alwaysTrue // goto

        // just in case the next section is missing:
        visitLabel(Label(), alwaysTrue)
        // oldNode.defaultTarget = if (opcode == 0xa7) null else currentNode
    }

    override fun visitLdcInsn(value: Any) {
        // loading constant, value can be any of the constants within the pool:
        // int, float, double, long, string, maybe more
        if (printOps) println("  [ldc] '$value', ${value.javaClass}")
        when (value) {
            true -> printer.push(i32).append(i32Const1)
            false -> printer.push(i32).append(i32Const0)
            is Int -> printer.push(i32).append(i32Const(value))
            is Long -> printer.push(i64).append(i64Const(value))
            is Float -> {
                printer.push(f32)
                if (value.isFinite()) {
                    printer.append(f32Const(value))
                } else {
                    printer.append(i32Const(value.toRawBits()))
                        .append(F32_REINTERPRET_I32)
                }
            }
            is Double -> {
                printer.push(f64)
                if (value.isFinite()) {
                    printer.append(f64Const(value))
                } else {
                    printer.append(i64Const(value.toRawBits()))
                        .append(F64_REINTERPRET_I64)
                }
            }
            is String -> { // pack string into constant memory, and load its address
                printer.push(ptrType)
                val address = gIndex.getString(value)
                printer.append(if (is32Bits) i32Const(address) else i64Const(address.toLong()))
                if (comments) {
                    printer.comment(
                        "\"" + value.shorten(100)
                            .filter { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' || it in " .,-!%/()={[]}" }
                            .toString() + "\""
                    )
                }
            }
            is Type -> {
                printer.push(i32)
                // used for assert() with getClassLoader()
                printer.append(i32Const(gIndex.getClassIndex(single(value.descriptor))))
                printer.append(Call("findClass"))
                if (comments) printer.comment("class $value")
            }
            else -> throw IllegalArgumentException("unknown '$value', ${value.javaClass}\n")
        }
    }

    var line = 0

    @Boring
    override fun visitLineNumber(line: Int, start: Label?) {
        if (printOps) println("Line $line: ($start)")
        if (comments) printer.comment("line $line")
        this.line = line
    }

    private fun findLocalVar(i: Int, wasmType: String): LocalVar {
        var v = localVarsWithParams.firstOrNull { it.index == i && it.wasmType == wasmType }
        if (v != null) return v
        v = findOrDefineLocalVar(i, wasmType)
        // initialize it once at the start... "synthetic local variable" in JDGui
        nodes.first().printer
            .prepend(listOf(Const.zero[wasmType]!!, v.localSet))
        return v
    }

    private fun findOrDefineLocalVar(i: Int, wasmType: String): LocalVar {
        var v = localVarsWithParams.firstOrNull { it.index == i && it.wasmType == wasmType }
        if (v == null) {
            val wasmName = defineLocalVar(i, wasmType)
            v = LocalVar("", wasmType, wasmName, i, false)
            localVarsWithParams.add(v)
        }
        return v
    }

    // name,type -> newName, because types can change in JVM, but they can't in WASM
    private val localVars = HashMap<Pair<Int, String>, String>()

    override fun visitLocalVariable(
        name: String?, descriptor: String, signature: String?,
        start: Label, end: Label, index: Int
    ) {
        // we don't care yet
        /*val wasmType = jvm2wasm1(descriptor)
        val wasmName = defineLocalVar(name, wasmType)
        localVariables.add(LocalVar(descriptor, wasmType, wasmName, start, end, index))
        // this can help with local variables
        if (printOps) println("  local var $name, $descriptor, $signature, $start .. $end, #$index")*/
    }

    private fun defineLocalVar(name: Int, wasmType: String): String {
        return localVars.getOrPut(Pair(name, wasmType)) {
            // register local variable
            val name2 = "l${localVars.size}"
            localVariables1.add(LocalVariable(name2, wasmType))
            name2
        }
    }

    override fun visitMaxs(maxStack: Int, maxLocals: Int) {
        // println(" max stacks: $maxStack, max locals: $maxLocals")
    }

    fun pop(splitArgs: List<String>, static: Boolean, ret: String) {
        for (v in splitArgs.reversed()) {// arguments
            printer.pop(v)
        }
        if (!static) {
            // instance
            printer.pop(ptrType)
        }
        // return type
        if (ret != "V") printer.push(jvm2wasm1(ret))
    }

    fun checkNotNull0(clazz: String, name: String, getCaller: (Builder) -> Unit) {
        if (checkNullPointers) {
            getCaller(printer)
            printer.append(i32Const(gIndex.getString(clazz)))
            printer.append(i32Const(gIndex.getString(name)))
            printer.append(Call("checkNotNull"))
            if (anyMethodThrows) handleThrowable()
        }
    }

    override fun visitMethodInsn(
        opcode0: Int,
        owner0: String,
        name: String,
        descriptor: String,
        isInterface: Boolean
    ) {
        val owner = replaceClass1(owner0)
        visitMethodInsn2(opcode0, owner, name, descriptor, isInterface, true)
    }

    fun visitMethodInsn2(
        opcode0: Int,
        owner0: String,
        name: String,
        descriptor: String,
        isInterface: Boolean,
        checkThrowable: Boolean
    ): Boolean {

        val owner = replaceClass1(owner0)

        if (printOps) println("  [call] ${OpCode[opcode0]}, $owner, $name, $descriptor, $isInterface")

        val i = descriptor.lastIndexOf(')')
        val args = descriptor.substring(1, i)
        val ret = descriptor.substring(i + 1)
        val splitArgs = split1(args).map { jvm2wasm(it) }
        val static = opcode0 == 0xb8
        val sig0 = MethodSig.c(owner, name, descriptor)
        if (static != (sig0 in hIndex.staticMethods))
            throw RuntimeException("Called static/non-static incorrectly, $static vs $sig0 (in $sig)")


        val sig = hIndex.getAlias(sig0)

        val methodsByOwner = hIndex.methods.getOrPut(owner) { HashSet() }
        if (sig == sig0 && methodsByOwner.add(sig0) && static) {
            hIndex.staticMethods.add(sig0)
        }

        var calledCanThrow = canThrowError(sig)

        fun getCaller(printer: Builder) {
            if (splitArgs.isNotEmpty()) {
                printer.append(Call(gIndex.getNth(listOf(ptrType) + splitArgs)))
            } else printer.dupI32()
        }

        when (opcode0) {
            0xb9 -> {
                val variants = getMethodVariants(sig0)
                if (!resolveIndirect(sig0, splitArgs, ret, variants, ::getCaller, calledCanThrow, owner)) {
                    // invoke interface
                    // load interface/function index
                    getCaller(printer)
                    printer.append(i32Const(gIndex.getInterfaceIndex(InterfaceSig.c(name, descriptor))))
                    // looks up class, goes to interface list, binary searches function, returns func-ptr
                    // instance, function index -> instance, function-ptr
                    stackPush()
                    printer.push(i32).append(Call("resolveInterface"))
                    stackPop() // so we can track the call better
                    if (anyMethodThrows) handleThrowable() // if it's not found or nullptr
                    printer.pop(i32) // pop instance
                    pop(splitArgs, false, ret)

                    stackPush()

                    printer.append(CallIndirect(gIndex.getType(false, descriptor, calledCanThrow)))
                    ActuallyUsedIndex.add(this.sig, sig)
                    if (comments) printer.comment("invoke interface $owner, $name, $descriptor")

                    stackPop()
                }
            }
            0xb6 -> { // invoke virtual
                if (owner[0] !in "[A" && owner !in dIndex.constructableClasses) {

                    stackPush()
                    getCaller(printer)

                    printer.append(i32Const(gIndex.getString(methodName(sig))))
                        // instance, function index -> function-ptr
                        .comment("not constructable class, $sig, $owner, $name, $descriptor")
                        .append(Call.resolveIndirectFail)
                        .append(Unreachable)

                    pop(splitArgs, false, ret)
                    stackPop()

                } else if (sig0 in hIndex.finalMethods) {

                    val sigs = getMethodVariants(sig0)
                    assertTrue(sigs.size < 2) { "Unclear $sig0 -> $sig?" }
                    val sig1 = sigs.firstOrNull() ?: sig

                    val setter = hIndex.setterMethods[sig1]
                    val getter = hIndex.getterMethods[sig1]

                    fun isStatic(field: FieldSig): Boolean {
                        return field.name in gIndex.getFieldOffsets(field.clazz, true).fields
                    }

                    when {
                        setter != null -> {
                            visitFieldInsn2(
                                if (isStatic(setter)) 0xb3 else 0xb5,
                                setter.clazz, setter.name, setter.descriptor, true
                            )
                            calledCanThrow = false
                        }
                        getter != null -> {
                            visitFieldInsn2(
                                if (isStatic(getter)) 0xb2 else 0xb4,
                                getter.clazz, getter.name, getter.descriptor, true
                            )
                            calledCanThrow = false
                        }
                        else -> {
                            if (!ignoreNonCriticalNullPointers) {
                                checkNotNull0(owner, name, ::getCaller)
                            }
                            pop(splitArgs, false, ret)
                            // final, so not actually virtual;
                            // can be directly called
                            val inline = hIndex.inlined[sig1]
                            if (inline != null) {
                                printer.append(inline)
                                printer.comment("virtual-inlined $sig1")
                            } else {
                                stackPush()
                                val name2 = methodName(sig1)
                                val name3 = methodName(sig0)
                                if (name3 == "java_lang_Object_hashCode_I" ||
                                    name3 == "java_util_function_Consumer_accept_Ljava_lang_ObjectV_accept_JV" ||
                                    name3 == "me_anno_gpu_OSWindow_addCallbacks_V"
                                ) throw IllegalStateException("$sig0 -> $sig1 must not be final!!!")
                                if (sig1 in hIndex.abstractMethods) throw IllegalStateException()
                                ActuallyUsedIndex.add(this.sig, sig1)
                                printer.append(Call(name2))
                                stackPop()
                            }
                        }
                    }
                } else {
                    val options = getMethodVariants(sig0)
                    if (!resolveIndirect(sig0, splitArgs, ret, options, ::getCaller, calledCanThrow, owner)) {
                        // method can have well-defined place in class :) -> just precalculate that index
                        // looks up the class, and in the class-function lut, it looks up the function ptr
                        // get the Nth element on the stack, where N = |args|
                        // problem: we don't have generic functions, so we need all combinations
                        getCaller(printer)
                        // +1 for internal VM offset
                        // << 2 for access without shifting
                        // println("$clazz/${this.name}/${this.descriptor} -> $sig0 -> $sig")
                        // printUsed(MethodSig(clazz, this.name, this.descriptor))
                        stackPush()
                        val funcPtr = (gIndex.getDynMethodIdx(sig0) + 1) shl 2
                        printer.append(i32Const(funcPtr))
                            // instance, function index -> function-ptr
                            .append(Call.resolveIndirect)
                            .comment("$sig0, #${options.size}")
                            .push(i32)
                        stackPop()
                        if (anyMethodThrows) handleThrowable()
                        printer.pop(i32)
                        pop(splitArgs, false, ret)
                        printer.append(CallIndirect(gIndex.getType(false, descriptor, calledCanThrow)))
                        if (comments) printer.comment("invoke virtual $owner, $name, $descriptor")
                        ActuallyUsedIndex.add(this.sig, sig)
                    }
                }
            }
            // typically, <init>, but also can be private or super function; -> no resolution required
            0xb7 -> {
                if (!ignoreNonCriticalNullPointers) {
                    checkNotNull0(owner, name, ::getCaller)
                }
                pop(splitArgs, false, ret)
                val inline = hIndex.inlined[sig]
                if (inline != null) {
                    printer.append(inline)
                    printer.comment("special-inlined $sig")
                } else {
                    stackPush()
                    val name2 = methodName(sig)
                    assertFalse(sig in hIndex.abstractMethods)
                    ActuallyUsedIndex.add(this.sig, sig)
                    printer.append(Call(name2))
                    stackPop()
                }
            }
            // static, no resolution required
            0xb8 -> {
                pop(splitArgs, true, ret)
                val inline = hIndex.inlined[sig]
                if (inline != null) {
                    printer.append(inline)
                    printer.comment("static-inlined $sig")
                } else {
                    stackPush()
                    val name2 = methodName(sig)
                    assertFalse(sig in hIndex.abstractMethods)
                    ActuallyUsedIndex.add(this.sig, sig)
                    printer.append(Call(name2))
                    if (comments) printer.comment("static call")
                    stackPop()
                }
            }
            else -> throw NotImplementedError("unknown call ${OpCode[opcode0]}, $owner, $name, $descriptor, $isInterface\n")
        }

        if (calledCanThrow && checkThrowable) {
            handleThrowable()
        }

        return calledCanThrow
    }

    fun stackPush() {
        if (canPush) {
            printer.append(i32Const(getCallIndex())).append(Call("stackPush"))
        }
    }

    fun stackPop() {
        if (canPush) {
            printer.append(Call("stackPop"))
        }
    }

    @Boring
    override fun visitLocalVariableAnnotation(
        typeRef: Int,
        typePath: TypePath?,
        start: Array<out Label>?,
        end: Array<out Label>?,
        index: IntArray?,
        descriptor: String?,
        visible: Boolean
    ): AnnotationVisitor? {
        return null
    }

    override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) {

        // create multi-dimensional array
        // size > 0 => initialize child dimensions as well
        // size == 0 => initialize it to null

        // remove [[, then read the type
        val type = when (descriptor.substring(numDimensions)) {
            "Z" -> "[Z"
            "C" -> "[C"
            "F" -> "[F"
            "D" -> "[D"
            "B" -> "[B"
            "S" -> "[S"
            "I" -> "[I"
            "J" -> "[J"
            else -> "[]"
        }

        if (printOps) println("  [newMultiA] $descriptor, $numDimensions -> $type")

        // adjust stack
        stackPush()
        for (i in 0 until numDimensions) printer.pop(i32)
        printer.push(ptrType)
            .append(i32Const(gIndex.getClassIndex(type)))
            .append(Call.createNativeArray[numDimensions])

        stackPop()
        if (anyMethodThrows) handleThrowable()

        if (numDimensions > 6)
            throw NotImplementedError("Multidimensional arrays with more than 5 dimensions haven't been implemented yet.")

    }

    override fun visitParameter(name: String?, access: Int) {
        if (printOps) println("  parameter $name $access")
    }

    // could be interesting for nullability :)
    override fun visitParameterAnnotation(
        parameter: Int,
        descriptor: String?,
        visible: Boolean
    ): AnnotationVisitor? {
        if (printOps) println("  param annotation $parameter, $descriptor, $visible")
        return null
    }

    override fun visitTableSwitchInsn(min: Int, max: Int, default: Label, vararg labels: Label) {
        // implement this in wasm text
        // println("  [table] switch $min .. $max, $default, [${labels.joinToString()}]")
        // printer.append(" ;; table\n")
        visitLookupSwitchInsn(default, IntArray(max - min + 1) { it + min }, labels)
    }

    override fun visitLookupSwitchInsn(default: Label, keys: IntArray, labels: Array<out Label>) {
        // implement this in wasm text
        // and implement this possible to be decoded as a tree:
        // we replace it for now with standard instructions
        if (printOps) println("  [lookup] switch $default, [${keys.joinToString()}], [${labels.joinToString()}]")
        val helper = findOrDefineLocalVar(Int.MAX_VALUE, i32)
        printer.pop(i32)
        printer.append(helper.localSet)
        for (i in keys.indices) {
            printer.append(helper.localGet).append(i32Const(keys[i])).append(I32EQ)
            afterJump(labels[i], false)
        }
        afterJump(default, true)
    }

    override fun visitTryCatchAnnotation(
        typeRef: Int,
        typePath: TypePath?,
        descriptor: String?,
        visible: Boolean
    ): AnnotationVisitor? {
        if (printOps) println("  try catch annotation? $typeRef, $typePath, $descriptor, $visible")
        return null
    }

    private val catchers = ArrayList<Catcher>()

    data class Catcher(val start: Label, val end: Label, val handler: Label, val type: String?)

    override fun visitTryCatchBlock(start: Label, end: Label, handler: Label, type: String?) {
        catchers.add(Catcher(start, end, handler, type))
        if (comments) printer.comment("try-catch $start .. $end, handler $handler, type $type")
        if (printOps) println("  ;; try-catch $start .. $end, handler $handler, type $type")
    }

    private var thIndex = -10
    fun handleThrowable(mustThrow: Boolean = false) {

        if (useWASMExceptions) {
            // todo catch exceptions to handle them...
            if (mustThrow) {
                // throw :)
                printer.append(Throw("exTag"))
                printer.append(Unreachable)
            } else {
                // nothing will be here :)
            }
            return
        }

        if (!canThrowError) {
            printer.append(Call("panic"))
            if (mustThrow) {
                printer.append(Unreachable)
            }
            return
        }

        val catchers = catchers.filter { c ->
            nodes.any { it.label == c.start } && nodes.none { it.label == c.end }
        }
        if (catchers.isNotEmpty()) {

            if (printOps) println("found catcher $catchers")
            if (comments) printer.comment("found catcher")

            val oldStack = ArrayList(stack)

            if (catchers.size > 1 || (catchers[0].type != null && catchers[0].type != "java/lang/Throwable")) {

                val throwable = findOrDefineLocalVar(thIndex--, ptrType)
                printer.append(throwable.localSet).comment("multiple/complex catchers")

                var handler = TranslatorNode(Label())
                nodes.add(handler)

                if (mustThrow) {
                    visitJumpInsn(0xa7, handler.label) // jump to handler
                } else {
                    printer.push(ptrType).append(throwable.localGet)
                    visitJumpInsn(0x102, handler.label) // if not null, jump to handler
                }

                handler.inputStack = ArrayList(stack)
                handler.outputStack = listOf(ptrType)

                for ((i, catcher) in catchers.withIndex()) {

                    if (i == 0) {
                        for (j in stack.indices) {
                            handler.printer.drop()
                        }
                        handler.printer.append(throwable.localGet)
                    }

                    if (catcher.type == null) {
                        handler.ifFalse = null
                        handler.isAlwaysTrue = true
                        handler.ifTrue = catcher.handler
                        return
                    } else {

                        // if condition
                        // throwable -> throwable, throwable, int - instanceOf > throwable, jump-condition
                        handler.printer.dupI32()
                        handler.printer.appendInstanceOf(catcher.type)
                        if (comments) handler.printer.comment("handler #$i/${catchers.size}/$throwable")

                        // actual branch
                        val nextHandler = TranslatorNode(Label())
                        nodes.add(nextHandler)
                        handler.ifFalse = nextHandler
                        handler.ifTrue = catcher.handler

                        handler = nextHandler
                        nextHandler.inputStack = listOf(ptrType)
                        nextHandler.outputStack = listOf(ptrType)
                    }
                }

                // handle if all failed: throw error
                if (comments) handler.printer.comment("must throw")
                returnIfThrowable(true, handler)

            } else {

                if (mustThrow) {

                    if (comments) printer.comment("throwing single generic catcher")

                    if (printOps) println("--- handler: ${currentNode.label}")

                    val currentNode = currentNode
                    if (stack.isNotEmpty()) {
                        for (j in stack.indices) { // don't drop exception
                            printer.drop()
                        }
                    }

                    visitLabel(Label(), true)

                    currentNode.isAlwaysTrue = true
                    currentNode.ifTrue = catchers[0].handler
                    currentNode.outputStack = listOf(ptrType)

                } else {

                    printer.comment("maybe throwing single generic catcher")

                    val mainHandler = TranslatorNode(Label())
                    val throwable = findOrDefineLocalVar(thIndex--, ptrType)

                    if (printOps) println("--- handler: ${mainHandler.label}")

                    printer.push(ptrType)
                    printer.append(throwable.localSet)
                    printer.append(throwable.localGet)
                    visitJumpInsn(0x9a, mainHandler.label) // if not null

                    mainHandler.inputStack = ArrayList(stack)
                    mainHandler.outputStack = listOf(ptrType)
                    for (e in stack.indices) mainHandler.printer.drop()
                    mainHandler.printer.append(throwable.localGet)
                    mainHandler.isAlwaysTrue = true
                    mainHandler.ifTrue = catchers[0].handler
                    nodes.add(mainHandler)
                }
            }

            stack.clear()
            stack.addAll(oldStack)

        } else {
            if (comments) {
                printer.comment(
                    if (mustThrow) "no catcher was found, returning"
                    else "no catcher was found, returning maybe"
                )
            }
            // easy, inline, without graph :)
            returnIfThrowable(mustThrow)
        }
    }

    private fun returnIfThrowable(mustThrow: Boolean, currentNode: TranslatorNode = this.currentNode) {
        val printer = currentNode.printer
        if (mustThrow) {
            if (resultType != 'V') {
                val retType = jvm2wasm1(resultType)
                printer.append(Const.zero[retType]!!)
                    .append(Call("swapi32$retType"))
            }
            printer.append(Return)
        } else {
            val tmp = tmpI32
            printer.append(tmp.localSet)
            printer.append(tmp.localGet)
            val ifTrue = if (resultType == 'V') {
                listOf(tmp.localGet, Return)
            } else {
                val zeroResult = Const.zero[jvm2wasm1(resultType)]!!
                listOf(zeroResult, tmp.localGet, Return)
            }
            printer.append(IfBranch(ifTrue, emptyList(), emptyList(), emptyList()))
        }
    }

    val tmpI32 by lazy { findOrDefineLocalVar(-3, i32) }

    override fun visitTypeAnnotation(
        typeRef: Int,
        typePath: TypePath?,
        descriptor: String?,
        visible: Boolean
    ): AnnotationVisitor? {
        println("  type annotation $typeRef, $typePath, $descriptor, $visible")
        return null
    }

    override fun visitTypeInsn(opcode: Int, type0: String) {
        val type = replaceClass1(type0)
        if (printOps) println("  [${OpCode[opcode]}] $type")
        when (opcode) {
            0xbb -> {
                // new instance
                stackPush()
                printer.push(ptrType)
                    .append(i32Const(gIndex.getClassIndex(type)))
                    .append(Call.createInstance)
                if (comments) printer.comment(type)
                stackPop()
                if (anyMethodThrows) handleThrowable()
            }
            0xbd -> {
                // a-new array, type doesn't matter
                stackPush()
                printer.pop(i32).push(ptrType)
                printer.append(Call.createObjectArray)
                if (comments) printer.comment(type)
                stackPop()
                if (anyMethodThrows) handleThrowable()
            }
            0xc0 -> {
                // check cast
                if (checkClassCasts) {
                    stackPush()
                    printer.pop(ptrType).push(ptrType)
                    printer.printCastClass(type)
                    if (comments) printer.comment(type)
                    stackPop()
                    if (anyMethodThrows) handleThrowable()
                }
            }
            0xc1 -> {
                // instance of
                printer.pop(ptrType).push(i32)
                printer.appendInstanceOf(type)
                if (comments) printer.comment(type)
            }
            else -> throw NotImplementedError("[type] unknown ${OpCode[opcode]}, $type\n")
        }
    }

    private fun Builder.printCastClass(clazz: String) {
        append(i32Const(gIndex.getClassIndex(clazz)))
        append(if (hasChildClasses(clazz)) Call.checkCast else Call.checkCastExact)
    }

    private fun Builder.appendInstanceOf(clazz: String) {
        if (clazz in dIndex.constructableClasses) {
            append(i32Const(gIndex.getClassIndex(clazz)))
            append(
                if (hasChildClasses(clazz)) {
                    if (hIndex.isInterfaceClass(clazz)) Call.instanceOf
                    else Call.instanceOfNonInterface
                } else Call.instanceOfExact
            )
        } else {
            append(Drop).append(i32Const0)
        }
    }

    private fun hasChildClasses(clazz: String): Boolean {
        val children = hIndex.childClasses[clazz] ?: return false
        return children.any { it in dIndex.constructableClasses }
    }

    override fun visitVarInsn(opcode: Int, varIndex: Int) {
        visitVarInsn2(opcode, varIndex, argsMapping[varIndex])
    }

    fun visitVarInsn2(opcode: Int, varIndex: Int, map: Int?) {
        if (printOps) println("  [var] local ${OpCode[opcode]}, $varIndex, $map")
        when (opcode) {
            0x15, in 0x1a..0x1d -> printer.push(i32) // iload
            0x16, in 0x1e..0x21 -> printer.push(i64) // lload
            0x17, in 0x22..0x25 -> printer.push(f32) // fload
            0x18, in 0x26..0x29 -> printer.push(f64) // dload
            0x19, in 0x2a..0x2d -> printer.push(ptrType) // aload
            0x36, in 0x3b..0x3e -> printer.pop(i32) // istore
            0x37, in 0x3f..0x42 -> printer.pop(i64) // lstore
            0x38, in 0x43..0x46 -> printer.pop(f32) // fstore
            0x39, in 0x47..0x4a -> printer.pop(f64) // dstore
            0x3a, in 0x4b..0x4e -> printer.pop(ptrType) // astore
            else -> assertFail()
        }
        if (map != null) {
            when (opcode) {
                in 0x15..0x2d -> printer.append(ParamGet[map]) // iload, loads local variable at varIndex
                in 0x36..0x4e -> printer.append(ParamSet[map]) // istore
                else -> assertFail()
            }
        } else {
            val varName = when (opcode) {
                0x15, in 0x1a..0x1d -> findLocalVar(varIndex, i32)
                0x16, in 0x1e..0x21 -> findLocalVar(varIndex, i64)
                0x17, in 0x22..0x25 -> findLocalVar(varIndex, f32)
                0x18, in 0x26..0x29 -> findLocalVar(varIndex, f64)
                0x19, in 0x2a..0x2d -> findLocalVar(varIndex, ptrType)
                0x36, in 0x3b..0x3e -> findOrDefineLocalVar(varIndex, i32)
                0x37, in 0x3f..0x42 -> findOrDefineLocalVar(varIndex, i64)
                0x38, in 0x43..0x46 -> findOrDefineLocalVar(varIndex, f32)
                0x39, in 0x47..0x4a -> findOrDefineLocalVar(varIndex, f64)
                0x3a, in 0x4b..0x4e -> findOrDefineLocalVar(varIndex, ptrType)
                else -> assertFail()
            }
            printer.append(if (opcode <= 0x2d) varName.localGet else varName.localSet)
        }
    }

    override fun visitIntInsn(opcode: Int, operand: Int) {
        if (printOps) println("  [intInsn] ${OpCode[opcode]}($operand)")
        when (opcode) {
            0x10 -> printer.push(i32).append(i32Const(operand))
            0x11 -> printer.push(i32).append(i32Const(operand))
            0xbc -> { // new array
                val type = when (operand) {
                    4 -> "[Z"
                    5 -> "[C"
                    6 -> "[F"
                    7 -> "[D"
                    8 -> "[B"
                    9 -> "[S"
                    10 -> "[I"
                    11 -> "[J"
                    else -> throw IllegalArgumentException()
                }
                stackPush()
                printer
                    .pop(i32).push(ptrType)
                    .append(i32Const(gIndex.getClassIndex(type)))
                    .append(Call.createNativeArray[1]).comment(type)
                stackPop()
                if (anyMethodThrows) handleThrowable()
            }
            else -> assertFail()
        }
    }

    override fun visitLabel(label: Label) {
        visitLabel(label, false)
    }

    private fun visitLabel(label: Label, findStackManually: Boolean) {

        val currNode = currentNode
        val nextNode = TranslatorNode(label)

        currNode.outputStack = ArrayList(stack)

        var found = false
        if (findStackManually/* || (currNode.hasNoCode())*/) {
            // find whether we had a good candidate in the past
            for (node in nodes) { // O(n²) -> potentially very slow :/
                if (node.ifTrue == label || node.ifFalse?.label == label) {
                    nextNode.inputStack = node.outputStack
                    // println("found $label :), $stack -> ${node.outputStack}")
                    stack.clear()
                    stack.addAll(node.outputStack)
                    found = true
                    break
                }
            }
            // if (!found) println("didn't find $label :/")
        }

        if (!found) {
            nextNode.inputStack = ArrayList(stack)
        }

        nodes.add(nextNode)
        currNode.ifFalse = nextNode

        currentNode = nextNode
        printer = nextNode.printer

        if (printOps) println(" [label] $label")
        if (comments) printer.comment(label.toString())
    }

    private fun getLoadInstr(descriptor: String): Instruction = when (single(descriptor)) {
        "Z", "B" -> I32Load8S
        "S" -> I32Load16S
        "C" -> I32Load16U
        "I" -> I32Load
        "J" -> I64Load
        "F" -> F32Load
        "D" -> F64Load
        else -> if (is32Bits) I32Load else I64Load
    }

    private fun getStoreInstr(descriptor: String) = getStoreInstr2(single(descriptor))

    private fun getStoreInstr2(descriptor: String): Instruction = when (descriptor) {
        "Z", "B" -> I32Store8
        "S", "C" -> I32Store16
        "I" -> I32Store
        "J" -> I64Store
        "F" -> F32Store
        "D" -> F64Store
        else -> if (is32Bits) I32Store else I64Store
    }

    private fun getStaticLoadCall(descriptor: String): Instruction = when (descriptor) {
        "Z", "B" -> Call.getStaticFieldS8
        "S" -> Call.getStaticFieldS16
        "C" -> Call.getStaticFieldU16
        "I" -> Call.getStaticFieldI32
        "J" -> Call.getStaticFieldI64
        "F" -> Call.getStaticFieldF32
        "D" -> Call.getStaticFieldF64
        else -> if (is32Bits) Call.getStaticFieldI32 else Call.getStaticFieldI64
    }

    private fun getLoadCall(descriptor: String): Instruction = when (single(descriptor)) {
        "Z", "B" -> Call.getFieldS8
        "S" -> Call.getFieldS16
        "C" -> Call.getFieldU16
        "I" -> Call.getFieldI32
        "J" -> Call.getFieldI64
        "F" -> Call.getFieldF32
        "D" -> Call.getFieldF64
        else -> if (is32Bits) Call.getFieldI32 else Call.getFieldI64
    }

    private fun getStaticStoreCall(descriptor: String): Instruction = when (descriptor) {
        "Z", "B" -> Call.setStaticFieldI8
        "S", "C" -> Call.setStaticFieldI16
        "I" -> Call.setStaticFieldI32
        "J" -> Call.setStaticFieldI64
        "F" -> Call.setStaticFieldF32
        "D" -> Call.setStaticFieldF64
        else -> if (is32Bits) Call.setStaticFieldI32 else Call.setStaticFieldI64
    }

    private fun getStoreCall(descriptor: String): Instruction = when (descriptor) {
        "Z", "B" -> Call.setFieldI8
        "S", "C" -> Call.setFieldI16
        "I" -> Call.setFieldI32
        "J" -> Call.setFieldI64
        "F" -> Call.setFieldF32
        "D" -> Call.setFieldF64
        else -> if (is32Bits) Call.setFieldI32 else Call.setFieldI64
    }

    private fun getVIOStoreCall(descriptor: String): Instruction = when (descriptor) {
        "Z", "B" -> Call.setVIOFieldI8
        "S", "C" -> Call.setVIOFieldI16
        "I" -> Call.setVIOFieldI32
        "J" -> Call.setVIOFieldI64
        "F" -> Call.setVIOFieldF32
        "D" -> Call.setVIOFieldF64
        else -> if (is32Bits) Call.setVIOFieldI32 else Call.setVIOFieldI64
    }

    private fun callClinit(clazz: String) {
        if (name == "<clinit>" && clazz == this.clazz) {
            if (comments) printer.comment("skipped <clinit>, we're inside of it")
            return
        } // it's currently being inited :)
        val sig = MethodSig.c(clazz, "<clinit>", "()V")
        if (hIndex.methods[clazz]?.contains(sig) != true) {
            if (comments) printer.comment("skipped <clinit>, because empty")
            return
        }
        stackPush()
        printer.append(Call(methodName(sig)))
        ActuallyUsedIndex.add(this.sig, sig)
        stackPop()
        if (anyMethodThrows && canThrowError(sig)) {
            handleThrowable()
        }
    }

    private val precalculateStaticFields = true
    override fun visitFieldInsn(opcode: Int, owner0: String, name: String, descriptor: String) {
        visitFieldInsn2(opcode, replaceClass1(owner0), name, descriptor, true)
    }

    private fun Builder.dupI32(): Builder {
        return dupI32(tmpI32)
    }

    fun visitFieldInsn2(opcode: Int, owner: String, name: String, descriptor: String, checkNull: Boolean) {
        // get offset to field, and store value there
        // if static, find field first... static[]
        // getstatic, putstatic, getfield, putfield
        if (printOps) println("  [field] ${OpCode[opcode]}, $owner, $name, $descriptor")
        val wasmType = jvm2wasm1(descriptor)
        val static = opcode <= 0xb3
        val fieldOffset = gIndex.getFieldOffset(owner, name, descriptor, static)
        val sig = FieldSig(owner, name, descriptor, static)
        val value = hIndex.finalFields[sig]
        val setter = opcode == 0xb3 || opcode == 0xb5
        if (value != null) {
            if (!checkNull) throw IllegalStateException("Field $owner,$name,$descriptor is final")
            if (setter) {
                printer.pop(stack.last()) // pop value
            }
            if (!static) { // drop instance
                printer.pop(ptrType)
                printer.drop()
            }
            if (setter) { // setter
                // drop value
                printer.drop()
                if (comments) printer.comment("final $sig")
            } else { // getter
                visitLdcInsn(value) // too easy xD
            }
        } else when (opcode) {
            0xb2 -> {
                // get static
                if (name in enumFieldsNames) {
                    callClinit(owner)
                    printer.append(i32Const(gIndex.getClassIndex(owner)))
                        .append(Call("findClass"))
                        .append(
                            i32Const(
                                gIndex.getFieldOffset(
                                    "java/lang/Class",
                                    "enumConstants",
                                    "[Ljava/lang/Object;",
                                    false
                                )!!
                            )
                        )
                    printer.append(I32Add).append(I32Load).comment("enum values")
                    printer.push(wasmType)
                } else if (fieldOffset != null) {
                    callClinit(owner)
                    printer.push(wasmType)
                    // load class index
                    if (precalculateStaticFields) {
                        val staticPtr = staticLookup[owner]!! + fieldOffset
                        printer.append(i32Const(staticPtr))
                    } else {
                        printer
                            .append(i32Const(gIndex.getClassIndex(owner)))
                            .append(i32Const(fieldOffset))
                            // class index, field offset -> static value
                            .append(Call("findStatic"))
                    }
                    if (alwaysUseFieldCalls) {
                        printer.append(getStaticLoadCall(descriptor))
                    } else {
                        printer.append(getLoadInstr(descriptor))
                    }
                    if (comments) printer.comment("get static '$owner.$name'")
                } else {
                    printer.push(wasmType).append(Const.zero[wasmType]!!)
                }
            }
            0xb3 -> {
                // put static
                if (name in enumFieldsNames) {
                    printer
                        .append(i32Const(gIndex.getClassIndex(owner)))
                        .append(Call("findClass"))
                        .append(
                            i32Const(
                                gIndex.getFieldOffset(
                                    "java/lang/Class",
                                    "enumConstants",
                                    "[Ljava/lang/Object;",
                                    false
                                )!!
                            )
                        )
                    printer.append(I32Add).append(Call("swapi32i32")).append(I32Store)
                    printer.comment("enum values")
                    printer.pop(wasmType)
                } else if (fieldOffset != null) {
                    callClinit(owner)
                    printer.pop(wasmType)
                    if (precalculateStaticFields) {
                        val staticPtr = staticLookup[owner]!! + fieldOffset
                        printer.append(i32Const(staticPtr))
                    } else {
                        printer
                            .append(i32Const(gIndex.getClassIndex(owner)))
                            .append(i32Const(fieldOffset))
                            // ;; class index, field offset -> static value
                            .append(Call.findStatic)
                    }
                    printer.append(getStaticStoreCall(descriptor))
                    if (comments) printer.comment("put static '$owner.$name'")
                } else {
                    printer.pop(wasmType)
                    printer.drop()
                }
            }
            0xb4 -> {
                // get field
                // second part of check is <self>
                if (comments) {
                    printer.comment(
                        if (fieldOffset != null) {
                            if (owner == clazz) "get field '$name'"
                            else "get field '$owner.$name'"
                        } else "dropped getting $name"
                    )
                }
                if (checkNull && !(!isStatic && printer.endsWith(ParamGet[0]))) {
                    checkNotNull0(owner, name) {
                        printer.dupI32()
                    }
                }
                printer.pop(ptrType).push(wasmType)
                if (fieldOffset != null) {
                    printer.append(i32Const(fieldOffset))
                    if (alwaysUseFieldCalls) {
                        printer
                            .append(getLoadCall(descriptor))
                    } else {
                        printer
                            .append(I32Add)
                            .append(getLoadInstr(descriptor))
                    }
                } else {
                    printer
                        .drop()
                        .append(Const.zero[wasmType]!!)
                }
            }
            0xb5 -> {
                // set field
                // second part of check is <self>
                if (comments) {
                    printer.comment(
                        if (fieldOffset != null) {
                            if (owner == clazz) "set field '$name'"
                            else "set field '$owner.$name'"
                        } else "dropped putting $name"
                    )
                }
                if (checkNull &&
                    !(!isStatic && printer.endsWith(listOf(ParamGet[0], ParamGet[1]))) &&
                    !(!isStatic && printer.endsWith(listOf(ParamGet[0], i32Const0)))
                ) {
                    checkNotNull0(owner, name) {
                        printer.append(Call(gIndex.getNth(listOf(ptrType, wasmType))))
                    }
                }
                printer.pop(wasmType).pop(ptrType)
                if (fieldOffset != null) {
                    // if endsWith local.get x2,
                    //  then optimize this to not use swaps
                    if (!alwaysUseFieldCalls && printer.endsWith(localGetX2Suffix)) {
                        printer.drop().drop()
                        printer.append(ParamGet[0])
                            .append(i32Const(fieldOffset))
                            .append(I32Add).append(ParamGet[1])
                            .append(getStoreInstr(descriptor))
                    } else {
                        // we'd need to call a function twice, so call a generic functions for this
                        printer.append(i32Const(fieldOffset))
                            .append(getStoreCall(descriptor))
                    }
                } else {
                    printer.drop().drop()
                }
            }
            else -> throw NotImplementedError(OpCode[opcode])
        }
    }

    private val localGetX2Suffix = listOf(ParamGet[0], ParamGet[1])

    override fun visitEnd() {
        if (!isAbstract) {
            for (node in nodes) {
                node.isReturn = node.printer.lastOrNull()?.isReturning() ?: false
            }
            val nodes = TranslatorNode.convertNodes(nodes)
            validateInputOutputStacks(nodes, sig)
            if (true) {
                StackValidator.validateStack(nodes, this)
            }
            val jointBuilder = StructuralAnalysis(this, nodes).joinNodes()
            optimizeUsingReplacements(jointBuilder)
            val headPrinter1 = createFuncHead()
            gIndex.translatedMethods[sig] = FunctionImpl(
                headPrinter1.funcName, headPrinter1.params, headPrinter1.results,
                headPrinter1.locals, jointBuilder.instrs,
                headPrinter1.isExported
            )
        }
    }

    private var lastLine = -1
    private fun getCallIndex(): Int {
        if (line != lastLine) {
            lastLine = line
            callTable.writeLE32(gIndex.getString(sig.clazz))
            callTable.writeLE32(gIndex.getString(sig.name))
            callTable.writeLE32(line)
        }
        return callTable.size() / 12 - 1
    }

    companion object {
        val callTable = ByteArrayOutputStream2(1024)
        val enumFieldsNames = listOf("\$VALUES", "ENUM\$VALUES")

        private var printOps = false
        private var comments = true
    }

}