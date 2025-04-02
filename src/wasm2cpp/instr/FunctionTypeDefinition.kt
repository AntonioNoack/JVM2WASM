package wasm2cpp.instr

import wasm.instr.FuncType
import wasm2cpp.StackElement

/**
 * using CalculateFunc = int32_t(*)(int32_t, int32_t, float);
 * CalculateFunc calculateFunc = reinterpret_cast<CalculateFunc>(funcPtr);
 * */
class FunctionTypeDefinition(
    val funcType: FuncType,
    val typeName: String,
    val instanceName: String,
    val indexExpr: StackElement
) : CppInstruction