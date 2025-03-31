package wasm.writer

import wasm.parser.DataSection
import wasm.parser.FunctionImpl

class Module(
    val types: List<Type>,
    val imports: List<Import>,
    val functions: List<Function>,
    val tables: List<Table>,
    val memories: List<Memory>,
    val tags: List<Tag>,
    val globals: List<Global>,
    val exports: List<Export>,
    val starts: List<Start>,
    val elemSegments: List<ElemSegment>,
    val code: List<FunctionImpl>,
    val dataSections: List<DataSection>
)