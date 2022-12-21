package hierarchy

import api
import dIndex
import dyninvoke.DynPrinter
import hIndex
import hierarchy.DelayedLambdaUpdate.Companion.needingBridgeUpdate
import hierarchy.DelayedLambdaUpdate.Companion.synthClassName
import org.objectweb.asm.*
import reb
import utils.single
import utils.split1
import utils.split2
import utils.FieldSig
import utils.GenericSig
import utils.MethodSig
import utils.printUsed

class FirstMethodIndexer(val sig: MethodSig, val clazz: FirstClassIndexer, val isStatic: Boolean) : MethodVisitor(api) {

    private val methods = HashSet<MethodSig>()
    private val fieldsR = HashSet<FieldSig>()
    private val fieldsW = HashSet<FieldSig>()
    private val constructed = HashSet<String>()

    private val annotations = ArrayList<Annota>()
    private val interfaceCalls = HashSet<MethodSig>()
    // private val interfaceCalls = HashSet<Pair<String,String>>()

    var usesSelf = false

    var instructionIndex = 0

    // not static,
    // local.get 0
    // local.get 1
    // field.set
    // return nothing
    val args = split2(sig.descriptor)
    var isSetter = args.size == 2 && args.last() == "V"

    // not static,
    // local.get 0
    // field.get
    // return sth
    var isGetter = args.size == 1 && args.last() != "V"

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
        val meth = hIndex.methods.getOrPut(owner) { HashSet() }
        if (meth.add(sig1)) {// a new method
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

            val potentialTargets = ArrayList<MethodSig>()
            fun handle(interfaceI: String) {
                potentialTargets.addAll(hIndex.methods[interfaceI]!!)
                val interfaces = hIndex.interfaces[interfaceI]
                if (interfaces != null) {
                    for (it in interfaces)
                        handle(it)
                }
            }
            handle(interface1)
            if (print) println(args.joinToString())
            val targetDescriptor1 = split2((args[0] as Type).toString())
            val targetDescriptor2 = split2((args[2] as Type).toString())
            val implementationTargets = potentialTargets
                .filter {
                    if (it.name == name && it !in hIndex.staticMethods && it in hIndex.notImplementedMethods) {
                        val id = split2(it.descriptor)
                        (id == targetDescriptor1 || id == targetDescriptor2)
                    } else false
                }
                .groupBy { GenericSig(it) }
                .entries
            if (implementationTargets.size != 1) {
                println(sig)
                println("----------------------")
                println(name)
                println(descriptor)
                println(method)
                for (arg in args) {
                    println("arg: $arg")
                }
                println("----------------------")
                // java.util.stream.StreamSpliterators.IntWrappingSpliterator.initPartialTraversalState()
                for (sig in implementationTargets) {
                    printUsed(sig.value.first())
                }
                println("others:")
                for (sig in potentialTargets) {
                    if (implementationTargets.none { sig in it.value }) printUsed(sig)
                }
                throw NotImplementedError("$interface1 -> $implementationTargets, which one?")
            }
            val implementationTarget = implementationTargets.first().key

            if (print) {
                println()
                println("---- $sig")
                println("dst.class: ${dst.owner}")
                println("dst.name: ${dst.name}")
                println("dst.desc: ${dst.desc}")
                println("dst.tag: ${dst.tag}")
                println("name: $name")
                println("desc: $descriptor")
                println("target: $implementationTarget")
                println("args: " + args.joinToString())
                println("----")
                // throw IllegalStateException()
            }

            hIndex.classFlags[synthClassName] = 0
            hIndex.superClass[synthClassName] = baseClass
            hIndex.childClasses.getOrPut(baseClass) { HashSet() }.add(synthClassName)
            hIndex.childClasses.getOrPut(interface1) { HashSet() }.add(synthClassName)
            hIndex.interfaces[synthClassName] = arrayOf(interface1)
            hIndex.doneClasses.add(synthClassName)
            hIndex.syntheticClasses.add(synthClassName)

            // actual function, which is being implemented by dst
            hIndex.missingClasses.add(synthClassName)

            visitMethodInsn(0, dst.owner, dst.name, dst.desc, false)

            val bridge = MethodSig.c(synthClassName, implementationTarget.name, implementationTarget.descriptor)
            methods.add(bridge)
            hIndex.jvmImplementedMethods.add(bridge)

            clazz.dep(dst.owner)

            methods.add(calledMethod)

            val dlu = DelayedLambdaUpdate("$sig", calledMethod, descriptor, synthClassName, bridge)
            needingBridgeUpdate[synthClassName] = dlu

            dIndex.methodDependencies[bridge] = hashSetOf(calledMethod)

            constructed.add(synthClassName)

            hIndex.methods[synthClassName] = hashSetOf(bridge)

            // val linkSrcTypies = utils.genericsTypies(bridge)
            // linkGenericsByInterfaces(interface1, bridge, linkSrcTypies, synthClassName, calledMethod)

            /*if (synthClassName == "java_lang_System_getProperty_Ljava_lang_StringLjava_lang_String") {
                println(sig)
                println("synth class name: $synthClassName")
                println("name: $name")
                println("interface: $interface1")
                println("desc: $descriptor")
                println("method: $method")
                println("synth-method: $synthMethod")
                println("args: " + args.joinToString())
                // throw java.lang.IllegalStateException("if inside Configuration\$StateInit, find interfaces for inheritance!")
                println("linked $linkSrc to $calledMethod")
            }*/

            // we need a new class with three parts:
            // b) a caller, which implements the interface, and passes the args to @dst
            // c) fields to hold the arguments

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
        val type = reb(type0)
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
        }
    }

    override fun visitTryCatchBlock(start: Label?, end: Label?, handler: Label?, type: String?) {
        isGetter = false
        isSetter = false
        instructionIndex++
        if (type != null) clazz.dep(reb(type))
    }

    var lastField: FieldSig? = null

    override fun visitFieldInsn(opcode: Int, owner0: String, name: String, descriptor: String) {

        if (instructionIndex != 1 && isGetter) isGetter = false
        if (instructionIndex != 2 && isSetter) isSetter = false
        instructionIndex++

        val owner = reb(owner0)
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

        if (instructionIndex == 1) {
            // only return
            hIndex.emptyMethods.add(sig)
        }

        hIndex.usesSelf[sig] = usesSelf
    }

}