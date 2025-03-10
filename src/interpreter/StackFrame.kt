package interpreter

class StackFrame {
    var stackStart = 0
    val locals = HashMap<String, Number>()
}