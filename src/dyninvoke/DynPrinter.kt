package dyninvoke

import org.objectweb.asm.Handle

object DynPrinter {
    fun print(
        name: String,
        descriptor: String,
        method: Handle,
        args: Array<out Any>
    ) {
        println("Invoke Dynamic")
        println("  name: $name") // pseudo-method name of the original Interface-function
        println("  desc: $descriptor") // descriptor that is being called
        println("  method-handle:")
        println("    owner: ${method.owner}")
        println("    name: ${method.name}")
        println("    tag: ${method.tag}")
        println("    desc: ${method.desc}")
        println("  args:")
        for (i in args) {
            if (i is Array<*>) {
                println("    $i [${i.javaClass}]:")
                for (j in i) {
                    println("      $j [${j?.javaClass}]")
                }
            } else {
                println("    $i [${i.javaClass}]")
            }
        }
    }
}