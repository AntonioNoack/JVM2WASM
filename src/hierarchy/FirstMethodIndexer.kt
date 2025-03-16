package hierarchy

import api
import dIndex
import gIndex
import hIndex
import hierarchy.DelayedLambdaUpdate.Companion.getSynthClassName
import hierarchy.DelayedLambdaUpdate.Companion.needingBridgeUpdate
import org.objectweb.asm.*
import replaceClass
import useResultForThrowables
import utils.*
import utils.CommonInstructions.INVOKE_INTERFACE
import utils.CommonInstructions.INVOKE_STATIC
import utils.CommonInstructions.NEW_INSTR
import wasm.instr.CallIndirect
import wasm.instr.FuncType

class FirstMethodIndexer(val sig: MethodSig, val clazz: FirstClassIndexer, val isStatic: Boolean) : MethodVisitor(api) {

    private val calledMethods = HashSet<MethodSig>()
    private val readFields = HashSet<FieldSig>()
    private val writtenFields = HashSet<FieldSig>()
    private val constructedClasses = HashSet<String>()

    private val annotations = ArrayList<Annota>()
    private val interfaceCalls = HashSet<MethodSig>()

    private var usesSelf = false

    private var instructionIndex = 0

    // not static,
    // local.get 0
    // local.get 1
    // field.set
    // return nothing
    private var isSetter = sig.descriptor.params.size == 2 && sig.descriptor.returnType == null

    // not static,
    // local.get 0
    // field.get
    // return sth
    private var isGetter = sig.descriptor.params.isEmpty() && sig.descriptor.returnType != null

    override fun visitIincInsn(varIndex: Int, increment: Int) {
        isSetter = false
        isGetter = false
        instructionIndex++
    }

    override fun visitIntInsn(opcode: Int, operand: Int) {
        isSetter = false
        isGetter = false
        instructionIndex++
    }

    override fun visitInsn(opcode: Int) {
        // todo check for return/areturn etc for setter/getter
        instructionIndex++
    }

    override fun visitJumpInsn(opcode: Int, label: Label?) {
        isSetter = false
        isGetter = false
        instructionIndex++
    }

    override fun visitLookupSwitchInsn(dflt: Label?, keys: IntArray?, labels: Array<out Label>?) {
        isSetter = false
        isGetter = false
        instructionIndex++
    }

    override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label?, vararg labels: Label?) {
        isSetter = false
        isGetter = false
        instructionIndex++
    }

    override fun visitMultiANewArrayInsn(descriptor: String?, numDimensions: Int) {
        isSetter = false
        isGetter = false
        instructionIndex++
    }

    override fun visitMethodInsn(opcode: Int, owner0: String, name: String, descriptor: String, isInterface: Boolean) {
        val owner = Descriptor.parseTypeMixed(owner0)
        visitMethodInsn1(opcode, owner, name, descriptor, isInterface)
    }

    private fun visitMethodInsn1(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {

        isSetter = false
        isGetter = false
        instructionIndex++

        clazz.dep(owner)

        val descriptor1 = Descriptor.c(descriptor)
        for (type in descriptor1.params) clazz.dep(type)
        val retType = descriptor1.returnType
        if (retType != null) clazz.dep(retType)

        val isStatic = opcode == INVOKE_STATIC
        val isInterfaceCall = opcode == INVOKE_INTERFACE
        val sig1 = MethodSig.c(owner, name, descriptor)

        val isNewMethod = HierarchyIndex.registerMethod(sig1)
        if (isNewMethod && opcode != 0) {
            val access =
                if (isStatic) Opcodes.ACC_STATIC
                else if (isInterfaceCall) Opcodes.ACC_INTERFACE
                else 0
            hIndex.methodFlags[sig1] = access
            hIndex.notImplementedMethods.add(sig1)
            // may be a child method
        }

        if (isInterfaceCall) { // invoke interface
            // if (hIndex.classFlags[owner]!!.hasFlag(ACC_INTERFACE))
            // else throw IllegalStateException(sig1.toString())
            interfaceCalls.add(sig1)
        } else {
            calledMethods.add(sig1)
        }
    }

    override fun visitVarInsn(opcode: Int, varIndex: Int) {
        if (!isStatic && (opcode == 0x2a || (opcode == 0x19 && varIndex == 0))) {// local.get 0
            if (instructionIndex != 0) {
                isGetter = false
                isSetter = false
            }
            usesSelf = true
        } else {
            // check for local.get 1
            if (!(varIndex == 1 && opcode in 0x15..0x35 && instructionIndex == 1)) {
                isSetter = false
            }
        }
        instructionIndex++
    }

    override fun visitInvokeDynamicInsn(name: String, descriptor0: String, method: Handle, vararg args: Any) {
        isSetter = false
        isGetter = false
        instructionIndex++
        // this call creates a new instance of a synthetic class
        // sample: CharSequence.chars(); takes CharSequence, returns Supplier()
        // additional parameters for java/lang/invoke/LambdaMetafactory.metafactory:
        // Integer flags (sample: 5,
        //      2 creates a new Class[] from the args, of the following length;
        //      4 creates a new MethodType[] of the following length;
        //      1 is something with Serializables...
        // )

        // to do idea: can we call the original factory,
        // to do and create an instance that way? :) [security flaw, if we host this!!]

        val descriptor = Descriptor.c(descriptor0)
        if (method.owner == "java/lang/invoke/LambdaMetafactory" && (method.name == "metafactory" || method.name == "altMetafactory") && args.size >= 3) {

            val baseClass = "java/lang/Object" //(args[0] as Type).returnType.descriptor
            val interface1 = descriptor.returnType!!
            val dst = args[1] as Handle

            // DynPrinter.print(name, descriptor, method, args)
            // println("dyn $interface1")

            clazz.dep(interface1)

            // there are 3 parts:
            // a) a function, that saves local variables (in call dynamic)
            // b) a function, that creates the interface for calling
            // c) a function, that implements the target, calls b() using variables from a()


            visitMethodInsn1(0, dst.owner, dst.name, dst.desc, false)

            // register new class (not visitable)
            // I really don't know of these called methods are static or not.. are they???
            //  sun/misc/ObjectInputFilter$Config/lambda$static$0 is static
            //  java/util/function/DoubleConsumer/lambda$andThen$0(Ljava_util_function_DoubleConsumer;D)V isn't static
            val calledMethod = MethodSig.c(dst.owner, dst.name, dst.desc)
            val synthClassName = getSynthClassName(sig, dst)
            val print = false// calledMethod.name == "<init>"
            if (print) println(args.joinToString())

            val implementationTargetDesc = (args[0] as Type).toString()

            if (print) {
                println()
                println("---- $sig")
                println("dst.class: ${dst.owner}")
                println("dst.name: ${dst.name}")
                println("dst.desc: ${dst.desc}")
                println("dst.tag: ${dst.tag}")
                println("name: $name")
                println("desc: $descriptor")
                println("target: $implementationTargetDesc")
                println("args: " + args.joinToString())
                println("----")
                // throw IllegalStateException()
            }

            hIndex.classFlags[synthClassName] = 0
            hIndex.registerSuperClass(synthClassName, baseClass)
            hIndex.childClasses.getOrPut(interface1) { HashSet() }.add(synthClassName)
            hIndex.interfaces[synthClassName] = listOf(interface1)
            hIndex.doneClasses.add(synthClassName)
            hIndex.syntheticClasses.add(synthClassName)

            // actual function, which is being implemented by dst
            hIndex.missingClasses.add(synthClassName)

            // bridge isn't static, it just calls the static lambda
            val bridge = MethodSig.c(synthClassName, name, implementationTargetDesc)
            calledMethods.add(bridge)
            hIndex.jvmImplementedMethods.add(bridge)

            clazz.dep(dst.owner)

            calledMethods.add(calledMethod)

            val dlu = DelayedLambdaUpdate(sig.toString(), calledMethod, descriptor, synthClassName, bridge)
            needingBridgeUpdate[synthClassName] = dlu

            dIndex.methodDependencies[bridge] = hashSetOf(calledMethod)

            constructedClasses.add(synthClassName)

            hIndex.registerMethod(bridge)

        } else {
            DynPrinter.print(name, descriptor, method, args)
            throw NotImplementedError()
        }
    }

    override fun visitLdcInsn(value: Any) {
        isGetter = false
        isSetter = false
        instructionIndex++
        if (value is Type) {
            clazz.dep(Descriptor.parseType(value.descriptor))
        }
    }

    override fun visitTypeInsn(opcode: Int, type0: String) {
        isGetter = false
        isSetter = false
        instructionIndex++
        val type = Descriptor.parseTypeMixed(type0)
        clazz.dep(type)
        if (opcode == NEW_INSTR) {
            if (type.endsWith("[]")) throw IllegalStateException(type)
            val type2 = if (type.startsWith("[[") || type.startsWith("[L")) "[]" else type
            constructedClasses.add(type2)
        }
    }

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
        val properties = HashMap<String, Any?>()
        val clazz = Descriptor.parseType(descriptor)
        annotations.add(Annota(clazz, properties))
        if (clazz == Annotations.USED_IF_DEFINED) {
            dIndex.usedMethods.add(sig)
        }
        return object : AnnotationVisitor(api) {
            override fun visit(name: String, value: Any?) {
                properties[name] = value
            }

            override fun visitArray(name: String): AnnotationVisitor {
                val values = ArrayList<Any?>()
                properties[name] = values
                return object : AnnotationVisitor(api) {
                    override fun visit(name: String?, value: Any?) {
                        values.add(value)
                    }
                }
            }
        }
    }

    override fun visitTryCatchBlock(start: Label?, end: Label?, handler: Label?, type: String?) {
        isGetter = false
        isSetter = false
        instructionIndex++
        if (type != null) clazz.dep(replaceClass(type))
    }

    private var lastField: FieldSig? = null
    override fun visitFieldInsn(opcode: Int, owner0: String, name: String, descriptor: String) {

        if (instructionIndex != 1 && isGetter) isGetter = false
        if (instructionIndex != 2 && isSetter) isSetter = false
        instructionIndex++

        val owner = replaceClass(owner0)
        clazz.dep(owner)

        // only getters are of importance, because setters without getters don't matter
        val type = Descriptor.parseType(descriptor)
        val sig = FieldSig(owner, name, type, opcode < 0xb4)
        lastField = sig
        // final fields can be inlined :)
        if (sig !in hIndex.finalFields) {
            // fields are only relevant if they are both written and read
            when (opcode) {
                0xb2 -> readFields.add(sig)// get static
                0xb3 -> writtenFields.add(sig)  // put static
                0xb4 -> readFields.add(sig)   // get field
                0xb5 -> writtenFields.add(sig)  // put field
            }
        }
    }

    private fun defineCallIndirectWASM(params: List<String>, results: List<String>) {
        defineCallIndirectWASM(FuncType(params, results))
    }

    private fun defineCallIndirectWASM(funcType: FuncType) {
        val callInstr = CallIndirect(funcType).toString()
        annotations.add(Annota(Annotations.WASM, mapOf("code" to callInstr)))
        gIndex.types.add(funcType)
    }

    override fun visitEnd() {

        if (calledMethods.isNotEmpty()) dIndex.methodDependencies[sig] = calledMethods
        if (readFields.isNotEmpty()) dIndex.getterDependencies[sig] = readFields
        if (writtenFields.isNotEmpty()) dIndex.setterDependencies[sig] = writtenFields

        if (constructedClasses.isNotEmpty()) {
            dIndex.constructorDependencies[sig] = constructedClasses
        }

        val throws = useResultForThrowables && !hIndex.hasAnnotation(sig, Annotations.NO_THROW)

        if (sig.clazz == "jvm/JavaReflect" && sig.name == "callConstructor") {
            defineCallIndirectWASM(listOf(ptrType), if (throws) listOf(ptrType) else emptyList())
        } else if (sig.clazz == "jvm/lang/JavaLangAccessImpl" && sig.name == "callStaticInit") {
            defineCallIndirectWASM(emptyList(), if (throws) listOf(ptrType) else emptyList())
        } else if (sig.clazz == "jvm/JavaReflectMethod" && sig.name.startsWith("invoke")) {
            // confirm that the method is native???
            val callSignature = CallSignature.c(sig, removeLastParam = true)
            hIndex.implementedCallSignatures.add(callSignature)
            defineCallIndirectWASM(callSignature.toFuncType())
        }

        if (annotations.isNotEmpty()) {
            hIndex.annotations[sig] = annotations
        }

        if (interfaceCalls.isNotEmpty()) {
            dIndex.interfaceDependencies[sig] = interfaceCalls
            dIndex.knownInterfaceDependencies.addAll(interfaceCalls)
        }

        if (lastField != null) {
            if (isGetter && instructionIndex != 3) isGetter = false
            if (isSetter && instructionIndex != 4) isSetter = false
            if (isGetter) hIndex.getterMethods[sig] = lastField!!
            if (isSetter) hIndex.setterMethods[sig] = lastField!!
        }

        if (instructionIndex == 1) { // only Return
            hIndex.emptyFunctions.add(sig)
        }

        hIndex.usesSelf[sig] = usesSelf
    }

}