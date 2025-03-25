package translator

import dIndex
import hIndex
import hierarchy.FirstClassIndexer
import org.objectweb.asm.*
import replaceClass
import utils.MethodSig

class ClassTranslator(val clazz: String) : ClassVisitor(FirstClassIndexer.API_LEVEL) {

    init {
        if (replaceClass(clazz) != clazz) throw IllegalStateException("Forgot to resolve $clazz")
    }

    private val minVersion = 50

    private var writer: ClassWriter? = null

    override fun visit(
        version: Int, access: Int, name: String, signature: String?,
        superName: String?, interfaces: Array<out String>?
    ) {
        if (version < minVersion) {
            // we need all stack map frames
            //  -> if we are below Java 1.6 (50), generate all stack map frames
            // a "Pro Gamer Move" ^^, this has been much, much easier than expected
            val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
            writer.visit(minVersion, access, name, signature, superName, interfaces)
            this.writer = writer
        }
    }

    // todo create array of methods, and annotations for fields, methods and classes

    override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
        return writer?.visitAnnotation(descriptor, visible)
    }

    override fun visitAttribute(attribute: Attribute?) {
        writer?.visitAttribute(attribute)
    }

    override fun visitField(
        access: Int, name: String?, descriptor: String?,
        signature: String?, value: Any?
    ): FieldVisitor? {
        return writer?.visitField(access, name, descriptor, signature, value)
    }

    override fun visitMethod(
        access: Int, name: String, descriptor: String,
        signature: String?, exceptions: Array<out String>?
    ): MethodVisitor? {
        val writer = writer
        return if (writer == null) {
            val sig = MethodSig.c(clazz, name, descriptor)
            val alias = hIndex.getAlias(sig)
            if (sig !in dIndex.methodsWithForbiddenDependencies && sig in dIndex.usedMethods && alias == sig) {
                MethodTranslator(access, clazz, name, sig.descriptor)
            } else null
        } else {
            writer.visitMethod(access, name, descriptor, signature, exceptions)
        }
    }

    override fun visitEnd() {
        val writer = writer
        if (writer != null) {
            writer.visitEnd()
            throw FoundBetterReader(ClassReader(writer.toByteArray()))
        }
    }

}