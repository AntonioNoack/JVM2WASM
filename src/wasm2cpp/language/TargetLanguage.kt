package wasm2cpp.language

import utils.FieldSig
import wasm.parser.FunctionImpl
import wasm2cpp.FunctionWriter
import wasm2cpp.expr.Expr
import wasm2cpp.instr.FieldAssignment
import wasm2cpp.instr.FunctionTypeDefinition
import wasm2cpp.instr.GotoInstr

interface TargetLanguage {
    fun appendExpr(expr: Expr)
    fun appendExprSafely(expr: Expr)

    fun defineFunctionHead(function: FunctionImpl, writer: FunctionWriter)
    fun beginDeclaration(name: String, jvmType: String)
    fun writeFunctionTypeDefinition(instr: FunctionTypeDefinition, writer: FunctionWriter)

    fun writeStaticInitCheck(writer: FunctionWriter)
    fun writeGoto(instr: GotoInstr)

    fun writeStaticInstance(field: FieldSig)
    fun writeFieldAssignment(assignment: FieldAssignment, writer: FunctionWriter)

}