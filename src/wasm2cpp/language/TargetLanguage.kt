package wasm2cpp.language

import wasm.parser.FunctionImpl
import wasm2cpp.FunctionWriter
import wasm2cpp.expr.Expr
import wasm2cpp.instr.*

interface TargetLanguage {

    fun appendName(name: String)

    fun appendExpr(expr: Expr)
    fun appendExprSafely(expr: Expr)

    fun writeFunctionStart(function: FunctionImpl, writer: FunctionWriter)
    fun beginDeclaration(name: String, jvmType: String)
    fun writeFunctionTypeDefinition(instr: FunctionTypeDefinition, writer: FunctionWriter)

    fun writeStaticInitCheck(writer: FunctionWriter)
    fun writeGoto(instr: GotoInstr)

    fun writeStaticInstance(className: String)
    fun writeFieldAssignment(assignment: FieldAssignment, writer: FunctionWriter)

    fun writeLoadInstr(instr: CppLoadInstr, writer: FunctionWriter)
    fun writeStoreInstr(instr: CppStoreInstr, writer: FunctionWriter)
    fun writeReturnStruct(results: List<Expr>)

}