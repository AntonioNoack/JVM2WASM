package wasm.writer

class ElemSegment(
    val index: Int,
    val tableIndex: Int,
    val flags: Int,
    val functionTable: List<Int>
)