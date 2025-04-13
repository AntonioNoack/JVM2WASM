package wasm2cpp.language

import wasm.parser.FunctionImpl
import wasm2cpp.FunctionWriter
import wasm2cpp.expr.Expr
import wasm2cpp.instr.FunctionTypeDefinition

interface TargetLanguage {
    fun appendExpr(expr: Expr)
    fun appendExprSafely(expr: Expr)

    fun defineFunctionHead(function: FunctionImpl, needsParameterNames: Boolean)
    fun beginDeclaration(name: String, type: String)
    fun writeFunctionTypeDefinition(instr: FunctionTypeDefinition, writer: FunctionWriter)

}