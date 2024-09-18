package wasm.instr

class ShiftInstr(name: String) : SimpleInstr(name) {
    val type = name.substring(0, 3)
    val isRight get() = name[6] == 'r'
    val isU get() = name[8] == 'u'
}