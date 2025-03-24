package translator

import alwaysUseFieldCalls
import annotations.Boring
import annotations.NotCalled
import api
import canThrowError
import checkArrayAccess
import checkClassCasts
import checkIntDivisions
import checkNullPointers
import crashOnAllExceptions
import dIndex
import dependency.ActuallyUsedIndex
import enableTracing
import exportAll
import gIndex
import graphing.StackValidator.validateInputOutputStacks
import graphing.StackValidator.validateStack
import graphing.StructuralAnalysis
import hIndex
import hierarchy.DelayedLambdaUpdate
import hierarchy.DelayedLambdaUpdate.Companion.getSynthClassName
import ignoreNonCriticalNullPointers
import me.anno.io.Streams.writeLE32
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertFail
import me.anno.utils.assertions.assertFalse
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.lists.Lists.pop
import me.anno.utils.types.Booleans.hasFlag
import me.anno.utils.types.Booleans.toInt
import me.anno.utils.types.Strings.shorten
import org.apache.logging.log4j.LogManager
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*
import replaceClass
import translator.ResolveIndirect.resolveIndirect
import useResultForThrowables
import useWASMExceptions
import utils.*
import utils.Builder.Companion.isDuplicable
import utils.CommonInstructions.ARRAY_LENGTH_INSTR
import utils.CommonInstructions.ATHROW_INSTR
import utils.CommonInstructions.GET_FIELD
import utils.CommonInstructions.GET_STATIC
import utils.CommonInstructions.INVOKE_INTERFACE
import utils.CommonInstructions.INVOKE_SPECIAL
import utils.CommonInstructions.INVOKE_STATIC
import utils.CommonInstructions.INVOKE_VIRTUAL
import utils.CommonInstructions.MONITOR_ENTER
import utils.CommonInstructions.MONITOR_EXIT
import utils.CommonInstructions.NEW_INSTR
import utils.CommonInstructions.SET_FIELD
import utils.CommonInstructions.SET_STATIC
import utils.PrintUsed.printUsed
import utils.ReplaceOptimizer.optimizeUsingReplacements
import utils.WASMTypes.*
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
import wasm.instr.Instructions.I64EQZ
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
    val descriptor: Descriptor,
) : MethodVisitor(api) {

    companion object {

        private val LOGGER = LogManager.getLogger(MethodTranslator::class)

        val stackTraceTable = ByteArrayOutputStream2(1024)
        val enumFieldsNames = listOf("\$VALUES", "ENUM\$VALUES")

        var comments = true
        var renameVariables = true

        private var printOps = false
        private var commentStackOps = false

        private val notStackPushedMethods = listOf(
            "stackPush", "stackPop", "printStackTraceLine",
            "createGCFieldTable", "findFieldsByClass", "setStackTrace",
            "calloc", "findGap", "allocateNewSpace", "writeClass",
            "Throwable_printStackTrace_V", "getStackDepth",
            "instanceOfExact", "instanceOfNonInterface", "isChildOrSameClassNonInterface",
            "resolveInterface", "resolveInterfaceByClass",
            "readPtrAtOffset",
            "new_java_lang_String_ACIIV"
        )

        @Suppress("UNUSED_PARAMETER")
        fun isLookingAtSpecial(sig: MethodSig): Boolean {
            // return methodName(sig) == "kotlin_text_StringsKt__StringsJVMKt_replace_Ljava_lang_StringLjava_lang_StringLjava_lang_StringZLjava_lang_String"
            return false
        }
    }

    private var isAbstract = false
    private val stack = ArrayList<String>()

    val variables = LocalVariables()
    private val labelNames = HashMap<Label, Int>()
    private val nodes = ArrayList<TranslatorNode>()
    private var currentNode = TranslatorNode(createLabel())
    var printer = currentNode.printer

    private val isStatic = access.hasFlag(ACC_STATIC)

    val sig = MethodSig.c(clazz, name, descriptor)
    val canThrowError = canThrowError(sig)

    private fun createLabel(): Int {
        val label = Label()
        val index = labelNames.size
        labelNames[label] = index
        return index
    }

    private fun getLabel(label: Label): Int {
        return labelNames.getOrPut(label) { labelNames.size }
    }

    var linearTreeNodeIndex = 0
    var endNodeExtractorIndex = 0
    var bigLoopExtractorIndex = 0
    var endNodeIndex = 0
    var nextLoopIndex = 0

    var isLookingAtSpecial = false

    private val enableStackPush = enableTracing &&
            (canThrowError || crashOnAllExceptions) &&
            sig.name !in notStackPushedMethods

    init {

        isLookingAtSpecial = isLookingAtSpecial(sig)
        printOps = isLookingAtSpecial
        if (printOps) println("Method-Translating $clazz.$name.$descriptor")

        nodes.add(currentNode)
        currentNode.inputStack = emptyList()

        if (access.hasFlag(ACC_NATIVE) || access.hasFlag(ACC_ABSTRACT)) {
            // if it has @WASM annotation, use that code
            val wasm = hIndex.wasmNative[sig]
            if (wasm != null) {
                checkNotMapped()
                variables.defineParamVariables(clazz, descriptor, isStatic)
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
            checkNotMapped()
            variables.defineParamVariables(clazz, descriptor, isStatic)
            if (name == STATIC_INIT) {
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

    private fun checkNotMapped() {
        val mapped = hIndex.getAlias(sig)
        assertEquals(mapped, sig) { "Must not translate $sig, because it is mapped to $mapped" }
    }

    private fun createFuncHead(): FunctionImpl {
        val name2 = methodName(sig)
        val exported = exportAll || sig in hIndex.exportedMethods
        val results = descriptor.getResultWASMTypes(canThrowError)
        val wasmParams = descriptor.wasmParams
        val numParams = wasmParams.size + (!isStatic).toInt()
        val params = variables.localVarsAndParams
            .subList(0, numParams)
        assertTrue(params.all { it.isParam })
        return FunctionImpl(
            name2, params.map { Param(it.wasmType, it.name) },
            results, variables.localVars.map { LocalVariable(it.name, it.wasmType) }, emptyList(), exported
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
        val data = "frame ${
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
        val type = variables.findOrDefineLocalVar(varIndex, i32, "int")
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
        if (printOps) println("// $stack = $x")
        if (comments && commentStackOps) printer.comment("$stack = $x")
        assertFalse(stack.isEmpty()) { "Expected $x, but stack was empty; $clazz, $name, $descriptor" }
        assertEquals(x, stack.last())
        return this
    }

    private fun Builder.pop(x: String): Builder {
        // ensure we got the correct type
        if (printOps) println("// $stack -= $x")
        if (comments && commentStackOps) printer.comment("$stack -= $x")
        assertFalse(stack.isEmpty()) { "Expected $x, but stack was empty; $clazz, $name, $descriptor" }
        assertEquals(x, stack.pop())
        return this
    }

    private fun Builder.push(x: String): Builder {
        if (printOps) println("// $stack += $x")
        if (comments && commentStackOps) printer.comment("$stack += $x")
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
                if (checkArrayAccess && useResultForThrowables) handleThrowable()
            }
            0x2f -> {
                stackPush()
                printer.pop(ptrType).pop(i32).push(i64)
                    .append(if (checkArrayAccess) Call.i64ArrayLoad else Call.i64ArrayLoadU)
                stackPop()
                if (checkArrayAccess && useResultForThrowables) handleThrowable()
            }
            0x30 -> {
                stackPush()
                printer.pop(ptrType).pop(i32).push(f32)
                    .append(if (checkArrayAccess) Call.f32ArrayLoad else Call.f32ArrayLoadU)
                stackPop()
                if (checkArrayAccess && useResultForThrowables) handleThrowable()
            }
            0x31 -> {
                stackPush()
                printer.pop(ptrType).pop(i32).push(f64)
                    .append(if (checkArrayAccess) Call.f64ArrayLoad else Call.f64ArrayLoadU)
                stackPop()
                if (checkArrayAccess && useResultForThrowables) handleThrowable()
            }
            0x32 -> {
                stackPush()
                printer.pop(ptrType).pop(i32).push(ptrType)
                    .append(
                        if (checkArrayAccess) if (is32Bits) Call.i32ArrayLoad else Call.i64ArrayLoad
                        else if (is32Bits) Call.i32ArrayLoadU else Call.i64ArrayLoadU
                    )
                stackPop()
                if (checkArrayAccess && useResultForThrowables) handleThrowable()
            }
            0x33 -> {
                stackPush()
                printer.pop(ptrType).poppush(i32)
                    .append(if (checkArrayAccess) Call.s8ArrayLoad else Call.s8ArrayLoadU)
                stackPop()
                if (checkArrayAccess && useResultForThrowables) handleThrowable()
            }
            0x34 -> {
                stackPush()
                printer.pop(ptrType).poppush(i32)
                    .append(if (checkArrayAccess) Call.u16ArrayLoad else Call.u16ArrayLoadU)
                stackPop()
                if (checkArrayAccess && useResultForThrowables) handleThrowable()
            }
            0x35 -> {
                stackPush()
                printer.pop(ptrType).poppush(i32)
                    .append(if (checkArrayAccess) Call.s16ArrayLoad else Call.s16ArrayLoadU)
                stackPop()
                if (checkArrayAccess && useResultForThrowables) handleThrowable()
            }
            // store instructions
            0x4f -> {
                stackPush()
                printer.pop(i32).pop(i32).pop(ptrType)
                    .append(if (checkArrayAccess) Call.i32ArrayStore else Call.i32ArrayStoreU)
                stackPop()
                if (checkArrayAccess && useResultForThrowables) handleThrowable()
            }
            0x50 -> {
                stackPush()
                printer.pop(i64).pop(i32).pop(ptrType)
                    .append(if (checkArrayAccess) Call.i64ArrayStore else Call.i64ArrayStoreU)
                stackPop()
                if (checkArrayAccess && useResultForThrowables) handleThrowable()
            }
            0x51 -> {
                stackPush()
                printer.pop(f32).pop(i32).pop(ptrType)
                    .append(if (checkArrayAccess) Call.f32ArrayStore else Call.f32ArrayStoreU)
                stackPop()
                if (checkArrayAccess && useResultForThrowables) handleThrowable()
            }
            0x52 -> {
                stackPush()
                printer.pop(f64).pop(i32).pop(ptrType)
                    .append(if (checkArrayAccess) Call.f64ArrayStore else Call.f64ArrayStoreU)
                stackPop()
                if (checkArrayAccess && useResultForThrowables) handleThrowable()
            }
            0x53 -> {
                stackPush()
                printer.pop(ptrType).pop(i32).pop(ptrType)
                    .append(
                        if (checkArrayAccess) if (is32Bits) Call.i32ArrayStore else Call.i64ArrayStore
                        else if (is32Bits) Call.i32ArrayStoreU else Call.i64ArrayStoreU
                    )
                stackPop()
                if (checkArrayAccess && useResultForThrowables) handleThrowable()
            }
            0x54 -> {
                stackPush()
                printer.pop(i32).pop(i32).pop(ptrType)
                    .append(if (checkArrayAccess) Call.i8ArrayStore else Call.i8ArrayStoreU)
                stackPop()
                if (checkArrayAccess && useResultForThrowables) handleThrowable()
            }
            0x55, 0x56 -> { // char/short-array store
                stackPush()
                printer.pop(i32).pop(i32).pop(ptrType)
                    .append(if (checkArrayAccess) Call.i16ArrayStore else Call.i16ArrayStoreU)
                stackPop()
                if (checkArrayAccess && useResultForThrowables) handleThrowable()
            }

            // returnx is important: it shows to cancel the flow = jump to end
            in 0xac..0xb1 -> {

                if (currentNode.ifTrue != null) {
                    // throw IllegalStateException("Branch cannot have return afterwards")
                    // we expected a label, but didn't get any -> create out own
                    visitLabel(createLabel())
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
                // nextNode(createLabel())
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
                val lastInstr = printer.lastOrNull()
                if (isDuplicable(lastInstr)) {
                    printer.append(lastInstr!!)
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
                    if (useResultForThrowables) handleThrowable()
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
                    if (useResultForThrowables) handleThrowable()
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
                    if (useResultForThrowables) handleThrowable()
                }
                printer.pop(i32).poppush(i32).append(I32_REM_S)
            }
            0x71 -> {
                if (checkIntDivisions) {
                    stackPush()
                    printer.poppush(i64).append(Call("checkNonZero64"))
                    stackPop()
                    if (useResultForThrowables) handleThrowable()
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

            ARRAY_LENGTH_INSTR -> {
                // array length
                stackPush()
                printer.pop(ptrType).push(i32)
                    .append(if (checkArrayAccess) Call.arrayLength else Call.arrayLengthU)
                stackPop()
                if (checkArrayAccess && useResultForThrowables) handleThrowable()
            }
            ATHROW_INSTR -> {// athrow, easy :3
                printer.pop(ptrType).push(ptrType)
                printer.dupPtr() // todo why are we duplicating the error???
                handleThrowable(true)
                printer.pop(ptrType)
            }
            // MONITOR_ENTER -> printer.pop(ptrType).append("  call \$monitorEnter\n") // monitor enter
            // MONITOR_EXIT -> printer.pop(ptrType).append("  call \$monitorExit\n") // monitor exit
            MONITOR_ENTER -> printer.pop(ptrType).drop() // monitor enter
            MONITOR_EXIT -> printer.pop(ptrType).drop() // monitor exit
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
        if (printOps) println(
            "  [invoke dyn by $sig] $name, $descriptor, " +
                    "[${method.owner}, ${method.name}, tag: ${method.tag}, " +
                    "desc: ${method.desc}], [${args.joinToString()}]"
        )
        if (comments) printer.comment("invoke-dyn $name, $descriptor, $method, [${args.joinToString()}]")

        val dst = args[1] as Handle

        val synthClassName = getSynthClassName(sig, dst)
        val dlu = DelayedLambdaUpdate.needingBridgeUpdate[synthClassName]!!
        val fields = dlu.descriptor.params

        // register new class (not visitable)
        printer.append(i32Const(gIndex.getClassIndex(synthClassName)))
        if (fields.isEmpty() && !dlu.usesSelf) {
            // no instance is needed 😁:
            //  we create a four-byte pseudo instance from just the class index value
            printer.push(ptrType).append(Call.getClassIdPtr)
            if (comments) printer.comment(synthClassName)
        } else {
            stackPush()
            printer.push(ptrType).append(Call.createInstance)
            if (comments) printer.comment(synthClassName)
            stackPop()
            if (useResultForThrowables) handleThrowable()
        }

        if (fields.isNotEmpty()) {
            val createdInstance = variables.defineLocalVar(ptrType, synthClassName)
            printer.pop(ptrType)
            printer.append(createdInstance.setter)

            ///////////////////////////////
            // implement the constructor //
            ///////////////////////////////
            // is this the correct order? should be :)
            for (i in fields.lastIndex downTo 0) {
                val type = fields[i]
                printer.append(createdInstance.getter) // instance
                val offset = gIndex.getFieldOffset(synthClassName, "f$i", type, false)
                if (offset == null) {
                    printUsed(sig)
                    println("constructor-dependency? ${synthClassName in dIndex.constructorDependencies[sig]!!}")
                    throw NullPointerException("Missing $synthClassName.f$i ($type), constructable? ${synthClassName in dIndex.constructableClasses}")
                }
                if (comments) printer.comment("set field #$i")
                if (alwaysUseFieldCalls) {
                    printer
                        .append(i32Const(offset))
                        .append(getVIOStoreCall(type))
                } else {
                    printer
                        .append(if (is32Bits) i32Const(offset) else i64Const(offset.toLong()))
                        .append(if (is32Bits) I32Add else I64Add)
                        .append(Call("swap${jvm2wasmTyped(type)}$ptrType")) // swap ptr and value
                        .append(getStoreInstr2(type))
                }
                printer.pop(jvm2wasmTyped(type))
            }

            // get the instance, ready to continue :)
            printer.push(ptrType)
            printer.append(createdInstance.localGet)
        }

    }

    override fun visitJumpInsn(opcode: Int, label0: Label) {
        val label = getLabel(label0)
        visitJumpInsn(opcode, label)
    }

    fun visitJumpInsn(opcode: Int, label: Int) {
        if (printOps) println("  [jump] ${OpCode[opcode]} -> [L$label]")
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
            0xc6 -> printer.pop(ptrType) // is null
                .append(if (is32Bits) I32EQZ else I64EQZ)
            0xc7 -> printer.pop(ptrType) // is not null
                .append(if (is32Bits) I32EQZ else I64EQZ)
                .append(I32EQZ)
            0xa7 -> {} // just goto -> stack doesn't change
            0x99 -> printer.pop(i32).append(I32EQZ) // == 0
            0x9a -> printer.pop(i32).append(i32Const0).append(I32NE) // != 0
            0x9b -> printer.pop(i32).append(i32Const0).append(I32LTS) // < 0
            0x9c -> printer.pop(i32).append(i32Const0).append(I32GES) // >= 0
            0x9d -> printer.pop(i32).append(i32Const0).append(I32GTS) // > 0
            0x9e -> printer.pop(i32).append(i32Const0).append(I32LES) // <= 0
            else -> assertFail(OpCode[opcode])
        }
        if (comments) printer.comment("jump ${OpCode[opcode]} -> [L$label], stack: $stack")
        afterJump(label, opcode == 0xa7)
    }

    private fun afterJump(ifTrueLabel: Int, alwaysTrue: Boolean) {
        val oldNode = currentNode
        // if (printOps) println("setting ifTrue for $oldNode to ${labelNames[label]}")
        oldNode.ifTrue = ifTrueLabel
        oldNode.isAlwaysTrue = alwaysTrue // goto

        // just in case the next section is missing:
        visitLabel(createLabel(), alwaysTrue)
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
            is String -> {
                // Pack string into constant memory, and load its address.
                // Optimizing them against being dropped isn't worth it, because these are parameter names,
                // and in most cases (99%), there is a field with that same name.
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
                val type = Descriptor.parseType(value.descriptor)
                printer.append(i32Const(gIndex.getClassIndex(type)))
                printer.append(Call.findClass)
                if (comments) printer.comment("class $value")
            }
            else -> throw IllegalArgumentException("unknown '$value', ${value.javaClass}\n")
        }
    }

    var line = 0

    @Boring
    override fun visitLineNumber(line: Int, start: Label?) {
        if (printOps) println("Line $line: [${if (start != null) getLabel(start) else "?"}]")
        if (comments) printer.comment("line $line")
        this.line = line
    }

    override fun visitLocalVariable(
        name: String?, descriptor0: String, signature: String?,
        start0: Label, end0: Label, index: Int
    ) {
        val start = getLabel(start0)
        val end = getLabel(end0)
        val descriptor = Descriptor.parseType(descriptor0)
        val wasmType = jvm2wasmTyped(descriptor)
        val info = LocalVarInfo(name, descriptor, signature, start, end, index, wasmType)
        variables.localVarInfos.add(info)
    }

    override fun visitMaxs(maxStack: Int, maxLocals: Int) {
        // println(" max stacks: $maxStack, max locals: $maxLocals")
    }

    fun pop(splitArgs: List<String>, static: Boolean, ret: String?) {
        for (v in splitArgs.reversed()) {// arguments
            printer.pop(v)
        }
        if (!static) {
            // instance
            printer.pop(ptrType)
        }
        // return type
        if (ret != null) printer.push(jvm2wasmTyped(ret))
    }

    fun checkNotNull0(clazz: String, name: String, getCaller: (Builder) -> Unit) {
        if (checkNullPointers) {
            getCaller(printer)
            printer.append(i32Const(gIndex.getString(clazz)))
            printer.append(i32Const(gIndex.getString(name)))
            printer.append(Call("checkNotNull"))
            if (useResultForThrowables) handleThrowable()
        }
    }

    override fun visitMethodInsn(
        opcode0: Int, owner0: String, name: String,
        descriptor: String, isInterface: Boolean
    ) {
        val owner = Descriptor.parseTypeMixed(owner0)
        visitMethodInsn2(opcode0, owner, name, descriptor, isInterface, true)
    }


    fun visitMethodInsn2(
        opcode: Int, owner: String, name: String, descriptor: String,
        isInterface: Boolean, checkThrowable: Boolean
    ): Boolean {
        val sig = MethodSig.c(owner, name, descriptor)
        return visitMethodInsn2(opcode, sig, isInterface, checkThrowable)
    }

    fun visitMethodInsn2(opcode0: Int, sig0: MethodSig, isInterface: Boolean, checkThrowable: Boolean): Boolean {

        val owner = sig0.clazz
        val name = sig0.name
        val descriptor = sig0.descriptor
        val isStatic = opcode0 == INVOKE_STATIC || name == STATIC_INIT

        if (printOps) println("  [call] ${OpCode[opcode0]}, $owner, $name, $descriptor, $isInterface")

        val ret = descriptor.returnType
        val splitArgs = descriptor.wasmParams
        if (isStatic != hIndex.isStatic(sig0))
            throw RuntimeException("Called static/non-static incorrectly, $isStatic vs $sig0 (in $sig)")

        val sig1 = hIndex.getAlias(sig0)

        val methodsByOwner = hIndex.methodsByClass.getOrPut(owner) { HashSet() }
        if (sig1 == sig0 && methodsByOwner.add(sig0) && isStatic) {
            hIndex.staticMethods.add(sig0)
        }

        var calledCanThrow = canThrowError(sig1)

        fun getCaller(printer: Builder) {
            if (splitArgs.isNotEmpty()) {
                printer.append(Call(gIndex.getNth(listOf(ptrType) + splitArgs)))
            } else printer.dupPtr()
        }

        when (opcode0) {
            INVOKE_INTERFACE -> {

                assertTrue(sig0 in dIndex.usedInterfaceCalls)

                val variants = findConstructableChildImplementations(sig0)
                if (!resolveIndirect(sig0, splitArgs, ret, variants, ::getCaller, calledCanThrow, owner)) {
                    // load interface/function index
                    getCaller(printer)
                    printer.append(i32Const(gIndex.getInterfaceIndex(InterfaceSig.c(name, sig0.descriptor))))
                    // looks up class, goes to interface list, binary searches function, returns func-ptr
                    // instance, function index -> instance, function-ptr
                    stackPush()
                    printer.push(i32).append(Call("resolveInterface"))
                    stackPop() // so we can track the call better
                    if (useResultForThrowables) handleThrowable() // if it's not found or nullptr
                    printer.pop(i32) // pop instance
                    pop(splitArgs, false, ret)

                    stackPush()

                    printer.append(CallIndirect(gIndex.getType(false, sig0.descriptor, calledCanThrow)))
                    ActuallyUsedIndex.add(this.sig, sig1)
                    if (comments) printer.comment("invoke interface $owner, $name, $descriptor")

                    stackPop()
                }
            }
            INVOKE_VIRTUAL -> {
                if (owner[0] !in "[A" && owner !in dIndex.constructableClasses) {

                    stackPush()
                    getCaller(printer)

                    if (comments) printer.comment("not constructable class, $sig1, $owner, $name, $descriptor")
                    printer
                        .append(i32Const(gIndex.getString(methodName(sig1))))
                        // instance, function index -> function-ptr
                        .append(Call.resolveIndirectFail)
                        .append(Unreachable)

                    pop(splitArgs, false, ret)
                    stackPop()

                } else if (
                    hIndex.isFinal(sig0) &&
                    // todo a method that is final should always pass this, but somehow,
                    //  me/anno/utils/hpc/WorkSplitter/processUnbalanced(IIILme_anno_utils_hpc_WorkSplitterXTask1d;)V
                    //  doesn't pass the test
                    findConstructableChildImplementations(sig0).size < 2
                ) {

                    val methods = findConstructableChildImplementations(sig0)
                    if (methods.size > 1) {
                        println("sig0: $sig0")
                        println("sig1: $sig1")
                        println("Options:")
                        for (option in methods) {
                            println("  - $option")
                        }
                        assertFail("Unclear candidates")
                    }
                    val sig2 = methods.firstOrNull() ?: sig1

                    val setter = hIndex.setterMethods[sig2]
                    val getter = hIndex.getterMethods[sig2]

                    fun isStatic(field: FieldSig): Boolean {
                        return field.name in gIndex.getFieldOffsets(field.clazz, true).fields
                    }

                    val isEmpty = sig2 in hIndex.emptyFunctions

                    when {
                        setter != null -> {
                            visitFieldInsn2(
                                if (isStatic(setter)) SET_STATIC else SET_FIELD,
                                setter.clazz, setter.name, setter.descriptor, true
                            )
                            calledCanThrow = false
                        }
                        getter != null -> {
                            visitFieldInsn2(
                                if (isStatic(getter)) GET_STATIC else GET_FIELD,
                                getter.clazz, getter.name, getter.descriptor, true
                            )
                            calledCanThrow = false
                        }
                        isEmpty -> {
                            pop(splitArgs, false, ret)
                            if (comments) printer.comment("skipping empty2 $sig2")
                            for (j in splitArgs.indices) printer.drop() // drop arguments
                            printer.drop() // drop called
                            calledCanThrow = false
                        }
                        else -> {
                            if (!ignoreNonCriticalNullPointers) {
                                checkNotNull0(owner, name, ::getCaller)
                            }
                            pop(splitArgs, false, ret)
                            // final, so not actually virtual;
                            // can be directly called
                            val inline = hIndex.inlined[sig2]
                            if (inline != null) {
                                printer.append(inline)
                                printer.comment("virtual-inlined $sig2")
                            } else {
                                stackPush()
                                val name2 = methodName(sig2)
                                val name3 = methodName(sig0)
                                if (name3 == "java_lang_Object_hashCode_I" ||
                                    name3 == "java_util_function_Consumer_accept_Ljava_lang_ObjectV_accept_JV" ||
                                    name3 == "me_anno_gpu_OSWindow_addCallbacks_V"
                                ) throw IllegalStateException("$sig0 -> $sig2 must not be final!!!")
                                if (hIndex.isAbstract(sig2)) {
                                    throw IllegalStateException("Calling abstract method: $sig0 -> $sig1 -> $sig2")
                                }
                                ActuallyUsedIndex.add(this.sig, sig2)
                                printer.append(Call(name2))
                                stackPop()
                            }
                        }
                    }
                } else {
                    val options = findConstructableChildImplementations(sig0)
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
                            .push(i32)
                        if (comments) printer.comment("$sig0, #${options.size}")
                        stackPop()
                        if (useResultForThrowables) handleThrowable()
                        printer.pop(i32)
                        pop(splitArgs, false, ret)
                        printer.append(CallIndirect(gIndex.getType(false, sig0.descriptor, calledCanThrow)))
                        if (comments) printer.comment("invoke virtual $owner, $name, $descriptor")
                        ActuallyUsedIndex.add(this.sig, sig1)
                    }
                }
            }
            // typically, <init>, but also can be private or super function; -> no resolution required
            INVOKE_SPECIAL -> {
                if (!ignoreNonCriticalNullPointers) {
                    checkNotNull0(owner, name, ::getCaller)
                }
                pop(splitArgs, false, ret)
                val inline = hIndex.inlined[sig1]
                if (inline != null) {
                    printer.append(inline)
                    if (comments) printer.comment("special-inlined $sig1")
                } else {
                    if (sig1.descriptor.raw == "()V" && sig1 in hIndex.emptyFunctions) {
                        printer.drop()
                        if (comments) printer.comment("skipping empty $sig1")
                    } else {
                        stackPush()
                        val name2 = methodName(sig1)
                        assertFalse(hIndex.isAbstract(sig1))
                        ActuallyUsedIndex.add(this.sig, sig1)
                        printer.append(Call(name2))
                        stackPop()
                    }
                }
            }
            // static, no resolution required
            INVOKE_STATIC -> {
                pop(splitArgs, true, ret)
                val inline = hIndex.inlined[sig1]
                if (inline != null) {
                    printer.append(inline)
                    if (comments) printer.comment("static-inlined $sig1")
                } else {
                    stackPush()
                    val name2 = methodName(sig1)
                    assertFalse(hIndex.isAbstract(sig1))
                    ActuallyUsedIndex.add(this.sig, sig1)
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
        if (enableStackPush) {
            val callIndex = getCallIndex()
            val callConst = i32Const(callIndex)
            // if previous and before that is stackPop, and before that is call,
            //  and before that is stackPush, and before that is same constant, drop popping and skip pushing
            val list = printer.instrs
            if (list.size >= 4 &&
                list[list.size - 1] == Call.stackPop &&
                list[list.size - 2] is Call &&
                list[list.size - 3] == Call.stackPush &&
                list[list.size - 4] == callConst
            ) {
                // saves 11581/1.6M instructions
                // to do we could even skip over all constant stuff to optimize a little more
                //  -> no, if we want to optimize, avoid crashing at all, and disable stackPush
                list.removeLast()
                // reusing it...
            } else {
                printer
                    .append(callConst)
                    .append(Call.stackPush)
            }
        }
    }

    fun stackPop() {
        if (enableStackPush) {
            printer.append(Call.stackPop)
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
        if (useResultForThrowables) handleThrowable()

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

    override fun visitLookupSwitchInsn(default0: Label, keys: IntArray, labels0: Array<out Label>) {
        // implement this in wasm text
        // and implement this possible to be decoded as a tree:
        // we replace it for now with standard instructions
        val default = getLabel(default0)
        val labels = labels0.map { getLabel(it) }
        if (printOps) println("  [lookup] switch [$default], [${keys.joinToString()}], [${labels.joinToString()}]")
        val helper = variables.defineLocalVar(i32, "int")
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

    data class Catcher(val start: Int, val end: Int, val handler: Int, val type: String)

    override fun visitTryCatchBlock(start0: Label, end0: Label, handler0: Label, type0: String?) {
        val start = getLabel(start0)
        val end = getLabel(end0)
        val handler = getLabel(handler0)
        val type = if (type0 != null) replaceClass(type0) else "java/lang/Throwable"
        catchers.add(Catcher(start, end, handler, type))
        if (comments) printer.comment("try-catch $start .. $end, handler $handler, type $type")
        if (printOps) println("  ;; try-catch $start .. $end, handler $handler, type $type")
    }

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
            printer.append(Call.panic)
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

            if (catchers.size > 1 || (catchers[0].type != "java/lang/Throwable")) {

                val throwable = variables.defineLocalVar(ptrType, "java/lang/Throwable")
                printer.append(throwable.localSet).comment("multiple/complex catchers")

                var handler = TranslatorNode(createLabel())
                nodes.add(handler)

                if (mustThrow) {
                    visitJumpInsn(0xa7, handler.label) // jump to handler
                } else {
                    printer.push(ptrType).append(throwable.localGet)
                    visitJumpInsn(0xc7, handler.label) // if not null, jump to handler
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

                    if (catcher.type == "java/lang/Throwable") {
                        handler.ifFalse = null
                        handler.isAlwaysTrue = true
                        handler.ifTrue = catcher.handler
                        return
                    } else {

                        // if condition
                        // throwable -> throwable, throwable, int - instanceOf > throwable, jump-condition
                        handler.printer.dupPtr()
                        handler.printer.appendInstanceOf(catcher.type)
                        if (comments) handler.printer.comment("handler #$i/${catchers.size}/$throwable")

                        // actual branch
                        val nextHandler = TranslatorNode(createLabel())
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

                val catcher = catchers.first()
                if (mustThrow) {

                    if (comments) printer.comment("throwing single generic catcher")

                    if (printOps) println("--- handler: ${currentNode.label}")

                    val currentNode = currentNode
                    if (stack.isNotEmpty()) {
                        for (j in stack.indices) { // don't drop exception
                            printer.drop()
                        }
                    }

                    visitLabel(createLabel(), true)

                    currentNode.isAlwaysTrue = true
                    currentNode.ifTrue = catcher.handler
                    currentNode.outputStack = listOf(ptrType)

                } else {

                    printer.comment("maybe throwing single generic catcher")

                    val mainHandler = TranslatorNode(createLabel())
                    val throwable = variables.defineLocalVar(ptrType, catcher.type)

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
                    mainHandler.ifTrue = catcher.handler
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
        val retType = descriptor.wasmReturnType
        if (mustThrow) {
            if (retType != null) {
                printer.append(Const.zero[retType]!!)
                    .append(Call("swapi32$retType"))
            }
            printer.append(Return)
        } else {
            val tmp = variables.tmpPtr
            printer.append(tmp.localSet)
            printer.append(tmp.localGet)
            val ifTrue = if (retType == null) {
                listOf(tmp.localGet, Return)
            } else {
                val zeroResult = Const.zero[retType]!!
                listOf(zeroResult, tmp.localGet, Return)
            }
            printer.append(IfBranch(ifTrue, emptyList(), emptyList(), emptyList()))
        }
    }

    override fun visitTypeAnnotation(
        typeRef: Int,
        typePath: TypePath?,
        descriptor: String?,
        visible: Boolean
    ): AnnotationVisitor? {
        // only occurrences: 16777216, null, Lkotlin/internal/OnlyInputTypes;, false
        if (printOps) println("  type annotation $typeRef, $typePath, $descriptor, $visible")
        return null
    }

    override fun visitTypeInsn(opcode: Int, type0: String) {
        val type = Descriptor.parseTypeMixed(type0)
        if (printOps) println("  [${OpCode[opcode]}] $type")
        when (opcode) {
            NEW_INSTR -> {
                // new instance
                stackPush()
                printer.push(ptrType)
                    .append(i32Const(gIndex.getClassIndex(type)))
                    .append(Call.createInstance)
                if (comments) printer.comment(type)
                stackPop()
                if (useResultForThrowables) handleThrowable()
            }
            0xbd -> {
                // a-new array, type doesn't matter
                stackPush()
                printer.pop(i32).push(ptrType)
                printer.append(Call.createObjectArray)
                if (comments) printer.comment(type)
                stackPop()
                if (useResultForThrowables) handleThrowable()
            }
            0xc0 -> {
                // check cast
                if (checkClassCasts) {
                    stackPush()
                    printer.pop(ptrType).push(ptrType)
                    printer.printCastClass(type)
                    if (comments) printer.comment(type)
                    stackPop()
                    if (useResultForThrowables) handleThrowable()
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
        append(if (hasConstructableChildClasses(clazz)) Call.checkCast else Call.checkCastExact)
    }

    private fun Builder.appendInstanceOf(clazz: String) {
        if (clazz in dIndex.constructableClasses) {
            append(i32Const(gIndex.getClassIndex(clazz)))
            append(
                if (hasConstructableChildClasses(clazz)) {
                    if (hIndex.isInterfaceClass(clazz)) Call.instanceOf
                    else Call.instanceOfNonInterface
                } else Call.instanceOfExact
            )
        } else {
            append(Drop).append(i32Const0)
        }
    }

    private fun hasConstructableChildClasses(clazz: String): Boolean {
        val children = hIndex.childClasses[clazz] ?: return false
        return children.any { childClass -> childClass in dIndex.constructableClasses }
    }

    override fun visitVarInsn(opcode: Int, varIndex: Int) {
        visitVarInsn2(opcode, varIndex, variables.parameterByIndex.getOrNull(varIndex))
    }

    private fun getVarIsPush(opcode: Int): Boolean {
        return when (opcode) {
            in 0x15..0x2d -> true
            in 0x36..0x4e -> false
            else -> assertFail()
        }
    }

    private fun getVarWASMType(opcode: Int): String {
        return when (opcode) {
            0x15, in 0x1a..0x1d -> i32 // iload
            0x16, in 0x1e..0x21 -> i64 // lload
            0x17, in 0x22..0x25 -> f32 // fload
            0x18, in 0x26..0x29 -> f64 // dload
            0x19, in 0x2a..0x2d -> ptrType // aload
            0x36, in 0x3b..0x3e -> i32 // istore
            0x37, in 0x3f..0x42 -> i64 // lstore
            0x38, in 0x43..0x46 -> f32 // fstore
            0x39, in 0x47..0x4a -> f64 // dstore
            0x3a, in 0x4b..0x4e -> ptrType // astore
            else -> assertFail()
        }
    }

    private fun visitVarInsn2(opcode: Int, varIndex: Int, paramVariable: LocalVariableOrParam?) {
        if (printOps) println("  [var] local ${OpCode[opcode]}, $varIndex, $paramVariable")
        val isPush = getVarIsPush(opcode)
        val type = getVarWASMType(opcode)
        if (isPush) printer.push(type)
        else printer.pop(type)
        val variable = paramVariable
            ?: variables.findOrDefineLocalVar(varIndex, type, "?")
        assertEquals(type, variable.wasmType)
        printer.append(if (isPush) variable.localGet else variable.localSet)
    }

    fun visitVarInsn2(opcode: Int, paramVariable: LocalVariableOrParam) {
        // varIndex=-1 is unused
        visitVarInsn2(opcode, -1, paramVariable)
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
                    .append(Call.createNativeArray[1])
                if (comments) printer.comment(type)
                stackPop()
                if (useResultForThrowables) handleThrowable()
            }
            else -> assertFail()
        }
    }

    override fun visitLabel(label: Label) {
        visitLabel(getLabel(label))
    }

    private fun visitLabel(label: Int) {
        visitLabel(label, false)
    }

    private fun visitLabel(label: Int, findStackManually: Boolean) {

        val currNode = currentNode
        val nextNode = TranslatorNode(label)

        currNode.outputStack = ArrayList(stack)

        var found = false
        if (findStackManually/* || (currNode.hasNoCode())*/) {
            // find whether we had a good candidate in the past
            for (node in nodes) { // O(n²) -> potentially very slow :/
                if (node.ifTrue == label || node.ifFalse?.label == label) {
                    nextNode.inputStack = node.outputStack
                    // println("found ${labelNames[label]} :), $stack -> ${node.outputStack}")
                    stack.clear()
                    stack.addAll(node.outputStack)
                    found = true
                    break
                }
            }
            // if (!found) println("didn't find ${labelNames[label]} :/")
        }

        if (!found) {
            nextNode.inputStack = ArrayList(stack)
        }

        nodes.add(nextNode)
        currNode.ifFalse = nextNode

        currentNode = nextNode
        printer = nextNode.printer

        if (printOps) println(" [L$label]")
        if (comments) printer.comment("[L$label]")
    }

    private fun getLoadInstr(descriptor: String): Instruction = when (descriptor) {
        "boolean", "byte" -> I32Load8S
        "short" -> I32Load16S
        "char" -> I32Load16U
        "int" -> I32Load
        "long" -> I64Load
        "float" -> F32Load
        "double" -> F64Load
        else -> if (is32Bits) I32Load else I64Load
    }

    private fun getStoreInstr(descriptor: String) = getStoreInstr2(descriptor)

    private fun getStoreInstr2(descriptor: String): Instruction = when (descriptor) {
        "boolean", "byte" -> I32Store8
        "short", "char" -> I32Store16
        "int" -> I32Store
        "long" -> I64Store
        "float" -> F32Store
        "double" -> F64Store
        "Z", "B", "I", "J", "F", "D" -> throw IllegalArgumentException()
        else -> if (is32Bits) I32Store else I64Store
    }

    private fun getStaticLoadCall(descriptor: String): Instruction = when (descriptor) {
        "boolean", "byte" -> Call.getStaticFieldS8
        "short" -> Call.getStaticFieldS16
        "char" -> Call.getStaticFieldU16
        "int" -> Call.getStaticFieldI32
        "long" -> Call.getStaticFieldI64
        "float" -> Call.getStaticFieldF32
        "double" -> Call.getStaticFieldF64
        "Z", "B", "I", "J", "F", "D" -> throw IllegalArgumentException()
        else -> if (is32Bits) Call.getStaticFieldI32 else Call.getStaticFieldI64
    }

    private fun getLoadCall(descriptor: String): Instruction = when (descriptor) {
        "boolean", "byte" -> Call.getFieldS8
        "short" -> Call.getFieldS16
        "char" -> Call.getFieldU16
        "int" -> Call.getFieldI32
        "long" -> Call.getFieldI64
        "float" -> Call.getFieldF32
        "double" -> Call.getFieldF64
        "Z", "B", "I", "J", "F", "D" -> throw IllegalArgumentException()
        else -> if (is32Bits) Call.getFieldI32 else Call.getFieldI64
    }

    private fun getStaticStoreCall(descriptor: String): Instruction = when (descriptor) {
        "boolean", "byte" -> Call.setStaticFieldI8
        "short", "char" -> Call.setStaticFieldI16
        "int" -> Call.setStaticFieldI32
        "long" -> Call.setStaticFieldI64
        "float" -> Call.setStaticFieldF32
        "double" -> Call.setStaticFieldF64
        "Z", "B", "I", "J", "F", "D" -> throw IllegalArgumentException()
        else -> if (is32Bits) Call.setStaticFieldI32 else Call.setStaticFieldI64
    }

    private fun getStoreCall(descriptor: String): Instruction = when (descriptor) {
        "boolean", "byte" -> Call.setFieldI8
        "short", "char" -> Call.setFieldI16
        "int" -> Call.setFieldI32
        "long" -> Call.setFieldI64
        "float" -> Call.setFieldF32
        "double" -> Call.setFieldF64
        "Z", "B", "I", "J", "F", "D" -> throw IllegalArgumentException()
        else -> if (is32Bits) Call.setFieldI32 else Call.setFieldI64
    }

    private fun getVIOStoreCall(descriptor: String): Instruction = when (descriptor) {
        "boolean", "byte" -> Call.setVIOFieldI8
        "short", "char" -> Call.setVIOFieldI16
        "int" -> Call.setVIOFieldI32
        "long" -> Call.setVIOFieldI64
        "float" -> Call.setVIOFieldF32
        "double" -> Call.setVIOFieldF64
        "Z", "B", "I", "J", "F", "D" -> throw IllegalArgumentException()
        else -> if (is32Bits) Call.setVIOFieldI32 else Call.setVIOFieldI64
    }

    private fun callStaticInit(clazz: String) {
        if (name == STATIC_INIT && clazz == this.clazz) {
            if (comments) printer.comment("skipped <clinit>, we're inside of it")
            return
        } // it's currently being inited :)
        val sig = MethodSig.staticInit(clazz)
        if (hIndex.methodsByClass[clazz]?.contains(sig) != true) {
            if (comments) printer.comment("skipped <clinit>, because empty")
            return
        }
        stackPush()
        printer.append(Call(methodName(sig)))
        ActuallyUsedIndex.add(this.sig, sig)
        stackPop()
        if (useResultForThrowables && canThrowError(sig)) {
            handleThrowable()
        }
    }

    private val precalculateStaticFields = true
    override fun visitFieldInsn(opcode: Int, owner0: String, name: String, descriptor: String) {
        val type = Descriptor.parseType(descriptor)
        visitFieldInsn2(opcode, replaceClass(owner0), name, type, true)
    }

    private fun Builder.dupPtr(): Builder {
        return dupIXX(variables.tmpPtr)
    }

    fun visitFieldInsn2(opcode: Int, owner: String, name: String, type: String, checkNull: Boolean) {
        // get offset to field, and store value there
        // if static, find field first... static[]
        // getstatic, putstatic, getfield, putfield
        if (printOps) println("  [field] ${OpCode[opcode]}, $owner, $name, $type")
        val wasmType = jvm2wasmTyped(type)
        val static = opcode == GET_STATIC || opcode == SET_STATIC
        val fieldOffset = gIndex.getFieldOffset(owner, name, type, static)
        val sig = FieldSig(owner, name, type, static)
        val value = hIndex.finalFields[sig]
        val setter = opcode == SET_FIELD || opcode == SET_STATIC
        if (value != null) {
            if (!checkNull) throw IllegalStateException("Field $owner,$name,$type is final")
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
            GET_STATIC -> {
                if (name in enumFieldsNames) {
                    callStaticInit(owner)
                    val clazzIndex = gIndex.getClassIndex(owner)
                    val fieldOffset1 = gIndex.getFieldOffset(
                        "java/lang/Class", "enumConstants",
                        "java/lang/Object", false
                    )!!
                    printer
                        .append(i32Const(clazzIndex))
                        .append(Call.findClass)
                        .append(i32Const(fieldOffset1))
                    printer.append(I32Add).append(I32Load)
                    if (comments) printer.comment("enum values")
                    printer.push(wasmType)
                } else if (fieldOffset != null) {
                    callStaticInit(owner)
                    printer.push(wasmType)
                    // load class index
                    if (precalculateStaticFields) {
                        val staticPtr = lookupStaticVariable(owner, fieldOffset)
                        printer.append(i32Const(staticPtr))
                    } else {
                        printer
                            .append(i32Const(gIndex.getClassIndex(owner)))
                            .append(i32Const(fieldOffset))
                            // class index, field offset -> static value
                            .append(Call.findStatic)
                    }
                    if (alwaysUseFieldCalls) {
                        printer.append(getStaticLoadCall(type))
                    } else {
                        printer.append(getLoadInstr(type))
                    }
                    if (comments) printer.comment("get static '$owner.$name'")
                } else {
                    printer.push(wasmType).append(Const.zero[wasmType]!!)
                }
            }
            SET_STATIC -> {
                if (name in enumFieldsNames) {
                    val clazzIndex = gIndex.getClassIndex(owner)
                    val fieldOffset1 = gIndex.getFieldOffset(
                        "java/lang/Class", "enumConstants",
                        "java/lang/Object", false
                    )!!
                    printer
                        .append(i32Const(clazzIndex))
                        .append(Call.findClass)
                        .append(i32Const(fieldOffset1))
                    printer.append(I32Add).append(Call("swapi32i32")).append(I32Store)
                    if (comments) printer.comment("enum values")
                    printer.pop(wasmType)
                } else if (fieldOffset != null) {
                    callStaticInit(owner)
                    printer.pop(wasmType)
                    if (precalculateStaticFields) {
                        val staticPtr = lookupStaticVariable(owner, fieldOffset)
                        printer.append(i32Const(staticPtr))
                    } else {
                        printer
                            .append(i32Const(gIndex.getClassIndex(owner)))
                            .append(i32Const(fieldOffset))
                            // ;; class index, field offset -> static value
                            .append(Call.findStatic)
                    }
                    printer.append(getStaticStoreCall(type))
                    if (comments) printer.comment("put static '$owner.$name'")
                } else {
                    printer.pop(wasmType)
                    printer.drop()
                }
            }
            GET_FIELD -> {
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
                        printer.dupPtr()
                    }
                }
                printer.pop(ptrType).push(wasmType)
                if (fieldOffset != null) {
                    printer.append(i32Const(fieldOffset))
                    if (alwaysUseFieldCalls) {
                        printer.append(getLoadCall(type))
                    } else {
                        printer
                            .append(I32Add)
                            .append(getLoadInstr(type))
                    }
                } else {
                    printer
                        .drop()
                        .append(Const.zero[wasmType]!!)
                }
            }
            SET_FIELD -> {
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
                            .append(getStoreInstr(type))
                    } else {
                        // we'd need to call a function twice, so call a generic functions for this
                        printer.append(i32Const(fieldOffset))
                            .append(getStoreCall(type))
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
            try {
                // must happen before GraphingNodes, because we need to be able to associate labels with nodes
                variables.renameLocalVariables(sig, nodes, labelNames.size)
                variables.initializeLocalVariables(nodes.first().printer)

                for (i in nodes.indices) {
                    val node = nodes[i]
                    node.isReturn = node.printer.lastOrNull()?.isReturning() ?: false
                }

                val nodes = TranslatorNode.convertNodes(nodes)
                validateInputOutputStacks(nodes, sig)
                validateStack(nodes, this)
                val jointBuilder = StructuralAnalysis(this, nodes).joinNodes()
                optimizeUsingReplacements(jointBuilder)

                val funcHead = createFuncHead()
                gIndex.translatedMethods[sig] = FunctionImpl(
                    funcHead.funcName, funcHead.params, funcHead.results,
                    funcHead.locals, jointBuilder.instrs,
                    funcHead.isExported
                )
                if (isLookingAtSpecial) {
                    throw IllegalStateException("Looking at special '$sig'")
                }
            } catch (e: Throwable) {
                LOGGER.warn("Error in $sig.visitEnd")
                throw e
            }
        }
    }

    private var lastLine = -1
    private fun getCallIndex(): Int {
        if (line != lastLine) {
            lastLine = line
            stackTraceTable.writeLE32(gIndex.getString(sig.clazz))
            stackTraceTable.writeLE32(gIndex.getString(sig.name))
            stackTraceTable.writeLE32(line)
        }
        return stackTraceTable.size() / 12 - 1
    }

}