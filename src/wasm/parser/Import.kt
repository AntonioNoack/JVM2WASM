package wasm.parser

import utils.Param
import wasm.instr.Instruction.Companion.emptyArrayList

class Import(funcName: String, params: List<Param>, results: List<String>) :
    FunctionImpl(funcName, params, results, emptyList(), emptyArrayList, false)