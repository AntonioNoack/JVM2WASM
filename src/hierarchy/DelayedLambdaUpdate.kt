package hierarchy

import useResultForThrowables
import dIndex
import gIndex
import hIndex
import me.anno.utils.assertions.assertFalse
import me.anno.utils.assertions.assertTrue
import org.objectweb.asm.Handle
import translator.MethodTranslator
import translator.MethodTranslator.Companion.comments
import utils.*
import wasm.instr.Const.Companion.i32Const0
import wasm.instr.Instructions.Return

class DelayedLambdaUpdate(
    private val source: String,
    val calledMethod: MethodSig,
    val descriptor: Descriptor,
    private val synthClassName: String,
    val bridgeMethod: MethodSig,
) {

    private fun isNative(type: String?): Boolean {
        return type != null && type in NativeTypes.nativeTypes
    }

    private fun boxUnbox(arg: String?, arg2: String?, printer: MethodTranslator, checkThrowable: Boolean) {
        if (isNative(arg)) box(arg, arg2, printer, checkThrowable)
        else unbox(arg, arg2, printer, checkThrowable)
    }

    private fun box(arg: String?, arg2: String?, printer: MethodTranslator, checkThrowable: Boolean) {
        assertTrue(isNative(arg))
        val invokeStatic = 0xb8
        when (arg) {
            "byte" -> printer.visitMethodInsn2(
                invokeStatic, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;",
                false, checkThrowable
            )
            "short" -> printer.visitMethodInsn2(
                invokeStatic, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;",
                false, checkThrowable
            )
            "char" -> printer.visitMethodInsn2(
                invokeStatic, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;",
                false, checkThrowable
            )
            "int" -> printer.visitMethodInsn2(
                invokeStatic, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;",
                false, checkThrowable
            )
            "long" -> printer.visitMethodInsn2(
                invokeStatic, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;",
                false, checkThrowable
            )
            "float" -> printer.visitMethodInsn2(
                invokeStatic, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;",
                false, checkThrowable
            )
            "double" -> printer.visitMethodInsn2(
                invokeStatic, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;",
                false, checkThrowable
            )
            "boolean" -> printer.visitMethodInsn2(
                invokeStatic, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;",
                false, checkThrowable
            )
            else -> throw NotImplementedError("$arg/$arg2")
        }
    }

    private fun unbox(arg: String?, arg2: String?, printer: MethodTranslator, checkThrowable: Boolean) {
        assertFalse(isNative(arg))
        val invokeVirtual = 0xb6
        when (arg2) {
            "byte" -> printer.visitMethodInsn2(
                invokeVirtual, "java/lang/Byte", "byteValue", "()B",
                false, checkThrowable
            )
            "short" -> printer.visitMethodInsn2(
                invokeVirtual, "java/lang/Short", "shortValue", "()S",
                false, checkThrowable
            )
            "char" -> printer.visitMethodInsn2(
                invokeVirtual, "java/lang/Character", "charValue", "()C",
                false, checkThrowable
            )
            "int" -> printer.visitMethodInsn2(
                invokeVirtual, "java/lang/Integer", "intValue", "()I",
                false, checkThrowable
            )
            "long" -> printer.visitMethodInsn2(
                invokeVirtual, "java/lang/Long", "longValue", "()J",
                false, checkThrowable
            )
            "float" -> printer.visitMethodInsn2(
                invokeVirtual, "java/lang/Float", "floatValue", "()F",
                false, checkThrowable
            )
            "double" -> printer.visitMethodInsn2(
                invokeVirtual, "java/lang/Double", "doubleValue", "()D",
                false, checkThrowable
            )
            "boolean" -> printer.visitMethodInsn2(
                invokeVirtual, "java/lang/Boolean", "booleanValue", "()Z",
                false, checkThrowable
            )
            else -> throw NotImplementedError("$arg/$arg2")
        }
    }

    fun indexFields() {
        // load all fields
        val fields = descriptor.params
            .mapIndexed { i, type ->
                val fieldName = "f$i"
                gIndex.getFieldOffset(synthClassName, fieldName, type, false)!!
                FieldSig(synthClassName, fieldName, type, false)
            }.toMutableSet()
        if (needsSelf) fields += FieldSig(synthClassName, "self", "java/lang/Object", false)
        dIndex.getterDependencies[bridgeMethod] = fields
        dIndex.setterDependencies[bridgeMethod] = fields
    }

    private val isInterface
        get() = hIndex.isInterfaceClass(calledMethod.clazz) &&
                calledMethod in hIndex.abstractMethods
    private val callingStatic get() = calledMethod in hIndex.staticMethods
    private val needsSelf get() = (!callingStatic || !isInterface)
    val usesSelf get() = hIndex.usesSelf[calledMethod] == true

    fun generateSyntheticMethod() {

        // build synthetic method code
        val mt = MethodTranslator(0, bridgeMethod.clazz, bridgeMethod.name, bridgeMethod.descriptor)
        val wantedDescriptor = calledMethod.descriptor
        val wantedParams = wantedDescriptor.params

        val print = false

        if (comments) mt.printer.comment("synthetic lambda")

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
                if (comments) mt.printer.comment("[0] += self")
                mt.visitVarInsn(0x2a, 0) // local.get this
                mt.visitFieldInsn2(0xb4, synthClassName, "self", "java/lang/Object", false) // get field
            } else {
                mt.printer.append(i32Const0)
                if (comments) mt.printer.comment("self, unused")
            }
            if (!callingStatic) k--
        }

        val fields = descriptor.params
        for (i in fields.indices) {
            val arg = fields[i]
            val fieldName = "f$i"
            val wanted = wantedParams.getOrNull(k++)
            if (print) println("[1] += $arg ($wanted)")
            if (comments) mt.printer.comment("[1] += $arg")
            // load self for field
            mt.visitVarInsn(0x2a, 0) // local.get this
            mt.visitFieldInsn2(0xb4, synthClassName, fieldName, arg, false) // get field
            if (isNative(arg) != (if (k == 0) false else isNative(wanted!!))) {
                boxUnbox(arg, wanted ?: "", mt, true)
            }
        }

        // append arguments to this function as values
        val fields2 = bridgeMethod.descriptor.params
        for (i in fields2.indices) {
            val arg = fields2[i]
            val wanted = wantedParams.getOrNull(k++)
            if (print) println("[2] += $arg ($wanted)")
            if (comments) mt.printer.comment("[2] += $arg")
            val opcode = when (arg) {
                "boolean", "char", "byte", "short", "int" -> 0x15
                "long" -> 0x16
                "float" -> 0x17
                "double" -> 0x18
                else -> 0x19
            }
            mt.visitVarInsn2(opcode, mt.variables.localVarsAndParams[i + 1])
            if (isNative(arg) != (if (k == 0) false else isNative(wanted!!))) {
                boxUnbox(arg, wanted ?: "", mt, true)
            }
        }

        // java.util.stream.LongPipeline
        // java.lang.Long
        // java.util.stream.Nodes.CollectorTask.OfInt

        if (print) println("calling $calledMethod")

        // call the original function
        val isConstructor = calledMethod.name == INSTANCE_INIT
        if (isConstructor) {
            // to do we have to register this potentially as creating a new class
            val clazz = calledMethod.clazz
            mt.visitTypeInsn(0xbb, clazz)
            // if is new instance, duplicate result
            mt.visitInsn(0x59)
        }

        var couldThrow = mt.visitMethodInsn2(
            if (isInterface) 0xb9 else if (callingStatic) 0xb8 else 0xb6,
            calledMethod, isInterface, false
        )

        // java/util/Spliterator$OfInt/tryAdvance(Ljava/util/function/Consumer;)Z
        // java.util.Spliterator.OfInt
        // java.util.stream.SpinedBuffer.OfInt
        // java.util.stream.StreamSpliterators.IntWrappingSpliterator
        // java.util.stream.Collectors.toList()

        // error code; this method will always be able to throw errors, because we cannot annotate it (except maybe later)

        // unboxing for result
        val ret0 = if (isConstructor) "java/lang/Object" else wantedDescriptor.returnType
        val ret1 = bridgeMethod.descriptor.returnType
        if (ret0 != ret1 && isNative(ret0) != isNative(ret1)) {
            if (couldThrow) mt.handleThrowable()
            boxUnbox(ret0, ret1, mt, false)
            couldThrow = true
        }

        if (!couldThrow && useResultForThrowables) {
            // calling this is easier than figuring our the return type for the correct return function
            mt.visitLdcInsn(0)
        }

        mt.printer.append(Return)
        mt.visitEnd()
    }

    companion object {

        val needingBridgeUpdate = HashMap<String, DelayedLambdaUpdate>(4096)

        fun getSynthClassName(sig: MethodSig, dst: Handle): String { // should be as unique as possible
            return methodName2(dst.owner, dst.name, dst.desc) + "x" +
                    methodName2(sig.clazz, sig.name, sig.descriptor.raw).hashCode().toString(16)
        }
    }
}