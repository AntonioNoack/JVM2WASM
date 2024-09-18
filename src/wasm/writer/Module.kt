package wasm.writer

class Module(
    val types: List<Type>,
    val imports: List<Import>,
    val functions: List<Function>,
    val tables: List<Table>,
    val memories: List<Memory>,
    val tags: List<Tag>,
    val globals: List<Global>,
    val exports: List<Export>,
    val starts: List<Start>
)