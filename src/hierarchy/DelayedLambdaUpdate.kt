package hierarchy

import anyMethodThrows
import dIndex
import gIndex
import hIndex
import me.anno.utils.assertions.assertFalse
import me.anno.utils.assertions.assertTrue
import org.objectweb.asm.Handle
import translator.MethodTranslator
import utils.FieldSig
import utils.MethodSig
import utils.methodName2
import utils.split1
import wasm.instr.Const.Companion.i32Const0
import wasm.instr.Instructions.Return

class DelayedLambdaUpdate(
    private val source: String,
    val calledMethod: MethodSig,
    private val descriptor: String,
    val synthClassName: String,
    val bridgeMethod: MethodSig,
) {

    private fun isNative(type: String): Boolean {
        return type[0] != 'L' && type[0] != 'T' && type[0] !in "[A"
    }

    private fun boxUnbox(arg: String, arg2: String, printer: MethodTranslator, checkThrowable: Boolean) {
        if (isNative(arg)) box(arg, arg2, printer, checkThrowable)
        else unbox(arg, arg2, printer, checkThrowable)
    }

    private fun box(arg: String, arg2: String, printer: MethodTranslator, checkThrowable: Boolean) {
        assertTrue(isNative(arg))
        val invokeStatic = 0xb8
        when (arg) {
            "B" -> printer.visitMethodInsn2(
                invokeStatic, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;",
                false, checkThrowable
            )
            "S" -> printer.visitMethodInsn2(
                invokeStatic, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;",
                false, checkThrowable
            )
            "C" -> printer.visitMethodInsn2(
                invokeStatic, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;",
                false, checkThrowable
            )
            "I" -> printer.visitMethodInsn2(
                invokeStatic, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;",
                false, checkThrowable
            )
            "J" -> printer.visitMethodInsn2(
                invokeStatic, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;",
                false, checkThrowable
            )
            "F" -> printer.visitMethodInsn2(
                invokeStatic, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;",
                false, checkThrowable
            )
            "D" -> printer.visitMethodInsn2(
                invokeStatic, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;",
                false, checkThrowable
            )
            "Z" -> printer.visitMethodInsn2(
                invokeStatic, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;",
                false, checkThrowable
            )
            else -> throw NotImplementedError("$arg/$arg2")
        }
    }

    private fun unbox(arg: String, arg2: String, printer: MethodTranslator, checkThrowable: Boolean) {
        assertFalse(isNative(arg))
        val invokeVirtual = 0xb6
        when (arg2) {
            "B" -> printer.visitMethodInsn2(
                invokeVirtual, "java/lang/Byte", "byteValue", "()B",
                false, checkThrowable
            )
            "S" -> printer.visitMethodInsn2(
                invokeVirtual, "java/lang/Short", "shortValue", "()S",
                false, checkThrowable
            )
            "C" -> printer.visitMethodInsn2(
                invokeVirtual, "java/lang/Character", "charValue", "()C",
                false, checkThrowable
            )
            "I" -> printer.visitMethodInsn2(
                invokeVirtual, "java/lang/Integer", "intValue", "()I",
                false, checkThrowable
            )
            "J" -> printer.visitMethodInsn2(
                invokeVirtual, "java/lang/Long", "longValue", "()J",
                false, checkThrowable
            )
            "F" -> printer.visitMethodInsn2(
                invokeVirtual, "java/lang/Float", "floatValue", "()F",
                false, checkThrowable
            )
            "D" -> printer.visitMethodInsn2(
                invokeVirtual, "java/lang/Double", "doubleValue", "()D",
                false, checkThrowable
            )
            "Z" -> printer.visitMethodInsn2(
                invokeVirtual, "java/lang/Boolean", "booleanValue", "()Z",
                false, checkThrowable
            )
            else -> throw NotImplementedError("$arg/$arg2")
        }
    }

    fun indexFields() {
        // load all fields
        val ix = descriptor.lastIndexOf(')')
        val fields = split1(descriptor.substring(1, ix), true)
            .mapIndexed { i, arg ->
                val fieldName = "f$i"
                gIndex.getFieldOffset(synthClassName, fieldName, arg, false)!!
                FieldSig(synthClassName, fieldName, arg, false)
            }.toMutableSet()
        if (needsSelf) fields += FieldSig(synthClassName, "self", "Ljava/lang/Object;", false)
        dIndex.fieldDependenciesR[bridgeMethod] = fields
        dIndex.fieldDependenciesW[bridgeMethod] = fields
    }

    // load all fields
    val fields: List<String>

    init {
        val ix = descriptor.lastIndexOf(')')
        fields = split1(descriptor.substring(1, ix), true)
    }

    private val isInterface
        get() = hIndex.isInterfaceClass(calledMethod.clazz) &&
                calledMethod in hIndex.abstractMethods
    private val callingStatic get() = calledMethod in hIndex.staticMethods
    private val needsSelf get() = (!callingStatic || !isInterface)
    val usesSelf get() = hIndex.usesSelf[calledMethod] == true

    fun generateSyntheticMethod() {

        // build synthetic method code
        val printer = MethodTranslator(0, bridgeMethod.clazz, bridgeMethod.name, bridgeMethod.descriptor)
        val wantedDescriptor = calledMethod.descriptor
        val ix3 = wantedDescriptor.lastIndexOf(')')
        val wantedParams = split1(wantedDescriptor.substring(1, ix3), true)

        val print = false

        printer.printer.comment("synthetic lambda")

        if (print) {
            println()
            println("src: $source")
            println("desc: $descriptor")
            println("bridge: $bridgeMethod")
            println("called: $calledMethod")
            println("${calledMethod.descriptor} -> $wantedParams")
        }

        val needsSelf = needsSelf
        var k = 0

        // todo load null, if the instance is not being used
        // todo this then saves us from creating an actual instance
        if (needsSelf) {
            if (print) println("[0] += self")
            if (usesSelf) {
                printer.printer.comment("[0] += self")
                printer.visitVarInsn(0x2a, 0) // local.get this
                printer.visitFieldInsn2(0xb4, synthClassName, "self", "Ljava/lang/Object;", false) // get field
            } else {
                printer.printer.append(i32Const0).comment("self, unused")
            }
            if (!callingStatic) k--
        }

        for (i in fields.indices) {
            val arg = fields[i]
            val fieldName = "f$i"
            val wanted = wantedParams.getOrNull(k++)
            if (print) println("[1] += $arg ($wanted)")
            printer.printer.comment("[1] += $arg")
            // load self for field
            printer.visitVarInsn(0x2a, 0) // local.get this
            printer.visitFieldInsn2(0xb4, synthClassName, fieldName, arg, false) // get field
            if (isNative(arg) != (if (k == 0) false else isNative(wanted!!))) {
                boxUnbox(arg, wanted ?: "", printer, true)
            }
        }

        // append arguments to this function as values
        val ix2 = bridgeMethod.descriptor.lastIndexOf(')')
        val fields2 = split1(bridgeMethod.descriptor.substring(1, ix2), true)
        for (i in fields2.indices) {
            val arg = fields2[i]
            val wanted = wantedParams.getOrNull(k++)
            if (print) println("[2] += $arg ($wanted)")
            printer.printer.comment("[2] += $arg")
            val opcode = when (arg[0]) {
                'Z', 'C', 'B', 'S', 'I' -> 0x15
                'J' -> 0x16
                'F' -> 0x17
                'D' -> 0x18
                else -> 0x19
            }
            // index is not correctly incremented for double/long
            printer.visitVarInsn2(opcode, -1, i + 1)
            if (isNative(arg) != (if (k == 0) false else isNative(wanted!!))) {
                boxUnbox(arg, wanted ?: "", printer, true)
            }
        }

        // java.util.stream.LongPipeline
        // java.lang.Long
        // java.util.stream.Nodes.CollectorTask.OfInt

        if (print) println("calling $calledMethod")

        // call the original function
        val isConstructor = calledMethod.name == "<init>"
        if (isConstructor) {
            // to do we have to register this potentially as creating a new class
            val clazz = calledMethod.clazz
            printer.visitTypeInsn(0xbb, clazz)
            // if is new instance, duplicate result
            printer.visitInsn(0x59)
        }

        var couldThrow = printer.visitMethodInsn2(
            if (isInterface) 0xb9 else if (callingStatic) 0xb8 else 0xb6,
            calledMethod.clazz,
            calledMethod.name,
            calledMethod.descriptor,
            isInterface,
            false
        )

        // java/util/Spliterator$OfInt/tryAdvance(Ljava/util/function/Consumer;)Z
        // java.util.Spliterator.OfInt
        // java.util.stream.SpinedBuffer.OfInt
        // java.util.stream.StreamSpliterators.IntWrappingSpliterator
        // java.util.stream.Collectors.toList()

        // error code; this method will always be able to throw errors, because we cannot annotate it (except maybe later)

        // unboxing for result
        val ret0 = if (isConstructor) "Ljava/lang/Object;" else wantedDescriptor.substring(ix3 + 1)
        val ret1 = bridgeMethod.descriptor.substring(ix2 + 1)
        if (ret0 != ret1 && isNative(ret0) != isNative(ret1)) {
            if (couldThrow) printer.handleThrowable()
            boxUnbox(ret0, ret1, printer, false)
            couldThrow = true
        }

        if (!couldThrow && anyMethodThrows) {
            // calling this is easier than figuring our the return type for the correct return function
            printer.visitLdcInsn(0)
        }

        printer.printer.append(Return)
        printer.visitEnd()
    }

    companion object {

        val needingBridgeUpdate = HashMap<String, DelayedLambdaUpdate>()

        fun synthClassName(sig: MethodSig, dst: Handle): String { // should be as unique as possible
            return methodName2(dst.owner, dst.name, dst.desc) + "x" +
                    methodName2(sig.clazz, sig.name, sig.descriptor).hashCode().toString(16)
        }
    }
}