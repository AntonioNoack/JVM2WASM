package wasm.instr

interface BreakableInstruction {
    val label: String
    val params: List<String>
    val results: List<String>

    companion object {
        val tmp = object : BreakableInstruction {
            override val params: List<String>
                get() = emptyList()
            override val results: List<String>
                get() = emptyList()
            override val label: String
                get() = "???"
        }
    }
}