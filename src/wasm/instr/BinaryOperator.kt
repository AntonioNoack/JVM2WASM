package wasm.instr

enum class BinaryOperator(val symbol: String) {
    // traditional math
    ADD("+"), SUB("-"),
    AND("&"), OR("|"), XOR("^"),
    MULTIPLY("*"), DIVIDE("/"), REMAINDER("%"),

    // binary operations
    ROTATE_LEFT("rotl"), ROTATE_RIGHT("rotr"),
    SHIFT_LEFT("shl"), SHIFT_RIGHT_SIGNED("shrs"), SHIFT_RIGHT_UNSIGNED("shru"),

    // idk what to call them
    MIN("min"), MAX("max"),

    // comparisons
    LESS("<"),
    LESS_EQUAL("<="),
    GREATER(">"),
    GREATER_EQUAL(">="),
    EQUAL("=="),
    NOT_EQUAL("!="),
}