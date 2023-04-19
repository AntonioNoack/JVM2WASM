package translator

import annotations.Boring
import annotations.NotCalled
import api
import canThrowError
import dIndex
import enableTracing
import exportAll
import gIndex
import graphing.Node
import graphing.StructuralAnalysis
import graphing.StructuralAnalysis.transform
import hIndex
import hierarchy.DelayedLambdaUpdate
import hierarchy.DelayedLambdaUpdate.Companion.synthClassName
import ignoreNonCriticalNullPointers
import jvm.JVM32.objectOverhead
import me.anno.io.Streams.writeLE32
import me.anno.maths.Maths.hasFlag
import me.anno.utils.LOGGER
import me.anno.utils.strings.StringHelper.shorten
import me.anno.utils.structures.lists.Lists.pop
import me.anno.utils.types.Booleans.toInt
import org.objectweb.asm.*
import org.objectweb.asm.Opcodes.*
import reb
import translator.GeneratorIndex.pair
import translator.GeneratorIndex.tri
import useWASMExceptions
import utils.*
import java.lang.reflect.Modifier
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

    var printOps = false
    var comments = false

    private var isAbstract = false
    private val stack = ArrayList<String>()
    private val argsMapping = HashMap<Int, Int>()

    private val headPrinter = Builder(32) // header
    private var resultType = 'V'

    private val startLabel = Label()
    private val nodes = ArrayList<Node>()
    private var currentNode = Node(startLabel)
    var printer = currentNode.printer

    init {
        nodes.add(currentNode)
        currentNode.inputStack = emptyList()
    }

    private val sig = MethodSig.c(clazz, name, descriptor)
    private val canThrowError = canThrowError(sig)

    private val localVariables = ArrayList<LocalVar>()
    private val activeLocalVars = ArrayList<LocalVar>()

    data class LocalVar(
        val descriptor: String,
        val wasmType: String,
        val wasmName: String,
        val start: Label,
        val end: Label,
        val index: Int
    )

    val isStatic = access.hasFlag(ACC_STATIC)

    init {
        if (access.hasFlag(ACC_NATIVE) || access.hasFlag(ACC_ABSTRACT) /*|| access.hasFlag(ACC_INTERFACE)*/) {
            // if has @WASM annotation, use that code
            val wasm = hIndex.wasmNative[sig]
            if (wasm != null) {
                printHeader(access)
                printer.append(wasm)
                currentNode.isReturn = true
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
            printHeader(access)
            if (name == "<clinit>") {
                // check whether this class was already inited
                val clazz1 = gIndex.getClassIndex(clazz)
                printer.append("  global.get \$Z\n")
                    .append("  i32.const $clazz1 i32.add\n")
                    .append("  i32.load8_u\n")
                printer.append(
                    if (canThrowError) "  (if (then i32.const 0 return))\n"
                    else "  (if (then return))\n"
                )
                printer.append("  global.get \$Z\n")
                    .append("  i32.const $clazz1 i32.add\n")
                    .append("  i32.const 1 i32.store8\n") // mark as being loaded
            }
        }
    }

    private fun printHeader(access: Int) {
        val static = access.hasFlag(ACC_STATIC)
        // convert descriptor into list of classes for arguments
        val args = split1(descriptor.substring(1, descriptor.lastIndexOf(')')))

        // special rules in Java:
        // double and long use two slots in localVariables
        val nothingLabel = Label()
        var numArgs = 0
        var idx = 0
        if (!static) {
            activeLocalVars.add(LocalVar(clazz, ptrType, "0", nothingLabel, nothingLabel, 0))
            argsMapping[numArgs++] = idx++
        }
        for (arg in args) {
            activeLocalVars.add(LocalVar(arg, jvm2wasm(arg), "$idx", nothingLabel, nothingLabel, numArgs))
            argsMapping[numArgs] = idx++
            numArgs += when (arg) {
                "D", "J" -> 2
                else -> 1
            }
        }

        resultType = descriptor[descriptor.indexOf(')') + 1]
        val name2 = methodName(sig)
        headPrinter.append("(func $").append(name2)

        val mapped = hIndex.methodAliases[name2]
        if (mapped != null && mapped != sig) {
            throw IllegalStateException("Must not translate $sig, because it is mapped to $mapped")
        }
        if (exportAll || sig in hIndex.exportedMethods) {
            headPrinter.append(" (export \"").append(name2).append("\")")
        }
        if (!static || args.isNotEmpty()) {
            headPrinter.append(" (param")
            if (!static) headPrinter.append(" ").append(ptrType)
            for (arg in args) {
                headPrinter.append(' ').append(jvm2wasm(arg))
            }
            headPrinter.append(")")
        }
        if (canThrowError) {
            headPrinter.append(" (result ")
            if (resultType != 'V') {
                headPrinter.append(jvm2wasm1(resultType)).append(' ')
            }
            headPrinter.append("i32)")
        } else if (resultType != 'V') {
            headPrinter.append(" (result ").append(jvm2wasm1(resultType)).append(')')
        }
        headPrinter.append('\n')
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
    override fun visitAnnotationDefault(): AnnotationVisitor {
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
            // if ("i32" in currentNode.printer) throw IllegalStateException()

            currentNode.inputStack = ArrayList(this.stack)

        } else throw NotImplementedError()
        if (printOps) printer.append(data).append('\n')
    }

    override fun visitIincInsn(varIndex: Int, increment: Int) {
        // increment the variable at that index
        if (printOps) println("  [$varIndex] += $increment")
        val type = findLocalVar(varIndex, i32)
        printer.append("  local.get ").append(type.wasmName)
        printer.append(" i32.const ").append(increment)
        printer.append(" i32.add local.set ").append(type.wasmName).append('\n')
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
            printer.append("  ;; $stack = $x\n")
        }
        if (stack.isEmpty()) throw IllegalStateException("Expected $x, but stack was empty; $clazz, $name, $descriptor")
        val v = stack.last()
        if (v != x) throw IllegalStateException("Expected $x, but got $v")
        return this
    }

    private fun Builder.pop(x: String): Builder {
        // ensure we got the correct type
        if (printOps) {
            println("// $stack -= $x")
            printer.append("  ;; $stack -= $x\n")
        }
        if (stack.isEmpty()) throw IllegalStateException("Expected $x, but stack was empty; $clazz, $name, $descriptor")
        val v = stack.pop()
        if (v != x) throw IllegalStateException("Expected $x, but got $v")
        return this
    }

    private fun Builder.push(x: String): Builder {
        if (printOps) {
            println("// $stack += $x")
            printer.append("  ;; $stack += $x\n")
        }
        stack.add(x)
        return this
    }

    override fun visitInsn(opcode: Int) {
        // https://github.com/AssemblyScript/assemblyscript/wiki/WebAssembly-to-TypeScript-Cheat-Sheet
        if (printOps) println("  [${OpCode[opcode]}]")
        when (opcode) {
            0x00 -> {} // nop
            0x01 -> printer.push(ptrType).append(if (is32Bits) "  i32.const 0\n" else "  i64.const 0\n") // load null
            0x02 -> printer.push(i32).append("  i32.const -1\n")
            0x03 -> printer.push(i32).append("  i32.const 0\n")
            0x04 -> printer.push(i32).append("  i32.const 1\n")
            0x05 -> printer.push(i32).append("  i32.const 2\n")
            0x06 -> printer.push(i32).append("  i32.const 3\n")
            0x07 -> printer.push(i32).append("  i32.const 4\n")
            0x08 -> printer.push(i32).append("  i32.const 5\n")
            0x09 -> printer.push(i64).append("  i64.const 0\n")
            0x0a -> printer.push(i64).append("  i64.const 1\n")
            0x0b -> printer.push(f32).append("  f32.const 0\n")
            0x0c -> printer.push(f32).append("  f32.const 1\n")
            0x0d -> printer.push(f32).append("  f32.const 2\n")
            0x0e -> printer.push(f64).append("  f64.const 0\n")
            0x0f -> printer.push(f64).append("  f64.const 1\n")

            // in 0x15 .. 0x19 -> printer.append("  local.get [idx]")

            0x2e -> {
                stackPush()
                printer.pop(ptrType).poppush(i32).append("  call \$i32ArrayLoad\n")
                stackPop()
                handleThrowable()
            }
            0x2f -> {
                stackPush()
                printer.pop(ptrType).pop(i32).push(i64).append("  call \$i64ArrayLoad\n")
                stackPop()
                handleThrowable()
            }
            0x30 -> {
                stackPush()
                printer.pop(ptrType).pop(i32).push(f32).append("  call \$f32ArrayLoad\n")
                stackPop()
                handleThrowable()
            }
            0x31 -> {
                stackPush()
                printer.pop(ptrType).pop(i32).push(f64).append("  call \$f64ArrayLoad\n")
                stackPop()
                handleThrowable()
            }
            0x32 -> {
                stackPush()
                printer.pop(ptrType).pop(i32).push(ptrType).append(
                    if (is32Bits) "  call \$i32ArrayLoad\n"
                    else "  call \$i64ArrayLoad\n"
                )
                stackPop()
                handleThrowable()
            }
            0x33 -> {
                stackPush()
                printer.pop(ptrType).poppush(i32).append("  call \$i8ArrayLoad\n")
                stackPop()
                handleThrowable()
            }
            0x34 -> {
                stackPush()
                printer.pop(ptrType).poppush(i32).append("  call \$u16ArrayLoad\n")
                stackPop()
                handleThrowable()
            }
            0x35 -> {
                stackPush()
                printer.pop(ptrType).poppush(i32).append("  call \$s16ArrayLoad\n")
                stackPop()
                handleThrowable()
            }

            0x4f -> {
                stackPush()
                printer.pop(i32).pop(i32).pop(ptrType).append("  call \$i32ArrayStore\n")
                stackPop()
                handleThrowable()
            }
            0x50 -> {
                stackPush()
                printer.pop(i64).pop(i32).pop(ptrType).append("  call \$i64ArrayStore\n")
                stackPop()
                handleThrowable()
            }
            0x51 -> {
                stackPush()
                printer.pop(f32).pop(i32).pop(ptrType).append("  call \$f32ArrayStore\n")
                stackPop()
                handleThrowable()
            }
            0x52 -> {
                stackPush()
                printer.pop(f64).pop(i32).pop(ptrType).append("  call \$f64ArrayStore\n")
                stackPop()
                handleThrowable()
            }
            0x53 -> {
                stackPush()
                printer.pop(ptrType).pop(i32).pop(ptrType).append(
                    if (is32Bits) "  call \$i32ArrayStore\n"
                    else "  call \$i64ArrayStore\n"
                )
                stackPop()
                handleThrowable()
            }
            0x54 -> {
                stackPush()
                printer.pop(i32).pop(i32).pop(ptrType).append("  call \$i8ArrayStore\n")
                stackPop()
                handleThrowable()
            }
            0x55 -> {
                stackPush()
                printer.pop(i32).pop(i32).pop(ptrType).append("  call \$i16ArrayStore\n")
                stackPop()
                handleThrowable()
            }
            0x56 -> {
                stackPush()
                printer.pop(i32).pop(i32).pop(ptrType).append("  call \$i16ArrayStore\n")
                stackPop()
                handleThrowable()
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

                currentNode.isReturn = true
                if (opcode != 0xb1) {
                    val type = stack.last()
                    printer.pop(type)
                    currentNode.outputStack = listOf(type, ptrType)
                } else {
                    currentNode.outputStack = listOf(ptrType)
                }

                if (canThrowError(sig)) {
                    printer.append("  ").append(ptrType).append(".const 0\n") // no Throwable, normal exit
                }

                printer.append("  return\n")

                // marking this as the end
                // if (printOps) println("marking $currentNode as return")
                // if it is missing anywhere, we could call this:
                // nextNode(Label())
            }
            0x101 -> {
                if (canThrowError) {
                    printer.append("  return\n")
                    currentNode.isReturn = true
                } else throw IllegalStateException()
            }

            0x57 -> {
                val type1 = stack.last()
                printer.pop(type1).append("  drop\n")
            }
            0x58 -> {
                val type = stack.last()
                printer.pop(type).append("  drop\n")
                if (!is32Bits) throw NotImplementedError("We'd need to differentiate ptrType from i64 for this to work correctly")
                if (type == i32 || type == f32)
                    printer.pop(stack.last()).append("  drop\n")
            }
            0x59 -> {// dup
                val type1 = stack.last()
                printer.push(type1)
                if (type1 == i32) {
                    dupI32()
                    printer.append('\n')
                } else {
                    printer.append("  call \$dup")
                        .append(type1).append('\n')
                }
            }
            0x5a -> {
                val v0 = stack.pop()!!
                val v1 = stack.pop()!!
                stack.add(v0)
                stack.add(v1)
                stack.add(v0)
                printer.append("  call \$dup_x1")
                    .append(v0).append(v1).append('\n')
                gIndex.usedDup_x1[pair(v0, v1)] = true
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
                printer.append("  call \$dup_x2")
                    .append(v0).append(v1).append(v2).append('\n')
                gIndex.usedDup_x2[tri(v0, v1, v2)] = true
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
                    printer.append("  call \$dup2").append(v0).append(v1).append('\n')
                } else {
                    // dup
                    stack.add(v1)
                    printer.append("  call \$dup").append(v1).append('\n')
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
                    // todo implement this
                    printer.append("  call \$dup2_x1")
                        .append(v1).append(v2).append(v3).append('\n')
                    gIndex.usedDup2_x1[tri(v1, v2, v3)] = true
                } else {
                    // value2, value1 ->
                    // value1, value2, value1
                    // seems to be identical to 0x5a
                    visitInsn(0x5a)
                }
            }
            0x5e -> {
                printer.append("  call \$dup2_x2\n")
                // if (x != null) log("mouseX", x[0] = getMouseX());
                // if (y != null) log("mouseY", y[0] = getMouseY());
                TODO()
            }
            0x5f -> { // swap
                val type1 = stack.last()
                val type2 = stack[stack.size - 2]
                if (type1 != type2) printer.pop(type1).pop(type2).push(type1).push(type2)
                printer.append("  call \$swap").append(type1).append(type2).append('\n')
            }
            0x60 -> printer.pop(i32).poppush(i32).append("  i32.add\n")
            0x61 -> printer.pop(i64).poppush(i64).append("  i64.add\n")
            0x62 -> printer.pop(f32).poppush(f32).append("  f32.add\n")
            0x63 -> printer.pop(f64).poppush(f64).append("  f64.add\n")
            0x64 -> printer.pop(i32).poppush(i32).append("  i32.sub\n")
            0x65 -> printer.pop(i64).poppush(i64).append("  i64.sub\n")
            0x66 -> printer.pop(f32).poppush(f32).append("  f32.sub\n")
            0x67 -> printer.pop(f64).poppush(f64).append("  f64.sub\n")
            // there are no signed/unsigned versions, because it only shows the last 32 bits
            0x68 -> printer.pop(i32).poppush(i32).append("  i32.mul\n")
            0x69 -> printer.pop(i64).poppush(i64).append("  i64.mul\n")
            0x6a -> printer.pop(f32).poppush(f32).append("  f32.mul\n")
            0x6b -> printer.pop(f64).poppush(f64).append("  f64.mul\n")
            0x6c -> {
                stackPush()
                printer.poppush(i32).append("  call \$safeDiv32\n")
                printer.pop(i32).poppush(i32)
                stackPop()
                handleThrowable()
            }
            0x6d -> {
                stackPush()
                printer.poppush(i64).append("  call \$safeDiv64\n")
                printer.pop(i64).poppush(i64)
                stackPop()
                handleThrowable()
            }
            0x6e -> printer.pop(f32).poppush(f32).append("  f32.div\n")
            0x6f -> printer.pop(f64).poppush(f64).append("  f64.div\n")
            0x70 -> {
                stackPush()
                printer.poppush(i32).append("  call \$checkNonZero32\n")
                stackPop()
                handleThrowable()
                printer.pop(i32).poppush(i32)
                printer.append("  i32.rem_s\n")
            }
            0x71 -> {
                stackPush()
                printer.poppush(i64).append("  call \$checkNonZero64\n")
                stackPop()
                handleThrowable()
                printer.pop(i64).poppush(i64)
                printer.append("  i64.rem_s\n")
            }
            0x72 -> printer.pop(f32).poppush(f32).append("  call \$f32rem\n")
            0x73 -> printer.pop(f64).poppush(f64).append("  call \$f64rem\n")
            0x74 -> printer.poppush(i32).append("  call \$i32neg\n")
            0x75 -> printer.poppush(i64).append("  call \$i64neg\n")
            0x76 -> printer.poppush(f32).append("  f32.neg\n")
            0x77 -> printer.poppush(f64).append("  f64.neg\n")
            0x78 -> printer.pop(i32).poppush(i32).append("  i32.shl\n")
            0x79 -> printer.pop(i32).poppush(i64).append("  i64.extend_i32_s i64.shl\n")
            0x7a -> printer.pop(i32).poppush(i32).append("  i32.shr_s\n")
            0x7b -> printer.pop(i32).poppush(i64).append("  i64.extend_i32_s i64.shr_s\n")
            0x7c -> printer.pop(i32).poppush(i32).append("  i32.shr_u\n")
            0x7d -> printer.pop(i32).poppush(i64).append("  i64.extend_i32_s i64.shr_u\n")
            0x7e -> printer.pop(i32).poppush(i32).append("  i32.and\n")
            0x7f -> printer.pop(i64).poppush(i64).append("  i64.and\n")
            0x80 -> printer.pop(i32).poppush(i32).append("  i32.or\n")
            0x81 -> printer.pop(i64).poppush(i64).append("  i64.or\n")
            0x82 -> printer.pop(i32).poppush(i32).append("  i32.xor\n")
            0x83 -> printer.pop(i64).poppush(i64).append("  i64.xor\n")
            // iinc, has a constant -> different function, i32.const <value>, i32.add
            0x85 -> printer.pop(i32).push(i64).append("  i64.extend_i32_s\n") // i2l
            0x86 -> printer.pop(i32).push(f32).append("  f32.convert_i32_s\n") // i2f
            0x87 -> printer.pop(i32).push(f64).append("  f64.convert_i32_s\n") // i2d
            0x88 -> printer.pop(i64).push(i32).append("  i32.wrap_i64\n") // l2i
            0x89 -> printer.pop(i64).push(f32).append("  f32.convert_i64_s\n") // l2f
            0x8a -> printer.pop(i64).push(f64).append("  f64.convert_i64_s\n") // l2d
            0x8b -> printer.pop(f32).push(i32).append("  call \$f2i\n") // f2i
            0x8c -> printer.pop(f32).push(i64).append("  call \$f2l\n") // f2l
            0x8d -> printer.pop(f32).push(f64).append("  f64.promote_f32\n") // f2d
            0x8e -> printer.pop(f64).push(i32).append("  call \$d2i\n") // d2i
            0x8f -> printer.pop(f64).push(i64).append("  call \$d2l\n") // d2l
            0x90 -> printer.pop(f64).push(f32).append("  f32.demote_f64\n") // d2f

            0x91 -> printer.poppush(i32)
                .append("  i32.const 24 i32.shl\n")
                .append("  i32.const 24 i32.shr_s\n")
            0x92 -> printer.poppush(i32)
                .append("  i32.const 65535 i32.and\n")
            0x93 -> printer.poppush(i32)
                .append("  i32.const 16 i32.shl\n")
                .append("  i32.const 16 i32.shr_s\n")

            0x94 -> printer.pop(i64).pop(i64).push(i32).append("  call \$lcmp\n")
            0x95 -> printer.pop(f32).pop(f32).push(i32).append("  call \$fcmpl\n") // -1 if NaN
            0x96 -> printer.pop(f32).pop(f32).push(i32).append("  call \$fcmpg\n") // +1 if NaN
            0x97 -> printer.pop(f64).pop(f64).push(i32).append("  call \$dcmpl\n") // -1 if NaN
            0x98 -> printer.pop(f64).pop(f64).push(i32).append("  call \$dcmpg\n") // +1 if NaN

            0xbe -> {
                stackPush()
                printer.pop(ptrType).push(i32).append("  call \$al\n")
                stackPop()
                handleThrowable()
            }
            0xbf -> {// athrow, easy :3
                printer.pop(ptrType).push(ptrType)
                stackPush()
                dupI32()
                printer.append(" call \$fIST") // fill in stack trace
                if (comments) printer.append(" ;; athrow\n")
                else printer.append('\n')
                stackPop()
                handleThrowable(true)
                printer.pop(ptrType)
            }
            // 0xc2 -> printer.pop(ptrType).append("  call \$monitorEnter\n") // monitor enter
            // 0xc3 -> printer.pop(ptrType).append("  call \$monitorExit\n") // monitor exit
            0xc2 -> printer.pop(ptrType).append("  drop\n") // monitor enter
            0xc3 -> printer.pop(ptrType).append("  drop\n") // monitor exit
            else -> throw NotImplementedError("unknown op ${OpCode[opcode]}\n")
        }
    }

    override fun visitInvokeDynamicInsn(
        name: String,
        descriptor: String,
        method: Handle,
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
            printer.append("  ;; invoke-dyn $name, $descriptor, $method, [${args.joinToString()}]\n")
        }

        val dst = args[1] as Handle

        val synthClassName = synthClassName(sig, dst)
        val dlu = DelayedLambdaUpdate.needingBridgeUpdate[synthClassName]!!
        val fields = dlu.fields

        // register new class (not visitable)
        printer.append("  i32.const ").append(gIndex.getClassIndex(synthClassName))
        if (dlu.fields.isEmpty() && !dlu.usesSelf) {
            // no instance is needed 😁
            printer.push(ptrType).append(" call \$cip")
            if (comments) printer.append(" ;; ").append(synthClassName)
            printer.append('\n')
        } else {
            stackPush()
            printer.push(ptrType).append(" call \$cr")
            if (comments) printer.append(" ;; ").append(synthClassName)
            printer.append('\n')
            stackPush()
            handleThrowable()
        }

        if (fields.isNotEmpty()) {
            val createdInstance = findOrDefineLocalVar(-2, ptrType).wasmName
            printer.pop(ptrType)
            printer.append("  local.set ").append(createdInstance).append('\n')

            ///////////////////////////////
            // implement the constructor //
            ///////////////////////////////
            // is this the correct order? should be :)
            for (i in fields.lastIndex downTo 0) {
                val arg = fields[i]
                printer.append("  local.get ").append(createdInstance).append("\n  ") // instance
                printer.append(ptrType).append(".const ")
                val offset = gIndex.getFieldOffset(synthClassName, "f$i", arg, false)
                if (offset == null) {
                    printUsed(sig)
                    println("constructor-dependency? ${synthClassName in dIndex.constructorDependencies[sig]!!}")
                    throw NullPointerException("Missing $synthClassName.f$i ($arg), constructable? ${synthClassName in dIndex.constructableClasses}")
                }
                printer.append(offset)
                printer.append(" ").append(ptrType).append(".add")
                if (comments) printer.append(";; set field #$i\n")
                else printer.append('\n')
                // swap ptr and value
                printer.append("  call \$swap").append(jvm2wasm(arg)).append(ptrType)
                printer.append("\n  ").append(getStoreInstr2(arg)).append('\n')
                printer.pop(jvm2wasm(arg))
            }

            // get the instance, ready to continue :)
            printer.push(ptrType)
            printer.append("  local.get ").append(createdInstance).append('\n')
        }

    }

    override fun visitJumpInsn(opcode: Int, label: Label) {
        if (printOps) println("  [jump] ${OpCode[opcode]} -> $label")
        when (opcode) {
            // consume two args for comparison
            0x9f -> printer.pop(i32).pop(i32).append("  i32.eq\n")
            0xa0 -> printer.pop(i32).pop(i32).append("  i32.ne\n")
            0xa1 -> printer.pop(i32).pop(i32).append("  i32.lt_s\n")
            0xa2 -> printer.pop(i32).pop(i32).append("  i32.ge_s\n")
            0xa3 -> printer.pop(i32).pop(i32).append("  i32.gt_s\n")
            0xa4 -> printer.pop(i32).pop(i32).append("  i32.le_s\n")
            0xa5 -> printer.pop(ptrType).pop(ptrType).append("  ").append(ptrType).append(".eq\n")
            0xa6 -> printer.pop(ptrType).pop(ptrType).append("  ").append(ptrType).append(".ne\n")
            0x100 -> {
                // consume one arg for == null
                printer.pop(ptrType).append(" i32.eqz\n") // todo can we switch true/false? would be nice here :)
            }
            0x102 -> printer.pop(ptrType) // consume one arg for != null
            0xc6 -> printer.pop(ptrType).append("  i32.eqz\n") // is null
            0xc7 -> printer.pop(ptrType) // done automatically
            0xa7 -> {} // just goto -> stack doesn't change
            0x99 -> printer.pop(i32)
                .append("  i32.eqz\n") // == 0 // todo can we switch true/false? would be nice here :)
            0x9a -> printer.pop(i32) // != 0; done automatically
            0x9b -> printer.pop(i32).append("  i32.const 0 i32.lt_s\n") // < 0
            0x9c -> printer.pop(i32).append("  i32.const 0 i32.ge_s\n") // >= 0
            0x9d -> printer.pop(i32).append("  i32.const 0 i32.gt_s\n") // > 0
            0x9e -> printer.pop(i32).append("  i32.const 0 i32.le_s\n") // <= 0
            else -> throw NotImplementedError(OpCode[opcode])
        }
        if (comments) printer.append("  ;; jump ${OpCode[opcode]} -> $label, stack: $stack\n")
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
            true -> printer.push(i32).append("  i32.const 1\n")
            false -> printer.push(i32).append("  i32.const 0\n")
            is Int -> printer.push(i32).append("  i32.const ").append(value).append('\n')
            is Long -> printer.push(i64).append("  i64.const ").append(value).append('\n')
            is Float -> {
                printer.push(f32)
                if (value.isFinite()) {
                    printer.append("  f32.const ").append(value).append('\n')
                } else {
                    printer.append("  i32.const ").append(value.toRawBits()).append('\n')
                        .append("  f32.reinterpret_i32\n")
                }
            }
            is Double -> {
                printer.push(f64)
                if (value.isFinite()) {
                    printer.append("  f64.const ").append(value).append('\n')
                } else {
                    printer.append("  i64.const ").append(value.toRawBits()).append('\n')
                        .append("  f64.reinterpret_i64\n")
                }
            }
            is String -> { // pack string into constant memory, and load its address
                printer.push(ptrType)
                val address = gIndex.getString(value)
                printer.append("  ").append(ptrType).append(".const ").append(address)
                if (comments) {
                    printer.append(" ;; \"").append(
                        value.shorten(100)
                            .filter { it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' || it in ".,-!%/()={[]}" }
                            .toString()
                    ).append("\"\n")
                } else {
                    printer.append('\n')
                }
            }
            is Type -> {
                printer.push(i32)
                // used for assert() with getClassLoader()
                printer.append("  i32.const ").append(gIndex.getClassIndex(single(value.descriptor)))
                printer.append(" call \$findClass")
                if (comments) printer.append(" ;; class $value\n")
                else printer.append('\n')
            }
            else -> throw IllegalArgumentException("unknown '$value', ${value.javaClass}\n")
        }
    }

    var line = 0

    @Boring
    override fun visitLineNumber(line: Int, start: Label?) {
        if (printOps) println("Line $line: ($start)")
        if (comments) printer.append("  ;; :$line\n")
        this.line = line
    }

    private fun findLocalVar(i: Int, wasmType: String): LocalVar {
        var v = activeLocalVars.firstOrNull { it.index == i && it.wasmType == wasmType }
        if (v != null) return v
        v = findOrDefineLocalVar(i, wasmType)
        // initialize it once at the start... "synthetic local variable" in JDGui
        nodes.first().printer.prepend("$wasmType.const 0 local.set ${v.wasmName} ;; synthetic\n")
        return v
    }

    private fun findOrDefineLocalVar(i: Int, wasmType: String): LocalVar {
        var v = activeLocalVars.firstOrNull { it.index == i && it.wasmType == wasmType }
        if (v == null) {
            val wasmName = defineLocalVar(i.toString(), wasmType)
            val label = Label()
            v = LocalVar("", wasmType, wasmName, label, label, i)
            activeLocalVars.add(v)
        }
        return v
    }

    // name,type -> newName, because types can change in JVM, but they can't in WASM
    private val localVars = HashMap<Pair<String?, String>, String>()

    override fun visitLocalVariable(
        name: String?,
        descriptor: String,
        signature: String?,
        start: Label,
        end: Label,
        index: Int
    ) {
        // we don't care yet
        /*val wasmType = jvm2wasm1(descriptor)
        val wasmName = defineLocalVar(name, wasmType)
        localVariables.add(LocalVar(descriptor, wasmType, wasmName, start, end, index))
        // this can help with local variables
        if (printOps) println("  local var $name, $descriptor, $signature, $start .. $end, #$index")*/
    }

    private fun defineLocalVar(name: String?, wasmType: String): String {
        return localVars.getOrPut(Pair(name, wasmType)) {
            // register local variable
            val name2 = "\$l${localVars.size}"
            if (methodName(sig) == "AW_clone_Ljava_lang_Object" && name2 == "\$l3") {
                println(localVars)
                println(printer)
                TODO()
            }
            headPrinter.append("  (local $name2 $wasmType)\n")
            name2
        }
    }

    override fun visitMaxs(maxStack: Int, maxLocals: Int) {
        // println(" max stacks: $maxStack, max locals: $maxLocals")
    }

    private fun pop(splitArgs: List<String>, static: Boolean, ret: String) {
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

    private fun checkNotNull0(clazz: String, name: String, getCaller: () -> Unit) {
        stackPush()
        getCaller()
        printer.append(" i32.const ").append(gIndex.getString(clazz))
        printer.append(" i32.const ").append(gIndex.getString(name))
        printer.append(" call \$checkNotNull\n")
        handleThrowable()
        stackPop()
    }

    override fun visitMethodInsn(
        opcode0: Int,
        owner0: String,
        name: String,
        descriptor: String,
        isInterface: Boolean
    ) {
        val owner = reb(owner0)
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

        val owner = reb(owner0)

        if (printOps) println("  [call] ${OpCode[opcode0]}, $owner, $name, $descriptor, $isInterface")

        val i = descriptor.lastIndexOf(')')
        val args = descriptor.substring(1, i)
        val ret = descriptor.substring(i + 1)
        val splitArgs = split1(args).map { jvm2wasm(it) }
        val static = opcode0 == 0xb8
        val sig0 = MethodSig.c(owner, name, descriptor)
        if (static != (sig0 in hIndex.staticMethods))
            throw RuntimeException("Called static/non-static incorrectly, $static vs $sig0 (in $sig)")


        val sig = hIndex.alias(sig0)

        val meth = hIndex.methods.getOrPut(owner) { HashSet() }
        if (sig == sig0 && meth.add(sig0)) {
            if (static) hIndex.staticMethods.add(sig0)
            throw IllegalStateException("Discovered method $sig0 in ${this.sig}")
        }

        var calledCanThrow = canThrowError(sig)
        if (false && sig0 in hIndex.emptyMethods) {
            println("skipping $sig")
            pop(splitArgs, static, ret)
            if (splitArgs.size + (!static).toInt() > 0) {
                printer.append(" ")
                for (j in splitArgs.indices) {
                    printer.append(" drop")
                }
                if (!static) printer.append(" drop")
                printer.append('\n')
            }
        } else {

            fun getCaller() {
                if (splitArgs.isNotEmpty()) {
                    printer.append("  call $").append(gIndex.getNth(listOf(ptrType) + splitArgs))
                } else dupI32()
            }

            when (opcode0) {
                0xb9 -> { // invoke interface
                    // load interface/function index
                    getCaller()
                    printer.append(" i32.const ").append(gIndex.getInterfaceIndex(owner, name, descriptor))
                    // looks up class, goes to interface list, binary searches function, returns func-ptr
                    // instance, function index -> instance, function-ptr
                    printer.push(i32).append(" call \$resolveInterface\n")
                    handleThrowable() // if it's not found or nullptr
                    printer.pop(i32) // pop instance (?)
                    pop(splitArgs, false, ret)

                    stackPush()

                    printer
                        .append("  call_indirect (type ")
                        .append(gIndex.getType(descriptor, calledCanThrow))
                    if (comments) printer.append(") ;; invoke interface $owner, $name, $descriptor\n")
                    else printer.append(")\n")

                    stackPop()

                }
                0xb6 -> { // invoke virtual
                    if (owner[0] != '[' && owner !in dIndex.constructableClasses) {

                        stackPush()

                        getCaller()

                        printer.append(" i32.const ")
                            .append(-gIndex.getString(methodName(sig)))
                            // instance, function index -> function-ptr
                            .append(" call \$resolveIndirect ;; not constructable class\n")
                            .push(i32)
                        handleThrowable()
                        printer.pop(i32)
                        pop(splitArgs, false, ret)

                        printer
                            .append("  call_indirect (type ")
                            .append(gIndex.getType(descriptor, calledCanThrow))
                            .append(if (comments) ") ;; invoke virtual $owner, $name, $descriptor\n" else ")\n")

                        stackPop()

                        /*if (canThrowError) {
                            pop(splitArgs, false, "V")
                            visitLdcInsn("Class not constructable!: $owner")
                            printer.append("  call \$createNullptr\n")
                            visitInsn(0x101) // return error
                            // handleThrowable(true)
                        } else throw IllegalStateException()*/
                    } else if (sig0 in hIndex.finalMethods) {

                        val setter = hIndex.setterMethods[sig]
                        val getter = hIndex.getterMethods[sig]

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
                                if (!ignoreNonCriticalNullPointers) checkNotNull0(owner, name, ::getCaller)
                                pop(splitArgs, false, ret)
                                // final, so not actually virtual;
                                // can be directly called
                                val inline = hIndex.inlined[sig]
                                if (inline != null) {
                                    printer.append("  ").append(inline).append('\n')
                                } else {
                                    stackPush()
                                    val name2 = methodName(sig)
                                    val name3 = methodName(sig0)
                                    if (name3 == "java_lang_Object_hashCode_I" ||
                                        name3 == "java_util_function_Consumer_accept_Ljava_lang_ObjectV_accept_JV" ||
                                        name3 == "me_anno_gpu_OSWindow_addCallbacks_V"
                                    ) throw IllegalStateException("$sig0 -> $sig must not be final!!!")
                                    if (sig in hIndex.abstractMethods) throw IllegalStateException()
                                    gIndex.actuallyUsed.add(this.sig, name2)
                                    printer.append("  call \$").append(name2).append('\n')
                                    stackPop()
                                }
                            }
                        }
                    } else {

                        // method can have well-defined place in class :) -> just precalculate that index
                        // looks up the class, and in the class-function lut, it looks up the function ptr
                        // get the Nth element on the stack, where N = |args|
                        // problem: we don't have generic functions, so we need all combinations
                        getCaller()
                        // +1 for internal VM offset
                        // << 2 for access without shifting
                        // println("$clazz/${this.name}/${this.descriptor} -> $sig0 -> $sig")
                        // printUsed(MethodSig(clazz, this.name, this.descriptor))
                        stackPush()
                        val funcPtr = (gIndex.getDynMethodIdx(sig0) + 1) shl 2
                        printer.append(" i32.const ").append(funcPtr)
                            // instance, function index -> function-ptr
                            .append(" call \$resolveIndirect\n")
                            .push(i32)
                        handleThrowable()
                        printer.pop(i32)
                        pop(splitArgs, false, ret)
                        printer
                            .append("  call_indirect (type ")
                            .append(gIndex.getType(descriptor, calledCanThrow))
                            .append(if (comments) ") ;; invoke virtual $owner, $name, $descriptor\n" else ")\n")
                        stackPop()
                    }
                }
                // typically, <init>, but also can be private or super function; -> no resolution required
                0xb7 -> {
                    if (!ignoreNonCriticalNullPointers) checkNotNull0(owner, name, ::getCaller)
                    pop(splitArgs, false, ret)
                    val inline = hIndex.inlined[sig]
                    if (inline != null) {
                        printer.append("  ").append(inline).append('\n')
                    } else {
                        stackPush()
                        val name2 = methodName(sig)
                        if (sig in hIndex.abstractMethods) throw IllegalStateException()
                        if (name2 == "java_util_function_Consumer_accept_Ljava_lang_ObjectV_accept_JV") {
                            printUsed(sig)
                            throw IllegalStateException()
                        }
                        gIndex.actuallyUsed.add(this.sig, name2)
                        if (name2 == "me_anno_gpu_OSWindow_addCallbacks_V")
                            throw IllegalStateException()
                        printer.append("  call \$").append(name2).append('\n')
                        stackPop()
                    }
                }
                // static, no resolution required
                0xb8 -> {
                    pop(splitArgs, true, ret)
                    val inline = hIndex.inlined[sig]
                    if (inline != null) {
                        printer.append("  ").append(inline).append('\n')
                    } else {
                        stackPush()
                        val name2 = methodName(sig)
                        if (sig in hIndex.abstractMethods) throw IllegalStateException()
                        if (name2 == "java_util_function_Consumer_accept_Ljava_lang_ObjectV_accept_JV") {
                            printUsed(sig)
                            throw IllegalStateException()
                        }
                        gIndex.actuallyUsed.add(this.sig, name2)
                        printer.append("  call \$").append(name2)
                        if (comments) printer.append(" ;; static call\n")
                        else printer.append('\n')
                        stackPop()
                    }
                }
                else -> throw NotImplementedError("unknown call ${OpCode[opcode0]}, $owner, $name, $descriptor, $isInterface\n")
            }
            if (calledCanThrow && checkThrowable) {
                handleThrowable()
            }
        }

        return calledCanThrow
    }

    private fun stackPush() {
        if (canThrowError && enableTracing)
            printer.append("  i32.const ").append(getCallIndex()).append(" call \$stackPush\n")
    }

    private fun stackPop() {
        if (canThrowError && enableTracing)
            printer.append("  call \$stackPop\n")
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

        printer.append("  i32.const ").append(gIndex.getClassIndex(type))
        if (numDimensions == 1) {
            printer.append(" call \$cna")
        } else {
            printer.append(" call \$cma").append(numDimensions)
        }
        printer.append('\n')
        stackPop()
        handleThrowable()

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
        val helper = findOrDefineLocalVar(Int.MAX_VALUE, i32).wasmName
        printer.pop(i32)
        printer.append("  local.set ").append(helper).append('\n')
        for (i in keys.indices) {
            printer.append("  local.get ").append(helper).append(" i32.const ").append(keys[i]).append(" i32.eq\n")
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
        if (comments) printer.append("  ;; try-catch $start .. $end, handler $handler, type $type\n")
        if (printOps) println("  ;; try-catch $start .. $end, handler $handler, type $type")
    }

    private var thIndex = -10
    fun handleThrowable(mustThrow: Boolean = false) {

        // if (line == 371 && sig.clazz == "me/anno/gpu/GFXBase") TODO()

        if (useWASMExceptions) {
            if (mustThrow) {
                // throw :)
                printer.append("  throw \$exTag\n")
            } else {
                // todo nothing will be here :)
            }
            return
        }

        if (!canThrowError) {
            printer.append("  call \$panic\n")
            return
        }

        val catchers = catchers.filter { c ->
            nodes.any { it.label == c.start } && nodes.none { it.label == c.end }
        }

        if (catchers.isNotEmpty()) {

            if (printOps) println("found catcher $catchers")
            if (comments) printer.append("  ;; found catcher\n")

            val oldStack = ArrayList(stack)

            if (catchers.size > 1 || (catchers[0].type != null && catchers[0].type != "java/lang/Throwable")) {

                val throwable = findOrDefineLocalVar(thIndex--, ptrType).wasmName
                if (comments) printer.append("  local.set $throwable ;; multiple/complex catchers\n")
                else printer.append("  local.set ").append(throwable).append('\n')

                var handler = Node(Label())
                nodes.add(handler)

                if (mustThrow) {
                    visitJumpInsn(0xa7, handler.label) // jump to handler
                } else {
                    printer.push(ptrType).append("  local.get $throwable\n")
                    visitJumpInsn(0x102, handler.label) // if not null, jump to handler
                }

                handler.inputStack = ArrayList(stack)
                handler.outputStack = listOf(ptrType)

                for ((i, catcher) in catchers.withIndex()) {

                    if (i == 0) {
                        handler.printer.append(" ")
                        for (e in stack) handler.printer.append(" drop")
                        handler.printer.append(" local.get ").append(throwable).append('\n')
                    }

                    if (catcher.type == null) {
                        handler.ifFalse = null
                        handler.isAlwaysTrue = true
                        handler.ifTrue = catcher.handler
                        return
                    } else {

                        // if condition
                        // throwable -> throwable, throwable, int - instanceOf > throwable, jump-condition
                        dupI32(handler.printer)
                        handler.printer.append(" i32.const ")
                            .append(gIndex.getClassIndex(catcher.type)).append(" call \$io")
                        if (comments) handler.printer.append(" ;; handler #$i/${catchers.size}/$throwable\n")
                        else handler.printer.append('\n')

                        // actual branch
                        val nextHandler = Node(Label())
                        nodes.add(nextHandler)
                        handler.ifFalse = nextHandler
                        handler.ifTrue = catcher.handler

                        handler = nextHandler
                        nextHandler.inputStack = listOf(ptrType)
                        nextHandler.outputStack = listOf(ptrType)
                    }

                }

                // handle if all failed: throw error
                if (comments) handler.printer.append(" ;; must throw\n")
                returnIfThrowable(true, handler)

            } else {

                if (mustThrow) {

                    if (comments) printer.append("  ;; throwing single generic catcher\n")

                    if (printOps) println("--- handler: ${currentNode.label}")

                    val currentNode = currentNode
                    if (stack.isNotEmpty()) {
                        printer.append(" ")
                        for (e in stack.indices) // don't drop exception
                            printer.append(" drop")
                        printer.append("\n")
                    }

                    visitLabel(Label(), true)

                    currentNode.isAlwaysTrue = true
                    currentNode.ifTrue = catchers[0].handler
                    currentNode.outputStack = listOf(ptrType)

                } else {

                    printer.append("  ;; maybe throwing single generic catcher\n")

                    val mainHandler = Node(Label())
                    val throwable = findOrDefineLocalVar(thIndex--, ptrType).wasmName

                    if (printOps) println("--- handler: ${mainHandler.label}")

                    printer.push(ptrType)
                    printer.append("  local.set ").append(throwable).append('\n')
                    printer.append("  local.get ").append(throwable).append('\n')
                    visitJumpInsn(0x9a, mainHandler.label) // if not null

                    mainHandler.inputStack = ArrayList(stack)
                    mainHandler.outputStack = listOf(ptrType)
                    mainHandler.printer.append(" ")
                    for (e in stack) mainHandler.printer.append(" drop")
                    mainHandler.printer.append(" local.get $throwable\n")
                    mainHandler.isAlwaysTrue = true
                    mainHandler.ifTrue = catchers[0].handler
                    nodes.add(mainHandler)
                }

            }

            stack.clear()
            stack.addAll(oldStack)

        } else {
            if (comments) {
                if (mustThrow) {
                    printer.append("  ;; no catcher was found, returning\n")
                } else {
                    printer.append("  ;; no catcher was found, returning maybe\n")
                }
            }
            // easy, inline, without graph :)
            returnIfThrowable(mustThrow)
        }

    }

    private fun returnIfThrowable(
        mustThrow: Boolean,
        currentNode: Node = this.currentNode
    ) {
        val printer = currentNode.printer
        if (mustThrow) {
            if (resultType == 'V') {
                printer.append("  return\n")
            } else {
                val retType = jvm2wasm1(resultType)
                printer.append("  ").append(retType)
                    .append(".const 0 call \$swapi32").append(retType)
                    .append(" return\n")
            }
            if (!printer.endsWith("return\n") && !printer.endsWith("unreachable\n"))
                printer.append("  unreachable\n")
            currentNode.isReturn = true
        } else {
            val tmp = tmpI32
            printer.append("  local.set ").append(tmp)
            printer.append(" local.get ").append(tmp)
            if (resultType == 'V') {
                printer.append(" (if (then local.get ").append(tmp).append(" return))\n")
            } else {
                printer.append(" (if (then ")
                    .append(jvm2wasm1(resultType))
                    .append(".const 0 local.get ").append(tmp).append(" return))\n")
            }
        }
    }

    private val tmpI32 by lazy { findOrDefineLocalVar(-3, "i32").wasmName }

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
        val type = reb(type0)
        if (printOps) println("  [${OpCode[opcode]}] $type")
        when (opcode) {
            0xbb -> {
                // new instance
                stackPush()
                printer.push(ptrType)
                printer.append("  i32.const ").append(gIndex.getClassIndex(type)).append(" call \$cr")
                if (comments) printer.append(" ;; $type\n")
                else printer.append('\n')
                stackPop()
                handleThrowable()
            }
            0xbd -> {
                // a-new array, type doesn't matter
                stackPush()
                printer.pop(i32).push(ptrType)
                printer.append("  call \$ca")
                if (comments) printer.append(" ;; $type\n")
                else printer.append('\n')
                stackPop()
                handleThrowable()
            }
            0xc0 -> {
                // check cast
                stackPush()
                printer.pop(ptrType).push(ptrType)
                printer.append("  i32.const ").append(gIndex.getClassIndex(type)).append(" call \$cc")
                if (comments) printer.append(" ;; $type\n")
                else printer.append('\n')
                stackPop()
                handleThrowable()
            }
            0xc1 -> {
                // instance of
                printer.pop(ptrType).push(i32)
                printer.append("  i32.const ").append(gIndex.getClassIndex(type)).append(" call \$io")
                if (comments) printer.append(" ;; $type\n")
                else printer.append('\n')
            }
            else -> throw NotImplementedError("[type] unknown ${OpCode[opcode]}, $type\n")
        }
    }

    override fun visitVarInsn(opcode: Int, varIndex: Int) {
        visitVarInsn2(opcode, varIndex, argsMapping[varIndex])
    }

    fun visitVarInsn2(opcode: Int, varIndex: Int, map: Int?) {
        if (printOps) println("  [var] local ${OpCode[opcode]}, $varIndex")
        when (opcode) {
            0x15, in 0x1a..0x1d -> printer.push(i32) // iload
            0x16, in 0x1e..0x21 -> printer.push(i64) // lload
            0x17, in 0x22..0x25 -> printer.push(f32)
            0x18, in 0x26..0x29 -> printer.push(f64)
            0x19, in 0x2a..0x2d -> printer.push(ptrType) // aload
            0x36, in 0x3b..0x3e -> printer.pop(i32)
            0x37, in 0x3f..0x42 -> printer.pop(i64)
            0x38, in 0x43..0x46 -> printer.pop(f32)
            0x39, in 0x47..0x4a -> printer.pop(f64)
            0x3a, in 0x4b..0x4e -> printer.pop(ptrType) // astore
            else -> throw NotImplementedError()
        }
        if (map != null) {
            when (opcode) {
                in 0x15..0x2d -> printer.append("  local.get ").append(map)
                    .append('\n') // iload, loads local variable at varIndex
                in 0x36..0x4e -> printer.append("  local.set ").append(map).append('\n') // istore
                else -> throw NotImplementedError()
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
                else -> throw NotImplementedError()
            }.wasmName
            printer.append(if (opcode <= 0x2d) "  local.get " else "  local.set ")
            printer.append(varName)
            printer.append('\n')
        }
    }

    override fun visitIntInsn(opcode: Int, operand: Int) {
        if (printOps) println("  [intInsn] ${OpCode[opcode]}($operand)")
        when (opcode) {
            0x10 -> printer.push(i32).append("  i32.const ").append(operand).append('\n')
            0x11 -> printer.push(i32).append("  i32.const ").append(operand).append('\n')
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
                    .append("  i32.const ").append(gIndex.getClassIndex(type))
                    .append(" call \$cna ;; ").append(type).append('\n')
                stackPop()
                handleThrowable()
            }
            else -> throw NotImplementedError()
        }
    }

    override fun visitLabel(label: Label) {
        visitLabel(label, false)
    }

    private fun visitLabel(label: Label, alwaysTrue: Boolean) {

        val currNode = currentNode
        val nextNode = Node(label)

        currNode.outputStack = ArrayList(stack)

        if (alwaysTrue/* || (currNode.hasNoCode())*/) {
            // find whether we had a good candidate in the past
            var found = false
            for (node in nodes) { // O(n²) -> potentially very slow :/
                if (node.ifTrue == label || node.ifFalse?.label == label) {
                    nextNode.inputStack = node.outputStack
                    // println("found $label :), $stack -> ${node.outputStack}")
                    stack.clear()
                    stack.addAll(node.outputStack!!)
                    found = true
                    break
                }
            }
            if (!found) {
                // println("didn't find $label :/")
                nextNode.inputStack = ArrayList(stack)
            }
        } else {
            nextNode.inputStack = ArrayList(stack)
        }

        nodes.add(nextNode)
        currNode.ifFalse = nextNode

        currentNode = nextNode
        printer = nextNode.printer

        if (printOps) println(" [label] $label")
        if (comments) printer.append("  ;; $label\n")
        for (v in localVariables) {
            when (label) {
                v.start -> activeLocalVars.add(v)
                v.end -> activeLocalVars.remove(v)
            }
        }
    }

    private fun getLoadInstr(descriptor: String) = when (single(descriptor)) {
        "Z", "B" -> "i32.load8_s"
        "S" -> "i32.load16_s"
        "C" -> "i32.load16_u"
        "I" -> "i32.load"
        "J" -> "i64.load"
        "F" -> "f32.load"
        "D" -> "f64.load"
        else -> if (is32Bits) "i32.load" else "i64.load"
    }

    private fun getStoreInstr(descriptor: String) =
        getStoreInstr2(single(descriptor))

    private fun getStoreInstr2(descriptor: String) = when (descriptor) {
        "Z", "B" -> "i32.store8"
        "S", "C" -> "i32.store16"
        "I" -> "i32.store"
        "J" -> "i64.store"
        "F" -> "f32.store"
        "D" -> "f64.store"
        else -> if (is32Bits) "i32.store" else "i64.store"
    }

    private fun callClinit(clazz: String) {
        if (name == "<clinit>" && clazz == this.clazz) {
            if (comments) printer.append("  ;; skipped <clinit>, we're inside of it\n")
            return
        } // it's currently being inited :)
        val sig = MethodSig.c(clazz, "<clinit>", "()V")
        if (hIndex.methods[clazz]?.contains(sig) != true) {
            if (comments) printer.append("  ;; skipped <clinit>, because empty\n")
            return
        }
        val clInitName = methodName(sig)
        gIndex.actuallyUsed.add(this.sig, clInitName)
        stackPush()
        printer.append("  call $").append(clInitName).append('\n')
        stackPop()
        if (canThrowError(sig)) handleThrowable()
    }

    private val precalculateStaticFields = true
    override fun visitFieldInsn(opcode: Int, owner0: String, name: String, descriptor: String) {
        visitFieldInsn2(opcode, reb(owner0), name, descriptor, true)
    }

    private fun dupI32(printer: Builder = this.printer) {
        val tmp = tmpI32
        printer.append("  local.set ").append(tmp)
            .append(" local.get ").append(tmp)
            .append(" local.get ").append(tmp)
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
                printer.append("  drop\n")
            }
            if (setter) { // setter
                // drop value
                if (comments) {
                    printer.append("  drop ;; final $sig\n")
                } else {
                    printer.append("  drop\n")
                }
            } else { // getter
                visitLdcInsn(value) // too easy xD
            }
        } else when (opcode) {
            0xb2 -> {
                // get static
                if (name in enumFieldsNames) {
                    callClinit(owner)
                    printer.append("  i32.const ").append(gIndex.getClassIndex(owner)).append(" call \$findClass")
                    printer.append(" i32.const ").append(
                        gIndex.getFieldOffset(
                            "java/lang/Class",
                            "enumConstants",
                            "[Ljava/lang/Object;",
                            false
                        )!!
                    )
                    if (comments) printer.append(" i32.add i32.load ;; enum values\n")
                    else printer.append(" i32.add i32.load\n")
                    printer.push(wasmType)
                } else if (fieldOffset != null) {
                    callClinit(owner)
                    printer.push(wasmType)
                    // load class index
                    if (precalculateStaticFields) {
                        val staticPtr = staticLookup[owner]!! + fieldOffset
                        printer.append("  i32.const ").append(staticPtr).append(' ')
                    } else {
                        printer
                            .append("  i32.const ").append(gIndex.getClassIndex(owner))
                            .append(" i32.const ").append(fieldOffset)
                            // class index, field offset -> static value
                            .append(" call \$findStatic ")
                    }
                    printer.append(getLoadInstr(descriptor))
                    if (comments) printer.append(" ;; get static '$owner.$name'\n")
                    else printer.append('\n')
                } else {
                    printer.push(wasmType)
                    printer.append("  ").append(wasmType).append(".const 0\n")
                }
            }
            0xb3 -> {
                // put static
                if (name in enumFieldsNames) {
                    printer.append("  i32.const ").append(gIndex.getClassIndex(owner)).append(" call \$findClass")
                    printer.append(" i32.const ").append(
                        gIndex.getFieldOffset(
                            "java/lang/Class",
                            "enumConstants",
                            "[Ljava/lang/Object;",
                            false
                        )!!
                    )
                    if (comments) printer.append(" i32.add call \$swapi32i32 i32.store ;; enum values\n")
                    else printer.append(" i32.add call \$swapi32i32 i32.store\n")
                    printer.pop(wasmType)
                } else if (fieldOffset != null) {
                    callClinit(owner)
                    printer.pop(wasmType)
                    if (precalculateStaticFields) {
                        val staticPtr = staticLookup[owner]!! + fieldOffset
                        printer.append("  i32.const ").append(staticPtr)
                    } else {
                        printer
                            .append("  i32.const ").append(gIndex.getClassIndex(owner))
                            .append(" i32.const ").append(fieldOffset)
                            // ;; class index, field offset -> static value
                            .append(" call \$findStatic")
                    }
                    printer.append(" call \$swap").append(jvm2wasm1(descriptor)).append("i32 ")
                    printer.append(getStoreInstr(descriptor))
                    if (comments) printer.append(" ;; put static '$owner.$name'\n")
                    else printer.append('\n')
                } else {
                    printer.pop(wasmType)
                    printer.append("  drop\n")
                }
            }
            0xb4 -> {
                // get field
                // second part of check is <self>
                if (checkNull && !(!isStatic && printer.endsWith("local.get 0\n"))) {
                    checkNotNull0(owner, name) {
                        dupI32()
                        printer.append('\n')
                    }
                }
                printer.pop(ptrType).push(wasmType)
                if (fieldOffset != null) {
                    printer.append("  i32.const ").append(fieldOffset)
                        .append(" i32.add ").append(getLoadInstr(descriptor))
                    if (comments) {
                        if (owner == clazz) printer.append(" ;; get field '$name'\n")
                        else printer.append(" ;; get field '$owner.$name'\n")
                    } else printer.append('\n')
                } else {
                    printer.append("  drop ").append(wasmType)
                    if (comments) {
                        printer.append(".const 0 ;; dropped getting $name\n")
                    } else {
                        printer.append(".const 0\n")
                    }
                }
            }
            0xb5 -> {
                // set field
                // second part of check is <self>
                if (checkNull && !(!isStatic && printer.endsWith("local.get 0\n  local.get 1\n"))) {
                    checkNotNull0(owner, name) {
                        printer.append("  call $").append(gIndex.getNth(listOf(ptrType, wasmType)))
                    }
                }
                printer.pop(wasmType).pop(ptrType)
                if (fieldOffset != null) {
                    printer.append("  call \$swap$ptrType$wasmType \n") // value <-> instance
                        .append("  i32.const ").append(fieldOffset)
                        .append(" i32.add call \$swap$wasmType$ptrType ") // instance <-> value
                        .append(getStoreInstr(descriptor))
                    if (comments) {
                        if (owner == clazz) printer.append(" ;; set field '$name'\n")
                        else printer.append(" ;; set field '$owner.$name'\n")
                    } else printer.append('\n')
                } else {
                    if (comments) {
                        printer.append("  drop drop ;; dropped putting $name\n")
                    } else {
                        printer.append("  drop drop\n")
                    }
                }
            }
            else -> throw NotImplementedError(OpCode[opcode])
        }
    }

    override fun visitEnd() {
        if (!isAbstract) {

           /* if (methodName(sig) == "me_anno_io_ISaveableXCompanionXregisterCustomClassX2_invoke_Lme_anno_io_ISaveable") {
                for (node in nodes) {
                    println("node $node: ${node.printer}")
                }
            }*/

            val lastNode = nodes.last()
            if (StructuralAnalysis.printOps)
                println("\n$clazz $name $descriptor: ${nodes.size}")

            // jnt.scimark2.FFT.log2(0);

            if (nodes.size > 1 && lastNode.next == null && !lastNode.isReturn) {
                nodes.removeAt(nodes.lastIndex)
                for (it in nodes) {
                    if (it.ifFalse == lastNode) it.ifFalse = null
                }
            }

            var txt = transform(sig, nodes).toString()

         /*   if (methodName(sig) == "me_anno_io_ISaveableXCompanionXregisterCustomClassX2_invoke_Lme_anno_io_ISaveable") {
                println(txt)
            }*/

            txt = txt.replace(
                "local.set \$l0 local.get \$l0 (if (then local.get \$l0 return))\n" +
                        "  i32.const 0\n" +
                        "  return", "return"
            )

            txt = txt.replace(
                "local.set \$l0 local.get \$l0 (if (then i32.const 0 local.get \$l0 return))\n" +
                        "  i32.const 0\n" +
                        "  return", "return"
            )
            txt = txt.replace(
                "local.set \$l0 local.get \$l0 (if (then i64.const 0 local.get \$l0 return))\n" +
                        "  i32.const 0\n" +
                        "  return", "return"
            )
            txt = txt.replace(
                "local.set \$l0 local.get \$l0 (if (then f32.const 0 local.get \$l0 return))\n" +
                        "  i32.const 0\n" +
                        "  return", "return"
            )
            txt = txt.replace(
                "local.set \$l0 local.get \$l0 (if (then f64.const 0 local.get \$l0 return))\n" +
                        "  i32.const 0\n" +
                        "  return", "return"
            )
            txt = txt.replace(
                "(if (then\n" +
                        "  i32.const 0\n" +
                        "  i32.const 0\n" +
                        "  return\n" +
                        ") (else\n" +
                        "  i32.const 1\n" +
                        "  i32.const 0\n" +
                        "  return\n" +
                        "))", "i32.eqz i32.const 0 return"
            )
            txt = txt.replace(
                "(if (result i32) (then\n" +
                        "  i32.const 0\n" +
                        ") (else\n" +
                        "  i32.const 1\n" +
                        "))", "i32.eqz"
            )

            // todo all replacement functions :)

            // these somehow don't work :/
            // they probably have different behaviours for NaN than we expect
            /*txt = txt.replace(
                "call \$dcmpg\n" +
                        "  (if (result i32) (then\n" +
                        "  i32.const 0\n" +
                        ") (else\n" +
                        "  i32.const 1\n" +
                        "))", "f64.eq i32.eqz"
            )

            txt = txt.replace(
                "call \$fcmpg\n" +
                        "  (if (result i32) (then\n" +
                        "  i32.const 0\n" +
                        ") (else\n" +
                        "  i32.const 1\n" +
                        "))", "f32.eq i32.eqz"
            )*/

            txt = txt.replace(
                "call \$lcmp\n" +
                        "  (if (result i32) (then\n" +
                        "  i32.const 0\n" +
                        ") (else\n" +
                        "  i32.const 1\n" +
                        "))", "i64.eq"
            )

            txt = txt.replace("call \$fcmpg\n  i32.const 0 i32.ge_s", "f32.ge")
            txt = txt.replace("call \$dcmpg\n  i32.const 0 i32.ge_s", "f64.ge")
            txt = txt.replace("call \$fcmpg\n  i32.const 0 i32.gt_s", "f32.gt")
            txt = txt.replace("call \$dcmpg\n  i32.const 0 i32.gt_s", "f64.gt")
            txt = txt.replace("call \$fcmpg\n  i32.const 0 i32.le_s", "f32.le")
            txt = txt.replace("call \$dcmpg\n  i32.const 0 i32.le_s", "f64.le")
            txt = txt.replace("call \$fcmpg\n  i32.const 0 i32.lt_s", "f32.lt")
            txt = txt.replace("call \$dcmpg\n  i32.const 0 i32.lt_s", "f64.lt")
            txt = txt.replace("call \$fcmpg\n  i32.const 0 i32.eq", "f32.eq")
            txt = txt.replace("call \$dcmpg\n  i32.const 0 i32.eq", "f64.eq")

            // difference in effect? mmh...
            // if they all work, we have simplified a lot of places :)
            txt = txt.replace("call \$fcmpl\n  i32.const 0 i32.ge_s", "f32.ge")
            txt = txt.replace("call \$dcmpl\n  i32.const 0 i32.ge_s", "f64.ge")
            txt = txt.replace("call \$fcmpl\n  i32.const 0 i32.gt_s", "f32.gt")
            txt = txt.replace("call \$dcmpl\n  i32.const 0 i32.gt_s", "f64.gt")
            txt = txt.replace("call \$fcmpl\n  i32.const 0 i32.le_s", "f32.le")
            txt = txt.replace("call \$dcmpl\n  i32.const 0 i32.le_s", "f64.le")
            txt = txt.replace("call \$fcmpl\n  i32.const 0 i32.lt_s", "f32.lt")
            txt = txt.replace("call \$dcmpl\n  i32.const 0 i32.lt_s", "f64.lt")
            txt = txt.replace("call \$fcmpl\n  i32.const 0 i32.eq", "f32.eq")
            txt = txt.replace("call \$dcmpl\n  i32.const 0 i32.eq", "f64.eq")

            txt = txt.replace("call \$lcmp\n  i32.const 0 i32.gt_s", "i64.gt_s")
            txt = txt.replace("call \$lcmp\n  i32.const 0 i32.ge_s", "i64.ge_s")
            txt = txt.replace("call \$lcmp\n  i32.const 0 i32.lt_s", "i64.lt_s")
            txt = txt.replace("call \$lcmp\n  i32.const 0 i32.le_s", "i64.le_s")
            txt = txt.replace("call \$lcmp\n  i32.const 0 i32.eq", "i64.eq")

            // todo would need a lot more combinations...
            txt = txt.replace(
                "f32.const 0\n" +
                        "  local.get 0\n" +
                        "  f32.sub", "local.get 0 f32.neg"
            )
            txt = txt.replace(
                "f64.const 0\n" +
                        "  local.get 0\n" +
                        "  f64.sub", "local.get 0 f64.neg"
            )
            txt = txt.replace("local.get 0\n  drop", "")
            txt = txt.replace("local.get 1\n  drop", "")
            txt = txt.replace(
                "i32.ne\n" +
                        "  (if (result i32) (then\n" +
                        "  i32.const 0\n" +
                        ") (else\n" +
                        "  i32.const 1\n" +
                        "))", "i32.eq"
            )
            txt = txt.replace(
                "i32.eq\n" +
                        "  (if (result i32) (then\n" +
                        "  i32.const 0\n" +
                        ") (else\n" +
                        "  i32.const 1\n" +
                        "))", "i32.ne"
            )

            txt = txt.replace(
                "call \$dcmpg\n" +
                        "  (if (result i32) (then\n" +
                        "  i32.const 0\n" +
                        ") (else\n" +
                        "  i32.const 1\n" +
                        "))", "f64.eq"
            )
            txt = txt.replace(
                "call \$fcmpg\n" +
                        "  (if (result i32) (then\n" +
                        "  i32.const 0\n" +
                        ") (else\n" +
                        "  i32.const 1\n" +
                        "))", "f32.eq"
            )

            txt = txt.replace("i32.load\n  drop", "drop")
            txt = txt.replace("i64.load\n  drop", "drop")
            txt = txt.replace("f32.load\n  drop", "drop")
            txt = txt.replace("f64.load\n  drop", "drop")
            txt = txt.replace("i32.const 0 i32.eq", "i32.eqz")
            txt = txt.replace("i32.const 0 i32.ne", "i32.nez")
            txt = txt.replace(
                "call \$dupi32 (if (param i32) (then i32.const 0 call \$swapi32i32 return) (else drop))\n" +
                        "  i32.const 0\n" +
                        "  return", "return"
            )
            txt = txt.replace(
                "f64.promote_f32\n" +
                        "  f64.sqrt\n" +
                        "  f32.demote_f64", "f32.sqrt"
            )
            // why ever these constructs exist...
            txt = txt.replace(
                "i32.ne\n" +
                        "  (if (param i32) (result i32 i32) (then\n" +
                        "  i32.const 0\n" +
                        ") (else\n" +
                        "  i32.const 1\n" +
                        "))", "i32.eq"
            )
            txt = txt.replace(
                "(if (result i32) (then\n" +
                        "  i32.const 1\n" +
                        ") (else\n" +
                        "  i32.const 0\n" +
                        "))", "i32.eqz i32.eqz"
            )

            txt = txt.replace(
                "call \$fcmpl\n" +
                        "  (if", "f32.ne\n  (if"
            )
            txt = txt.replace(
                "call \$dcmpl\n" +
                        "  (if", "f64.ne\n  (if"
            )
            txt = txt.replace(
                "call \$fcmpg\n" +
                        "  (if", "f32.ne\n  (if"
            )
            txt = txt.replace(
                "call \$dcmpg\n" +
                        "  (if", "f64.ne\n  (if"
            )

            txt = txt.replace(
                "call \$fcmpl\n" +
                        "  i32.eqz", "f32.eq"
            )
            txt = txt.replace(
                "call \$dcmpl\n" +
                        "  i32.eqz", "f64.eq"
            )
            txt = txt.replace(
                "call \$fcmpg\n" +
                        "  i32.eqz", "f32.eq"
            )
            txt = txt.replace(
                "call \$dcmpg\n" +
                        "  i32.eqz", "f64.eq"
            )

            if (repKey in txt) for (r in repSetter) {
                if (r.first in txt) {
                    txt = txt.replace(r.first, r.second)
                }
            }

            // then add the result to the actual printer
            headPrinter.append(txt)
            headPrinter.append(")\n")
            // java_lang_Class_desiredAssertionStatus_Z
            /*val sig = MethodSig.c(clazz, name, descriptor)
            if (clazz == "java/lang/Class" && name == "desiredAssertionStatus") {
                printUsed(sig)
                println("aliases:")
                for ((alias, sig1) in hIndex.methodAliases.toSortedMap()
                    .filter { it.key.startsWith("java_lang_Class") }) {
                    println("$alias -> $sig1")
                }
                throw IllegalStateException("$sig shall be aliased")
            }*/

          /*  if (methodName(sig) == "me_anno_io_ISaveableXCompanionXregisterCustomClassX2_invoke_Lme_anno_io_ISaveable") {
                LOGGER.debug("contents: $headPrinter")
                LOGGER.debug("old: ${gIndex.translatedMethods[sig]}")
                println("flags: $access")
                throw NotImplementedError("What?!?")
            }*/

            gIndex.translatedMethods[sig] = headPrinter.toString()

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
        const val repKey = "local.get 0\n" +
                "  local.get 1\n" +
                "  call \$swapi32i32 \n" +
                "  i32.const "
        val repSetter = Array(128) {
            val j = it + objectOverhead
            "" +
                    "local.get 0\n" +
                    "  local.get 1\n" +
                    "  call \$swapi32i32 \n" +
                    "  i32.const $j i32.add call \$swapi32i32 i32.store" to
                    "local.get 0 i32.const $j i32.add local.get 1 i32.store"
        }
    }

}