package hierarchy

import org.objectweb.asm.AnnotationVisitor

class AnnotaVisitor(api: Int, val properties: HashMap<String, Any?>) : AnnotationVisitor(api) {
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