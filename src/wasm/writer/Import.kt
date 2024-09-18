package wasm.writer

open class Import(
    val kind: ExternalKind,
    val moduleName: String,
    val fieldName: String
)