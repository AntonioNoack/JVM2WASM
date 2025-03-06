package hierarchy

import me.anno.utils.assertions.assertTrue

data class GenericSig(val name: String, val superClass: String) {
    init {
        assertTrue(!name.endsWith(";;"), name)
    }
}
