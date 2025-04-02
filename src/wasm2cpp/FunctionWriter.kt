package wasm2cpp

import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertFail
import me.anno.utils.assertions.assertTrue
import utils.StringBuilder2
import wasm.instr.*
import wasm.instr.Instructions.Return
import wasm.instr.Instructions.Unreachable
import wasm.parser.FunctionImpl
import wasm.parser.GlobalVariable
import wasm2cpp.instr.*

// todo inherit from this class and...
//  - using HighLevel getters, setters and local-variables, pass around true structs
//  - using that, generate JavaScript
// todo new highLevel instructions for instanceOf and indirect calls

// todo intermediate stack-less representation with assignments and complex expressions

// todo enable all warnings, and clear them all for truly clean code
//  - ignore not-used outputs from functions
//  - mark functions as pure (compile-time constant)
//  - inline pure functions (incl. potential reordering) into expressions
//  - discard unused expressions

class FunctionWriter(val globals: Map<String, GlobalVariable>) {

    companion object {
        val cppKeywords = (
                "alignas,alignof,and,and_eq,asm,atomic_cancel,atomic_commit,atomic_noexcept,auto,bitand,bitor,bool,break," +
                        "case,catch,char,char8_t,char16_t,char32_t,class,compl,concept,const,consteval,constexpr,constinit," +
                        "const_cast,continue,contract_assert,co_await,co_return,co_yield,,decltype,default,delete,do," +
                        "double,dynamic_cast,else,enum,explicit,export,extern,false,float,for,friend,goto,if,inline,int," +
                        "long,mutable,namespace,new,noexcept,not,not_eq,nullptr,operator,or,or_eq,private,protected,public," +
                        "reflexpr,register,reinterpret_cast,requires,return,short,signed,sizeof,static,static_assert," +
                        "static_cast,struct,switch,synchronized,template,this,thread_local,throw,true,try,typedef," +
                        "typeid,typename,union,unsigned,using,virtual,void,volatile,wchar_t,while,xor,xor_eq"
                ).split(',').toHashSet()
    }

    private var depth = 1

    lateinit var function: FunctionImpl

    private fun init(function: FunctionImpl) {
        this.function = function
        depth = 1
    }

    fun write(function: FunctionImpl) {

        init(function)

        defineFunctionHead(function, true)
        writer.append(" {\n")

        if (function.funcName.startsWith("static_")) {
            begin().append("static bool wasCalled = false").end()
            begin().append(
                if (function.results.isEmpty()) "if(wasCalled) return"
                else "if(wasCalled) return 0"
            ).end()
            begin().append("wasCalled = true").end()
        }

        writeInstructions(function.body)
        writer.append("}\n")
    }

    private fun begin(): StringBuilder2 {
        for (i in 0 until depth) writer.append("  ")
        return writer
    }

    private fun StringBuilder2.end() {
        append(";\n")
    }

    private fun writeInstructions(instructions: List<Instruction>) {
        for (i in instructions.indices) {
            writeInstruction(instructions[i])
        }
    }

    private fun writeInstruction(i: Instruction) {
        when (i) {
            is CppLoadInstr -> {
                begin().append(i.type).append(' ').append(i.newName).append(" = ")
                    .append("((").append(i.memoryType).append("*) ((uint8_t*) memory + (u32)")
                    .append(i.addrExpr.expr).append("))[0]").end()
            }
            is CppStoreInstr -> {
                begin().append("((").append(i.memoryType).append("*) ((uint8_t*) memory + (u32)")
                    .append(i.addrExpr.expr).append("))[0] = ")
                if (i.type != i.memoryType) {
                    writer.append('(').append(i.memoryType).append(") ")
                }
                writer.append(i.valueExpr.expr).end()
            }
            is NullDeclaration -> begin().append(i.type).append(' ').append(i.name)
                .append(" = 0").end()
            is Declaration -> begin().append(i.type).append(' ').append(i.name)
                .append(" = ").append(i.initialValue.expr).end()
            is Assignment -> begin().append(i.name)
                .append(" = ").append(i.newValue.expr).end()
            is ExprReturn -> {
                val results = i.results
                assertEquals(function.results.size, results.size)
                begin().append("return")
                when (results.size) {
                    1 -> writer.append(' ').append(results.first().expr)
                    else -> {
                        writer.append(" { ")
                        for (ri in results.indices) {
                            if (ri > 0) writer.append(", ")
                            writer.append(results[ri].expr)
                        }
                        writer.append(" }")
                    }
                }
                writer.end()
            }
            Return -> {
                assertTrue(function.results.isEmpty())
                begin().append("return").end()
            }
            Unreachable -> {
                begin().append("unreachable(\"")
                    .append(function.funcName).append("\")").end()
            }
            is ExprIfBranch -> {
                begin().append("if (").append(i.expr.expr).append(") {\n")
                depth++
                writeInstructions(i.ifTrue)
                depth--
                if (i.ifFalse.isNotEmpty()) {
                    begin().append("} else {\n")
                    depth++
                    writeInstructions(i.ifFalse)
                    depth--
                }
                begin().append("}\n")
            }
            is ExprCall -> {
                begin()
                if (i.resultName != null) {
                    for (r in i.resultTypes) { // combine result types into struct name
                        writer.append(r)
                    }
                    writer.append(' ').append(i.resultName).append(" = ")
                }
                writer.append(i.funcName).append('(')
                for (param in i.params) {
                    if (!writer.endsWith("(")) writer.append(", ")
                    writer.append(param.expr)
                }
                writer.append(")").end()
            }
            is FunctionTypeDefinition -> {
                val type = i.funcType
                val tmpType = i.typeName
                val tmpVar = i.instanceName
                // using CalculateFunc = int32_t(*)(int32_t, int32_t, float);
                begin().append("using ").append(tmpType).append(" = ")
                if (type.results.isEmpty()) {
                    writer.append("void")
                } else {
                    for (ri in type.results.indices) {
                        writer.append(type.results[ri])
                    }
                }
                writer.append("(*)(")
                for (pi in type.params.indices) {
                    if (pi > 0) writer.append(", ")
                    writer.append(type.params[pi])
                }
                writer.append(")").end()
                // CalculateFunc calculateFunc = reinterpret_cast<CalculateFunc>(funcPtr);
                begin().append(tmpType).append(' ').append(tmpVar).append(" = reinterpret_cast<")
                    .append(tmpType).append(">(indirect[").append(i.indexExpr.expr).append("])").end()
            }
            is BlockInstr -> {
                // write body
                writeInstructions(i.body)
                // write label at end as jump target
                begin().append(i.label).append(":\n")
            }
            is LoopInstr -> {
                begin().append(i.label).append(": while (true) {\n")
                depth++
                writeInstructions(i.body)
                depth--
                begin().append("}\n")
            }
            is GotoInstr -> begin().append("goto ").append(i.label).end()
            is BreakInstr -> begin().append("break").end()
            is SwitchCase -> writeSwitchCase(i)
            is Comment -> begin().append("// ").append(i.name).append('\n')
            else -> assertFail("Unknown instruction type ${i.javaClass}")
        }
    }

    private fun writeSwitchCase(switchCase: SwitchCase) {
        val cases = switchCase.cases
        for (j in cases.indices) {
            begin().append("case").append(j).append(": {\n")
            depth++
            writeInstructions(cases[j])
            depth--
            begin().append("}\n")
        }
    }
}
