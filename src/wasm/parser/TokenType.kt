package wasm.parser

enum class TokenType(val prefix: Int) {
    OPEN_BRACKET(0),
    CLOSE_BRACKET(0),
    NAME(0),
    STRING(1),
    DOLLAR(1),
    NUMBER(0),
    COMMENT(3)
}