package wasm.parser

import me.anno.io.json.generic.JsonReader.Companion.toHex
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.arrays.ByteArrayList
import me.anno.utils.types.Strings.indexOf2
import wasm.instr.*
import wasm.instr.SimpleInstr.Companion.simpleInstructions
import kotlin.math.min

class WATParser {

    var memorySizeInBlocks = -1
    val imports = ArrayList<Import>()
    val dataSections = ArrayList<DataSection>()
    val functionTable = ArrayList<String>()
    val functions = ArrayList<FunctionImpl>()
    val types = HashMap<String, FunctionType>()
    val globals = HashMap<String, GlobalVariable>()

    private val params = ArrayList<String>()
    private val results = ArrayList<String>()

    fun parse(text: String) {
        val tokens = parseTokens(text)
        // parse it
        val endI = parseBlock(tokens, 0)
        assertEquals(tokens.size, endI)
    }

    private fun parseTokens(text: String): TokenList {
        val list = TokenList(text)
        var i = 0
        while (i < text.length) {
            when (text[i++]) {
                '(' -> list.push(TokenType.OPEN_BRACKET, i, i + 1)
                ')' -> list.push(TokenType.CLOSE_BRACKET, i, i + 1)
                ' ', '\n' -> {}
                in 'a'..'z' -> {
                    val i0 = i - 1
                    while (i < text.length) {
                        when (text[i]) {
                            in 'a'..'z', in '0'..'9', in "._" -> i++
                            else -> break
                        }
                    }
                    list.push(TokenType.NAME, i0, i)
                }
                '"' -> {
                    val i0 = i
                    while (i < text.length) {
                        when (text[i++]) {
                            '"' -> break
                            '\\' -> i++
                            else -> {}
                        }
                    }
                    list.push(TokenType.STRING, i0, i - 1)
                }
                '$' -> {
                    val i0 = i
                    while (i < text.length) {
                        when (text[i]) {
                            in 'A'..'Z', in 'a'..'z', in '0'..'9', '_' -> i++
                            else -> break
                        }
                    }
                    list.push(TokenType.DOLLAR, i0, i)
                }
                in '0'..'9', '-' -> {
                    val i0 = i - 1
                    while (i < text.length) {
                        when (text[i]) {
                            in '0'..'9', '.', 'E', '-' -> i++
                            else -> break
                        }
                    }
                    list.push(TokenType.NUMBER, i0, i)
                }
                ';' -> {
                    // comment
                    assertEquals(text[i].code, ';'.code)
                    i = text.indexOf2('\n', i) + 1
                }
                else -> throw NotImplementedError(
                    text[i - 1].toString() + " from " + text.substring(i - 1, min(i + 100, text.length))
                )
            }
        }
        return list
    }

    fun parseBlock(list: TokenList, i0: Int): Int {
        assertEquals(TokenType.OPEN_BRACKET, list.getType(i0))
        var i = i0 + 1
        while (true) {
            when (val type = list.getType(i)) {
                TokenType.OPEN_BRACKET -> i = parseBlock(list, i)
                TokenType.CLOSE_BRACKET -> {
                    // println("returning from parseBlock @${getStackDepth()}")
                    return i + 1
                }
                TokenType.NAME -> {
                    when (val name = list.getString(i++)) {
                        "module" -> {}
                        "memory" -> {
                            i = skipBlock(list, i)
                            assertEquals(TokenType.NUMBER, list.getType(i))
                            memorySizeInBlocks = list.getString(i).toInt()
                            i++
                        }
                        "import" -> {
                            // (import "jvm" "fcmpl" (func $fcmpl (param f32 f32) (result i32)))
                            list.consume(TokenType.STRING, "jvm", i++)
                            val funcName = list.consume(TokenType.STRING, i++)
                            list.consume(TokenType.OPEN_BRACKET, i++)
                            list.consume(TokenType.NAME, "func", i++)
                            list.consume(TokenType.DOLLAR, funcName, i++) // repeated name
                            if (list.getType(i) == TokenType.OPEN_BRACKET) i =
                                parseBlock(list, i) // params are optional
                            if (list.getType(i) == TokenType.OPEN_BRACKET) i = parseBlock(list, i)
                            imports.add(Import(funcName, ArrayList(params), ArrayList(results)))
                            params.clear()
                            results.clear()
                            list.consume(TokenType.CLOSE_BRACKET, i++)
                        }
                        "param" -> {
                            while (list.getType(i) == TokenType.NAME) {
                                params.add(list.getString(i++))
                            }
                        }
                        "result" -> {
                            while (list.getType(i) == TokenType.NAME) {
                                results.add(list.getString(i++))
                            }
                        }
                        "data" -> {
                            // (data (i32.const 64) "\00\00\00\00\00\00\00\0
                            list.consume(TokenType.OPEN_BRACKET, i++)
                            list.consume(TokenType.NAME, "i32.const", i++)
                            val startIndex = list.consume(TokenType.NUMBER, i++).toInt()
                            list.consume(TokenType.CLOSE_BRACKET, i++)
                            assertEquals(TokenType.STRING, list.getType(i))
                            var r0 = list.getStart(i)
                            val r1 = list.getEnd(i++)
                            val content = ByteArrayList(r1 - r0)
                            while (r0 < r1) {
                                when (val c = list.text[r0++]) {
                                    '\\' -> {
                                        val c0 = list.text[r0++]
                                        val c1 = list.text[r0++]
                                        assertTrue(c0 in '0'..'9' || c0 in 'a'..'f')
                                        content.add(toHex(c0, c1).toByte())
                                    }
                                    else -> content.add(c.code.toByte())
                                }
                            }
                            dataSections.add(DataSection(startIndex, content.toByteArray()))
                        }
                        "table" -> {
                            // ???
                            // (table 8282 funcref)
                            i += 2
                        }
                        "elem" -> {
                            // (elem (i32.const 0) and then a list of function references
                            i = skipBlock(list, i)
                            while (list.getType(i) == TokenType.DOLLAR) {
                                functionTable.add(list.getString(i++))
                            }
                        }
                        "type" -> {
                            // (type $f00000000RV0 (func (param i32 i32 i32 i32 i32 i32 i32 i32 i32) (result i32)))
                            val typeName = list.consume(TokenType.DOLLAR, i++)
                            list.consume(TokenType.OPEN_BRACKET, i++)
                            list.consume(TokenType.NAME, "func", i++)
                            if (list.getType(i) == TokenType.OPEN_BRACKET) i =
                                parseBlock(list, i) // params are optional
                            if (list.getType(i) == TokenType.OPEN_BRACKET) i = parseBlock(list, i)
                            list.consume(TokenType.CLOSE_BRACKET, i++)
                            types[typeName] = FunctionType(ArrayList(params), ArrayList(results))
                            params.clear()
                            results.clear()
                        }
                        "func" -> {
                            // (func $dupi32 (export "dupi32") (param i32) (result i32 i32) local.get 0 local.get 0)
                            val funcName = list.consume(TokenType.DOLLAR, i++)
                            val locals = ArrayList<LocalVariable>()
                            var isExported = false
                            while (list.getType(i) == TokenType.OPEN_BRACKET) {
                                i++ // skip (
                                when (list.consume(TokenType.NAME, i++)) {
                                    "export" -> {
                                        isExported = true
                                        list.consume(TokenType.STRING, funcName, i++)
                                    }
                                    "param" -> {
                                        while (list.getType(i) != TokenType.CLOSE_BRACKET) {
                                            params.add(list.consume(TokenType.NAME, i++))
                                        }
                                    }
                                    "result" -> {
                                        while (list.getType(i) != TokenType.CLOSE_BRACKET) {
                                            results.add(list.consume(TokenType.NAME, i++))
                                        }
                                    }
                                    "local" -> {
                                        // (local $addr i32)
                                        val localName = list.consume(TokenType.DOLLAR, i++)
                                        val localType = list.consume(TokenType.NAME, i++)
                                        locals.add(LocalVariable(localName, localType))
                                    }
                                    else -> {
                                        // some instruction
                                        i -= 2
                                        break
                                    }
                                }
                                assertEquals(TokenType.CLOSE_BRACKET, list.getType(i++))
                            }
                            val (j, content) = parseFunctionBlock(list, i)
                            // println("Function content '$funcName': ${content.toList()}")
                            functions.add(
                                FunctionImpl(
                                    funcName, ArrayList(params), ArrayList(results),
                                    locals, content, isExported
                                )
                            )
                            params.clear()
                            results.clear()
                            i = j - 1
                            list.consume(TokenType.CLOSE_BRACKET, i) // end of function
                        }
                        "global" -> {
                            // (global $S i32 (i32.const 33875))
                            // (global $Q (mut i32) (i32.const 2798408))
                            val globalName = list.consume(TokenType.DOLLAR, i++)
                            val isMutable = list.getType(i) == TokenType.OPEN_BRACKET
                            if (isMutable) {
                                i++ // (
                                list.consume(TokenType.NAME, "mut", i++)
                            }
                            list.consume(TokenType.NAME, "i32", i++)
                            if (isMutable) list.consume(TokenType.CLOSE_BRACKET, i++)
                            list.consume(TokenType.OPEN_BRACKET, i++)
                            list.consume(TokenType.NAME, "i32.const", i++)
                            val initialValue = list.consume(TokenType.NUMBER, i++).toInt()
                            globals[globalName] = GlobalVariable("global_$globalName", "i32", initialValue, isMutable)
                            list.consume(TokenType.CLOSE_BRACKET, i++)
                        }
                        else -> throw NotImplementedError(
                            "Unknown name: $name at " + list.subList(i, min(i + 20, list.size))
                        )
                    }
                }
                else -> throw NotImplementedError("Unknown type: $type at " + list.subList(i, min(i + 20, list.size)))
            }
        }
    }

    private fun skipBlock(list: TokenList, i0: Int): Int {
        assertEquals(TokenType.OPEN_BRACKET, list.getType(i0))
        var i = i0 + 1
        while (true) {
            when (list.getType(i)) {
                TokenType.OPEN_BRACKET -> i = skipBlock(list, i)
                TokenType.CLOSE_BRACKET -> return i + 1
                else -> i++
            }
        }
    }

    private fun parseFunctionBlock(list: TokenList, i0: Int): FunctionBlock {
        val result = ArrayList<Instruction>()
        var i = i0
        while (true) {
            when (list.getType(i)) {
                TokenType.NAME -> {
                    when (val instrName = list.getString(i++)) {
                        "local.get" -> {
                            when (list.getType(i)) {
                                TokenType.NUMBER -> result.add(ParamGet(list.consume(TokenType.NUMBER, i++).toInt()))
                                TokenType.DOLLAR -> result.add(LocalGet(list.getString(i++)))
                                else -> throw NotImplementedError()
                            }
                        }
                        "local.set" -> {
                            when (list.getType(i)) {
                                TokenType.NUMBER -> result.add(ParamSet(list.consume(TokenType.NUMBER, i++).toInt()))
                                TokenType.DOLLAR -> result.add(LocalSet(list.getString(i++)))
                                else -> throw NotImplementedError()
                            }
                        }
                        "global.get" -> result.add(GlobalGet(list.consume(TokenType.DOLLAR, i++)))
                        "global.set" -> result.add(GlobalSet(list.consume(TokenType.DOLLAR, i++)))
                        "i32.const" -> result.add(Const("i32", list.consume(TokenType.NUMBER, i++)))
                        "i64.const" -> result.add(Const("i64", list.consume(TokenType.NUMBER, i++)))
                        "f32.const" -> result.add(Const("f32", list.consume(TokenType.NUMBER, i++)))
                        "f64.const" -> result.add(Const("f64", list.consume(TokenType.NUMBER, i++)))
                        "call" -> result.add(Call(list.consume(TokenType.DOLLAR, i++)))
                        "call_indirect" -> {
                            // call_indirect (type $fR00)
                            list.consume(TokenType.OPEN_BRACKET, i++)
                            list.consume(TokenType.NAME, "type", i++)
                            val type = list.consume(TokenType.DOLLAR, i++)
                            list.consume(TokenType.CLOSE_BRACKET, i++)
                            result.add(CallIndirect(type))
                        }
                        "if" -> {
                            // (if (then ...) (else ...))
                            val params = ArrayList<String>()
                            val results = ArrayList<String>()
                            list.consume(TokenType.OPEN_BRACKET, i++)
                            if (list.getString(i) == "param") {
                                i++
                                while (list.getType(i) == TokenType.NAME) {
                                    params.add(list.getString(i++))
                                }
                                list.consume(TokenType.CLOSE_BRACKET, i++)
                                list.consume(TokenType.OPEN_BRACKET, i++)
                            }
                            if (list.getString(i) == "result") {
                                i++
                                while (list.getType(i) == TokenType.NAME) {
                                    results.add(list.getString(i++))
                                }
                                list.consume(TokenType.CLOSE_BRACKET, i++)
                                list.consume(TokenType.OPEN_BRACKET, i++)
                            }
                            list.consume(TokenType.NAME, "then", i++)
                            val (j, ifTrue) = parseFunctionBlock(list, i)
                            i = j
                            val ifFalse = if (list.getType(i) == TokenType.OPEN_BRACKET) {
                                i++
                                list.consume(TokenType.NAME, "else", i++)
                                val (k, ifFalse) = parseFunctionBlock(list, i)
                                i = k
                                ifFalse
                            } else emptyList()
                            result.add(IfBranch(ifTrue, ifFalse, params, results))
                        }
                        "loop" -> {
                            // (loop $b1886 (result)
                            val label = list.consume(TokenType.DOLLAR, i++)
                            val results = ArrayList<String>()
                            if (list.getType(i) == TokenType.OPEN_BRACKET &&
                                list.getType(i + 1) == TokenType.NAME &&
                                list.getString(i + 1) == "result"
                            ) {
                                i += 2 // skip open-bracket and "result"
                                while (list.getType(i) == TokenType.NAME) {
                                    results.add(list.getString(i++))
                                }
                                list.consume(TokenType.CLOSE_BRACKET, i++)
                            }
                            val (j, body) = parseFunctionBlock(list, i)
                            result.add(LoopInstr(label, body, results))
                            i = j - 1
                        }
                        "br" -> {
                            val label = list.consume(TokenType.DOLLAR, i++)
                            result.add(Jump(label))
                        }
                        "br_if" -> {
                            val label = list.consume(TokenType.DOLLAR, i++)
                            result.add(JumpIf(label))
                        }
                        "block" -> {
                            // (block (block (block (block (block (block (block (block (block
                            //  (block local.get $lbl (br_table 0 1 2 3 4 5 6 7 8))
                            var depth = 0
                            while (list.getType(i) == TokenType.OPEN_BRACKET) {
                                i++
                                list.consume(TokenType.NAME, "block", i++)
                                depth++
                            }
                            list.consume(TokenType.NAME, "local.get", i++)
                            list.consume(TokenType.DOLLAR, "lbl", i++)
                            list.consume(TokenType.OPEN_BRACKET, i++)
                            list.consume(TokenType.NAME, "br_table", i++)
                            for (j in 0 until depth) {
                                list.consume(TokenType.NUMBER, "$j", i++)
                            }
                            list.consume(TokenType.CLOSE_BRACKET, i++)
                            val cases = ArrayList<List<Instruction>>()
                            for (j in 0 until depth) {
                                val (k, instructions) = parseFunctionBlock(list, i)
                                cases.add(instructions)
                                i = k
                            }
                            result.add(SwitchCase(cases))
                        }
                        else -> {
                            val simple = simpleInstructions[instrName]
                            if (simple != null) {
                                result.add(simple)
                            } else throw NotImplementedError(instrName)
                        }
                    }
                }
                TokenType.OPEN_BRACKET -> {
                    val (j, instructions) = parseFunctionBlock(list, i + 1)
                    result.addAll(instructions)
                    i = j
                }
                TokenType.CLOSE_BRACKET -> {
                    // println("returning from ${getStackDepth()}: $result")
                    return FunctionBlock(i + 1, result)
                }
                else -> throw IllegalStateException("Unexpected ${list.getType(i)} in function block")
            }
        }
    }

}
