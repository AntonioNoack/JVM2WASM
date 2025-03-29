package hierarchy

import hIndex
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.FieldVisitor
import utils.Descriptor
import utils.FieldSig

class FirstFieldVisitor(api: Int, private val fieldSig: FieldSig) : FieldVisitor(api) {
    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
        val annotationClass = Descriptor.parseType(descriptor)
        val annota = hIndex.addAnnotation(fieldSig, Annota(annotationClass))
        FirstClassIndexer.dep(annotationClass, fieldSig.clazz)
        return AnnotaVisitor(api, annota.properties)
    }
}