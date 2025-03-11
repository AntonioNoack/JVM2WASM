package interpreter

import me.anno.utils.pooling.Stack

class StackFrame {
    var stackStart = 0
    val locals = HashMap<String, Number>()

    companion object {
        val pool = Stack(StackFrame::class)
    }
}