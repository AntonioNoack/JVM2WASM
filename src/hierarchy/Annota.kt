package hierarchy

import utils.Annotations.getAnnotaImplClass

data class Annota(val clazz: String, val properties: HashMap<String, Any?> = HashMap()) {
    val implClass get() = getAnnotaImplClass(clazz)
}