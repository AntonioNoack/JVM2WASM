package wasm.writer

// cannot strip elements, ordinal matters!
enum class SectionType {
    CUSTOM,
    TYPE,
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