package hierarchy

import anyMethodThrows
import api
import dIndex
import dyninvoke.DynPrinter
import hIndex
import hierarchy.DelayedLambdaUpdate.Companion.needingBridgeUpdate
import hierarchy.DelayedLambdaUpdate.Companion.synthClassName
import org.objectweb.asm.*
import replaceClass1
import utils.*

class FirstMethodIndexer(val sig: MethodSig, val clazz: FirstClassIndexer, val isStatic: Boolean) : MethodVisitor(api) {

    private val methods = HashSet<MethodSig>()
    private val fieldsR = HashSet<FieldSig>()
    private val fieldsW = HashSet<FieldSig>()
    private val constructed = HashSet<String>()

    private val annotations = ArrayList<Annota>()
    private val interfaceCalls = HashSet<MethodSig>()

    private var usesSelf = false

    private var instructionIndex = 0

    // not static,
    // local.get 0
    // local.get 1
    // field.set
    // return nothing
    val args = split2(sig.descriptor)
    private var isSetter = args.size == 2 && args.last() == "V"

    // not static,
    // local.get 0
    // field.get
    // return sth
    private var isGetter = args.size == 1 && args.last() != "V"

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

    override fun visitMethodInsn(
        opcode: Int, owner: String, name: String, descriptor: String,
        isInterface: Boolean
    ) {

        isSetter = false
        isGetter = false
        instructionIndex++

        clazz.dep(owner)
        val ix = descriptor.lastIndexOf(')')
        for (type in split1(descriptor.substring(1, ix))) {
            clazz.dep(type)
        }
        if (!descriptor.endsWith(")V")) {
            clazz.dep(single(descriptor.substring(ix + 1)))
        }

        val sig1 = MethodSig.c(owner, name, descriptor)

        val static = opcode == 0xb8
        val isNewMethod = hIndex.methods.getOrPut(owner) { HashSet() }.add(sig1)
        if (isNewMethod) {
            if (static) hIndex.staticMethods.add(sig1)
            if (hIndex.notImplementedMethods.add(sig1)) {
                hIndex.hasSuperMaybeMethods.add(sig1)
            }
            // may be a child method
        }

        if (opcode == 0xb9) { // invoke interface
            // if (hIndex.classFlags[owner]!!.hasFlag(ACC_INTERFACE))
            // else throw IllegalStateException(sig1.toString())
            interfaceCalls.add(sig1)
        } else {
            methods.add(sig1)
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

    override fun visitInvokeDynamicInsn(
        name: String,
        descriptor: String,
        method: Handle,
        vararg args: Any
    ) {
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

        if (method.owner == "java/lang/invoke/LambdaMetafactory" && (method.name == "metafactory" || method.name == "altMetafactory") && args.size >= 3) {

            val baseClass = "java/lang/Object" //(args[0] as Type).returnType.descriptor
            val interface1 = single(descriptor.substring(descriptor.lastIndexOf(')') + 1))
            val dst = args[1] as Handle

            visitMethodInsn(0, dst.owner, dst.name, dst.desc, false)

            // DynPrinter.print(name, descriptor, method, args)
            // println("dyn $interface1")

            clazz.dep(interface1)

            // there are 3 parts:
            // a) a function, that saves local variables (in call dynamic)
            // b) a function, that creates the interface for calling
            // c) a function, that implements the target, calls b() using variables from a()

            // register new class (not visitable)
            val calledMethod = MethodSig.c(dst.owner, dst.name, dst.desc)
            val synthClassName = synthClassName(sig, dst)
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

            visitMethodInsn(0, dst.owner, dst.name, dst.desc, false)

            val bridge = MethodSig.c(synthClassName, name, implementationTargetDesc)
            methods.add(bridge)
            hIndex.jvmImplementedMethods.add(bridge)

            clazz.dep(dst.owner)

            methods.add(calledMethod)

            val dlu = DelayedLambdaUpdate("$sig", calledMethod, descriptor, synthClassName, bridge)
            needingBridgeUpdate[synthClassName] = dlu

            dIndex.methodDependencies[bridge] = hashSetOf(calledMethod)

            constructed.add(synthClassName)

            hIndex.methods[synthClassName] = hashSetOf(bridge)

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
            clazz.dep(single(value.descriptor))
        }
    }

    override fun visitTypeInsn(opcode: Int, type0: String) {
        isGetter = false
        isSetter = false
        instructionIndex++
        val type = replaceClass1(type0)
        clazz.dep(type)
        if (opcode == 0xbb) {
            if (type.endsWith("[]")) throw IllegalStateException(type)
            val type2 = if (type.startsWith("[[") || type.startsWith("[L")) "[]" else type
            constructed.add(type2)
        }
    }

    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
        val properties = HashMap<String, Any?>()
        val clazz = single(descriptor)
        annotations.add(Annota(clazz, properties))
        if (clazz == "annotations/UsedIfIndexed") {
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
        if (type != null) clazz.dep(replaceClass1(type))
    }

    private var lastField: FieldSig? = null
    override fun visitFieldInsn(opcode: Int, owner0: String, name: String, descriptor: String) {

        if (instructionIndex != 1 && isGetter) isGetter = false
        if (instructionIndex != 2 && isSetter) isSetter = false
        instructionIndex++

        val owner = replaceClass1(owner0)
        clazz.dep(owner)

        // only getters are of importance, because setters without getters don't matter
        val sig = FieldSig(owner, name, descriptor, opcode < 0xb4)
        lastField = sig
        // final fields can be inlined :)
        if (sig !in hIndex.finalFields) {
            // fields are only relevant if they are both written and read
            when (opcode) {
                0xb2 -> {
                    // get
                    fieldsR.add(sig)
                    // methods.add(MethodSig.c(owner, "<clinit>", "()V"))
                }

                0xb3 -> {
                    // put static
                    fieldsW.add(sig)
                    // methods.add(MethodSig.c(owner, "<clinit>", "()V"))
                }

                0xb4 -> {
                    // get field
                    fieldsR.add(sig)
                }

                0xb5 -> {
                    // put field
                    fieldsW.add(sig)
                }
            }
        }
    }

    override fun visitEnd() {

        if (methods.isNotEmpty()) dIndex.methodDependencies[sig] = methods
        if (fieldsR.isNotEmpty()) dIndex.fieldDependenciesR[sig] = fieldsR
        if (fieldsW.isNotEmpty()) dIndex.fieldDependenciesW[sig] = fieldsW

        if (constructed.isNotEmpty()) {
            dIndex.constructorDependencies[sig] = constructed
        }

        if (sig.clazz == "jvm/JavaReflect" && sig.name == "callConstructor") {
            val callInstr =
                if (anyMethodThrows) "call_indirect (type \$iXi)"
                else "call_indirect (type \$iX)"
            annotations.add(Annota("annotations/WASM", mapOf("code" to callInstr)))
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