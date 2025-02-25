package wasm.parser

class Import(funcName: String, params: List<String>, results: List<String>) :
    FunctionImpl(funcName, params, results, emptyList(), emptyList(), false)