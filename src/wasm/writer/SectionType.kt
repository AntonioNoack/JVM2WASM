package wasm.writer

// cannot strip elements, ordinal matters!
enum class SectionType(val name1: String = "") {
    CUSTOM,
    TYPE("Type"),
    IMPORT,
    FUNCTION,
    TABLE,
    MEMORY,
    GLOBAL,
    EXPORT,
    START,
    ELEM,
    CODE,
    DATA,
    DATA_COUNT,
    TAG,
}