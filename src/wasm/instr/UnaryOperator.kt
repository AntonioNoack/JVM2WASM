package wasm.instr

enum class UnaryOperator(val symbol: String) {
    NEGATE("-"),

    SQRT("sqrt"),
    ABSOLUTE("abs"),
    FLOOR("floor"),
    CEIL("ceil"),
    ROUND("round"),

    EQUALS_ZERO("== 0"),
    TRUNCATE("trunc"),

    ANY_CAST(""),
    ANY_LOAD("")
}