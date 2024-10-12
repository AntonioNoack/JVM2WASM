package wasm.writer

enum class ExternalKind {
    FUNC, // function index
    TABLE, // table index
    MEMORY, // memory index
    GLOBAL, // global index
    TAG // not in the docs??
}