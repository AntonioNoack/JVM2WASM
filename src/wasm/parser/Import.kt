package wasm.parser

import utils.Param

class Import(funcName: String, params: List<Param>, results: List<String>) :
    FunctionImpl(funcName, params, results, emptyList(), emptyList(), false)