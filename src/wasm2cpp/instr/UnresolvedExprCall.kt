package wasm2cpp.instr

import utils.MethodSig
import wasm2cpp.StackElement
import wasm2js.shortName

open class UnresolvedExprCall(
    val self: StackElement?,
    val sig: MethodSig,
    val isSpecial: Boolean,
    params: List<StackElement>,
) : ExprCall(shortName(sig), params)