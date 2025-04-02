package wasm.parser

import wasm.instr.FuncType

open class Module {
    var memorySizeInBlocks = -1
    val imports = ArrayList<Import>()
    val dataSections = ArrayList<DataSection>()
    val functionTable = ArrayList<String>()
    val functions = ArrayList<FunctionImpl>()
    val types = HashMap<String, FuncType>()
    val globals = HashMap<String, GlobalVariable>()
}