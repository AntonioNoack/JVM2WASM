package utils

// todo use org.objectweb.asm.Opcodes instead
object CommonInstructions {

    const val INVOKE_VIRTUAL = 0xb6
    const val INVOKE_INTERFACE = 0xb9

    const val NEW_INSTR = 0xbb
    const val NEW_ARRAY_INSTR = 0xbc
    const val ANEW_ARRAY_INSTR = 0xbd
    const val ARRAY_LENGTH_INSTR = 0xbe
    const val ATHROW_INSTR = 0xbf

    const val DUP_INSTR = 0x59

    const val MONITOR_ENTER = 0xc2
    const val MONITOR_EXIT = 0xc3
}