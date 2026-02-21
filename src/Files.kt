import me.anno.io.files.Reference.getReference

private val jvm2wasmFile = java.io.File(".").absolutePath
val jvm2wasmFolder = getReference(jvm2wasmFile)
    .apply { check(exists) { "Missing $jvm2wasmFile..." } }

val targetsFolder = jvm2wasmFolder.getChild("targets")