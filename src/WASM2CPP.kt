import me.anno.io.json.generic.JsonReader.Companion.toHex
import me.anno.utils.Clock
import me.anno.utils.OS.documents
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.arrays.ByteArrayList
import me.anno.utils.structures.arrays.IntArrayList
import me.anno.utils.types.Booleans.toInt
import me.anno.utils.types.Strings.indexOf2
import sun.reflect.generics.reflectiveObjects.NotImplementedException
import utils.StringBuilder2
import kotlin.math.min

enum class TokenType {
    OPEN_BRACKET,
    CLOSE_BRACKET,
    NAME,
    STRING,
    DOLLAR,
    NUMBER
}

class Token(val type: TokenType, val value: String) {
    companion object {
        val open = Token(TokenType.OPEN_BRACKET, "(")
        val close = Token(TokenType.CLOSE_BRACKET, ")")
    }

    override fun toString(): String {
        return value
    }
}

class TokenList(val text: String) {

    val types = ByteArrayList(256)
    val starts = IntArrayList(256)
    val ends = IntArrayList(256)

    val size get() = types.size

    fun push(type: TokenType, startIncl: Int, endExcl: Int) {
        types.add(type.ordinal.toByte())
        starts.add(startIncl)
        ends.add(endExcl)
    }

    fun getType(i: Int): TokenType {
        return TokenType.entries[types[i].toInt()]
    }

    fun getStart(i: Int): Int {
        return starts[i]
    }

    fun getEnd(i: Int): Int {
        return ends[i]
    }

    fun getString(i: Int): String {
        return text.substring(getStart(i), getEnd(i))
    }

    fun getToken(i: Int): Token {
        return when (val type = getType(i)) {
            TokenType.OPEN_BRACKET -> Token.open
            TokenType.CLOSE_BRACKET -> Token.close
            else -> Token(type, getString(i))
        }
    }

    fun subList(startIncl: Int, endExcl: Int): TokenSubList {
        return TokenSubList(this, startIncl, endExcl)
    }

    fun toList(): List<Token> {
        return (0 until types.size).map {
            getToken(it)
        }
    }

    fun consume(type: TokenType, name: String, i: Int) {
        assertEquals(type, getType(i))
        assertEquals(name, getString(i))
    }

    fun consume(type: TokenType, i: Int): String {
        assertEquals(type, getType(i))
        return getString(i)
    }

    override fun toString(): String {
        return toList().toString()
    }

}

class TokenSubList(val list: TokenList, val startIncl: Int, val endExcl: Int) {
    fun toList(): List<Token> {
        return (startIncl until endExcl).map { list.getToken(it) }
    }

    override fun toString(): String {
        return toList().toString()
    }
}

fun parseTokens(text: String): TokenList {
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

fun skipBlock(list: TokenList, i0: Int): Int {
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

data class FunctionType(val params: List<String>, val results: List<String>)
data class Import(val funcName: String, val params: List<String>, val results: List<String>)
data class DataSection(val startIndex: Int, val content: ByteArray)
data class FunctionImpl(
    val funcName: String, val params: List<String>, val results: List<String>,
    val locals: List<LocalVariable>,
    val body: List<Instruction>
)

var memorySizeInBlocks = -1
val params = ArrayList<String>()
val results = ArrayList<String>()
val imports = ArrayList<Import>()
val dataSections = ArrayList<DataSection>()
val functionTable = ArrayList<String>()
val functions = ArrayList<FunctionImpl>()
val functionsByName = HashMap<String, FunctionImpl>()
val types = HashMap<String, FunctionType>()
val globals = HashMap<String, GlobalVariable>()

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
                        if (list.getType(i) == TokenType.OPEN_BRACKET) i = parseBlock(list, i) // params are optional
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
                        if (list.getType(i) == TokenType.OPEN_BRACKET) i = parseBlock(list, i) // params are optional
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
                        while (list.getType(i) == TokenType.OPEN_BRACKET) {
                            i++ // skip (
                            when (list.consume(TokenType.NAME, i++)) {
                                "export" -> list.consume(TokenType.STRING, funcName, i++)
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
                        functions.add(FunctionImpl(funcName, ArrayList(params), ArrayList(results), locals, content))
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
                        globals[globalName] = GlobalVariable("global_$globalName", "i32", initialValue)
                        list.consume(TokenType.CLOSE_BRACKET, i++)
                    }
                    else -> throw NotImplementedError(
                        "Unknown name: $name at " + list.subList(
                            i,
                            min(i + 20, list.size)
                        )
                    )
                }
            }
            else -> throw NotImplementedError("Unknown type: $type at " + list.subList(i, min(i + 20, list.size)))
        }
    }
}

class GlobalVariable(val name: String, val type: String, val initialValue: Int)
class LocalVariable(val name: String, val type: String)

data class FunctionBlock(val i: Int, val instructions: List<Instruction>)

class BlockInstruction(val instructions: List<Instruction>) : Instruction

class ParamGet(val index: Int) : Instruction {
    val name = "p$index"
    override fun toString(): String = "local.get $index"
}

class LocalGet(val name: String) : Instruction {
    override fun toString(): String = "local.get $name"
}

class ParamSet(val index: Int) : Instruction {
    val name = "p$index"
    override fun toString(): String = "local.set $index"
}

class LocalSet(val name: String) : Instruction {
    override fun toString(): String = "local.set $name"
}

class GlobalGet(val name: String) : Instruction {
    override fun toString(): String = "global.get $name"
}

class GlobalSet(val name: String) : Instruction {
    override fun toString(): String = "global.set $name"
}

class Const(val type: String, val value: String) : Instruction {
    override fun toString(): String = "$type.const $value"
}

class Call(val name: String) : Instruction {
    override fun toString(): String = "call $name"
}

class CallIndirect(val type: String) : Instruction {
    override fun toString(): String = "call_indirect $type"
}

class SwitchCase(val cases: List<List<Instruction>>) : Instruction {
    override fun toString(): String = "switchCase[${cases.size}]"
}

class IfBranch(
    val ifTrue: List<Instruction>, val ifFalse: List<Instruction>,
    val params: List<String>, val results: List<String>
) : Instruction

class LoopInstr(val label: String, val body: List<Instruction>, val results: List<String>) : Instruction

class Jump(val label: String) : Instruction {
    override fun toString(): String {
        return "br $label"
    }
}

class JumpIf(val label: String) : Instruction {
    override fun toString(): String {
        return "br_if $label"
    }
}

val simpleInstructions = HashMap<String, SimpleInstr>()

open class SimpleInstr(val name: String) : Instruction {
    init {
        simpleInstructions[name] = this
    }

    override fun toString(): String {
        return name
    }
}

class UnaryInstruction(name: String, val call: String) : SimpleInstr(name) {
    val type = name.substring(0, 3)
}

class UnaryInstruction2(name: String, val prefix: String, val suffix: String, val popType: String) : SimpleInstr(name) {
    val pushType = name.substring(0, 3)
}

class BinaryInstruction(name: String, val operator: String) : SimpleInstr(name) {
    val type = name.substring(0, 3)
}

class CompareInstr(name: String, val operator: String, val castType: String? = null) : SimpleInstr(name) {
    val type = name.substring(0, 3)
}

val I32Add = BinaryInstruction("i32.add", "+")
val I32Sub = BinaryInstruction("i32.sub", "-")
val I32Mul = BinaryInstruction("i32.mul", "*")

val I64Add = BinaryInstruction("i64.add", "+")
val I64Sub = BinaryInstruction("i64.sub", "-")
val I64Mul = BinaryInstruction("i64.mul", "*")

val F32Add = BinaryInstruction("f32.add", "+")
val F32Sub = BinaryInstruction("f32.sub", "-")
val F32Mul = BinaryInstruction("f32.mul", "*")
val F32Div = BinaryInstruction("f32.div", "/")

val F64Add = BinaryInstruction("f64.add", "+")
val F64Sub = BinaryInstruction("f64.sub", "-")
val F64Mul = BinaryInstruction("f64.mul", "*")
val F64Div = BinaryInstruction("f64.div", "/")

val I32Store8 = SimpleInstr("i32.store8")
val I32Store16 = SimpleInstr("i32.store16")
val I32Store = SimpleInstr("i32.store")
val I64Store = SimpleInstr("i64.store")
val F32Store = SimpleInstr("f32.store")
val F64Store = SimpleInstr("f64.store")

val I32Load8U = SimpleInstr("i32.load8_u")
val I32Load8S = SimpleInstr("i32.load8_s")
val I32Load16U = SimpleInstr("i32.load16_u")
val I32Load16S = SimpleInstr("i32.load16_s")
val I32Load = SimpleInstr("i32.load")
val I64Load = SimpleInstr("i64.load")
val F32Load = SimpleInstr("f32.load")
val F64Load = SimpleInstr("f64.load")

val I32_DIVS = SimpleInstr("i32.div_s")
val I64_DIVS = SimpleInstr("i64.div_s")

val F32Trunc = UnaryInstruction("f32.trunc", "std::trunc")
val F64Trunc = UnaryInstruction("f64.trunc", "std::trunc")

val F32GE = CompareInstr("f32.ge", ">=")
val F32GT = CompareInstr("f32.gt", ">")
val F32LE = CompareInstr("f32.le", "<=")
val F32LT = CompareInstr("f32.lt", "<")
val F32NE = CompareInstr("f32.ne", "!=")
val F32EQ = CompareInstr("f32.eq", "==")

val F64GE = CompareInstr("f64.ge", ">=")
val F64GT = CompareInstr("f64.gt", ">")
val F64LE = CompareInstr("f64.le", "<=")
val F64LT = CompareInstr("f64.lt", "<")
val F64NE = CompareInstr("f64.ne", "!=")
val F64EQ = CompareInstr("f64.eq", "==")

val I32_TRUNC_F32S = UnaryInstruction2("i32.trunc_f32_s", "static_cast<i32>(std::trunc(", "))", "f32")
val I32_TRUNC_F64S = UnaryInstruction2("i32.trunc_f64_s", "static_cast<i32>(std::trunc(", "))", "f64")
val I64_TRUNC_F32S = UnaryInstruction2("i64.trunc_f32_s", "static_cast<i64>(std::trunc(", "))", "f32")
val I64_TRUNC_F64S = UnaryInstruction2("i64.trunc_f64_s", "static_cast<i64>(std::trunc(", "))", "f64")
val F64_PROMOTE_F32 = UnaryInstruction2("f64.promote_f32", "static_cast<f64>(", ")", "f32")
val F32_DEMOTE_F64 = UnaryInstruction2("f32.demote_f64", "static_cast<f32>(", ")", "f64")
val I64_EXTEND_I32S = SimpleInstr("i64.extend_i32_s")
val I32_WRAP_I64 = SimpleInstr("i32.wrap_i64")
val F64_CONVERT_I32S = SimpleInstr("f64.convert_i32_s")
val F64_CONVERT_I64S = SimpleInstr("f64.convert_i64_s")
val F32_CONVERT_I32S = SimpleInstr("f32.convert_i32_s")
val F32_CONVERT_I64S = SimpleInstr("f32.convert_i64_s")
val I64_REINTERPRET_F64 = SimpleInstr("i64.reinterpret_f64")
val F64_REINTERPRET_I64 = SimpleInstr("f64.reinterpret_i64")
val I32_REINTERPRET_F32 = SimpleInstr("i32.reinterpret_f32")
val F32_REINTERPRET_I32 = SimpleInstr("f32.reinterpret_i32")

val I32GES = CompareInstr("i32.ge_s", ">=")
val I32GTS = CompareInstr("i32.gt_s", ">")
val I32LES = CompareInstr("i32.le_s", "<=")
val I32LTS = CompareInstr("i32.lt_s", "<")

val I32GEU = CompareInstr("i32.ge_u", ">=", "u32")
val I32GTU = CompareInstr("i32.gt_u", ">", "u32")
val I32LEU = CompareInstr("i32.le_u", "<=", "u32")
val I32LTU = CompareInstr("i32.lt_u", "<", "u32")

val I64GES = CompareInstr("i64.ge_s", ">=")
val I64GTS = CompareInstr("i64.gt_s", ">")
val I64LES = CompareInstr("i64.le_s", "<=")
val I64LTS = CompareInstr("i64.lt_s", "<")

val I32EQZ = SimpleInstr("i32.eqz")
val I64EQZ = SimpleInstr("i64.eqz")

val I32EQ = CompareInstr("i32.eq", "==")
val I32NE = CompareInstr("i32.ne", "!=")
val I64EQ = CompareInstr("i64.eq", "==")
val I64NE = CompareInstr("i64.ne", "!=")

val I32_REM_S = SimpleInstr("i32.rem_s")
val I64_REM_S = SimpleInstr("i64.rem_s")

val Return = SimpleInstr("return")
val Unreachable = SimpleInstr("unreachable")

val I32Shl = BinaryInstruction("i32.shl", "<<")
val I32ShrU = SimpleInstr("i32.shr_u")
val I32ShrS = SimpleInstr("i32.shr_s")
val I64Shl = SimpleInstr("i64.shl")
val I64ShrU = SimpleInstr("i64.shr_u")
val I64ShrS = SimpleInstr("i64.shr_s")

val I32And = BinaryInstruction("i32.and", "&")
val I64And = BinaryInstruction("i64.and", "&")
val I32Or = BinaryInstruction("i32.or", "|")
val I64Or = BinaryInstruction("i64.or", "|")
val I32XOr = BinaryInstruction("i32.xor", "^")
val I64XOr = BinaryInstruction("i64.xor", "^")

val F32_ABS = UnaryInstruction("f32.abs", "std::abs")
val F64_ABS = UnaryInstruction("f64.abs", "std::abs")
val F32_NEG = UnaryInstruction("f32.neg", "-")
val F64_NEG = UnaryInstruction("f64.neg", "-")
val F32_MIN = SimpleInstr("f32.min")
val F64_MIN = SimpleInstr("f64.min")
val F32_MAX = SimpleInstr("f32.max")
val F64_MAX = SimpleInstr("f64.max")
val F32_SQRT = UnaryInstruction("f32.sqrt", "std::sqrt")
val F64_SQRT = UnaryInstruction("f64.sqrt", "std::sqrt")
val F32_FLOOR = UnaryInstruction("f32.floor", "std::floor")
val F64_FLOOR = UnaryInstruction("f64.floor", "std::floor")
val F32_CEIL = UnaryInstruction("f32.ceil", "std::ceil")
val F64_CEIL = UnaryInstruction("f64.ceil", "std::ceil")
val F32_NEAREST = UnaryInstruction("f32.nearest", "std::round")
val F64_NEAREST = UnaryInstruction("f64.nearest", "std::round")

val I32_ROTL = SimpleInstr("i32.rotl")
val I64_ROTL = SimpleInstr("i64.rotl")

val I32_ROTR = SimpleInstr("i32.rotr")
val I64_ROTR = SimpleInstr("i64.rotr")

val Drop = SimpleInstr("drop")

fun getStackDepth(): Int {
    return RuntimeException().stackTrace.size
}

fun parseFunctionBlock(list: TokenList, i0: Int): FunctionBlock {
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
            else -> throw NotImplementedError()
        }
    }
}

interface Instruction

val writer = StringBuilder2(1024)
val tmp = documents.getChild("IdeaProjects/JVM2WASM/tmp")

fun defineTypes() {
    writer.append("// types\n")
    writer.append("#include <cstdint>\n")
    val map = listOf(
        "i32" to "int32_t",
        "i64" to "int64_t",
        "u32" to "uint32_t",
        "u64" to "uint64_t",
        "f32" to "float",
        "f64" to "double"
    )
    for ((wasm, cpp) in map) {
        writer.append("typedef ").append(cpp).append(' ').append(wasm).append(";\n")
    }
    writer.append("\n")
}

fun defineReturnStructs() {
    writer.append("// return-structs\n")
    for (typeList in functions
        .map { it.results }.filter { it.size > 1 }
        .toHashSet().sortedBy { it.size }) {
        // define return struct
        val name = typeList.joinToString("")
        writer.append("struct ").append(name).append(" {")
        for (ki in typeList.indices) {
            val ni = typeList[ki]
            writer.append(" ").append(ni)
                .append(' ').append("v").append(ki).append(";")
        }
        writer.append(" };\n")
    }
    writer.append("\n")
}

fun defineFunctionHeads() {
    writer.append("// function heads\n")
    for (function in functions.sortedBy {
        it.results.size.toString() + it.funcName
    }) {
        defineFunctionHead(function, false)
        writer.append(";\n")
    }
    writer.append('\n')
}

fun defineImports() {
    writer.append("// imports\n")
    for (import in imports.sortedBy {
        it.results.size.toString() + it.funcName
    }) {
        val func = FunctionImpl(import.funcName, import.params, import.results, emptyList(), emptyList())
        defineFunctionHead(func, false)
        writer.append(";\n")
        functionsByName[import.funcName] = func
    }
    writer.append('\n')
}

fun defineFunctionHead(function: FunctionImpl, parameterNames: Boolean) {
    defineFunctionHead(function.funcName, function.params, function.results, parameterNames)
}

fun defineFunctionHead(funcName: String, params: List<String>, results: List<String>, parameterNames: Boolean) {
    if (results.isEmpty()) {
        writer.append("void")
    } else {
        for (ri in results) {
            writer.append(ri)
        }
    }
    writer.append(' ').append(funcName).append('(')
    for (i in params.indices) {
        val pi = params[i]
        if (i > 0) writer.append(", ")
        writer.append(pi)
        if (parameterNames) {
            writer.append(" p").append(i)
        }
    }
    writer.append(")")
}

fun defineFunctionImplementations() {
    writer.append("// implementations\n")
    writer.append("#include <cmath> // trunc, ...\n")
    for (fi in functions.indices) {
        defineFunctionImplementation(functions[fi])
    }
    writer.append('\n')
}

data class StackElement(val type: String, val name: String)

fun defineFunctionImplementation(function: FunctionImpl) {
    defineFunctionHead(function, true)
    writer.append(" {\n")

    var depth = 1
    val localsByName = function.locals
        .associateBy { it.name }

    fun begin(): StringBuilder2 {
        for (i in 0 until depth) {
            writer.append("  ")
        }
        return writer
    }

    fun StringBuilder2.end() {
        append(";\n")
    }

    val stack = ArrayList<StackElement>()
    var genI = 0
    fun gen(): String = "tmp${genI++}"
    fun pop(type: String): String {
        val i0 = stack.removeLast()
        println("pop -> $i0 + $stack")
        assertEquals(type, i0.type)
        return i0.name
    }

    fun push(type: String, name: String = gen()): String {
        stack.add(StackElement(type, name))
        println("push -> $stack")
        return name
    }

    fun beginNew(type: String): StringBuilder2 {
        return begin().append(type).append(' ').append(push(type)).append(" = ")
    }

    fun beginSetEnd(name: String, type: String) {
        begin().append(name).append(" = ").append(pop(type)).end()
    }

    fun shift(type: String): Int {
        return when (type) {
            "i32", "f32" -> 2
            "i64", "f64" -> 3
            else -> throw NotImplementedError()
        }
    }

    fun load(type: String, memoryType: String = type) {
        val ptr = pop("i32")
        beginNew(type).append("((").append(memoryType).append("*) memory)[(u32) ").append(ptr)
            .append(" >> ").append(shift(type)).append("]").end()
    }

    fun store(type: String, memoryType: String = type) {
        val value = pop(type)
        val ptr = pop("i32")
        begin().append("((").append(memoryType).append("*) memory)[(u32) ").append(ptr)
            .append(" >> ").append(shift(type)).append("] = ").append(value).append(";\n")
    }

    fun writeInstruction(i: Instruction) {
        when (i) {
            is ParamGet -> {
                val index = i.index
                val type = function.params[index]
                beginNew(type).append(i.name).end()
            }
            is ParamSet -> {
                val index = i.index
                val type = function.params[index]
                beginSetEnd(i.name, type)
            }
            is GlobalGet -> {
                val global = globals[i.name]!!
                beginNew(global.type).append(global.name).end()
            }
            is GlobalSet -> {
                val global = globals[i.name]!!
                beginSetEnd(global.name, global.type)
            }
            is LocalGet -> {
                val local = localsByName[i.name]!!
                beginNew(local.type).append(local.name).end()
            }
            is LocalSet -> {
                val local = localsByName[i.name]!!
                beginSetEnd(local.name, local.type)
            }
            // loading
            I32Load8S -> load("i32", "int8_t")
            I32Load8U -> load("i32", "uint8_t")
            I32Load16S -> load("i32", "int16_t")
            I32Load16U -> load("i32", "uint16_t")
            I32Load -> load("i32")
            I64Load -> load("i64")
            F32Load -> load("f32")
            F64Load -> load("f64")
            // storing
            I32Store8 -> store("i32", "int8_t")
            I32Store16 -> store("i32", "int16_t")
            I32Store -> store("i32")
            I64Store -> store("i64")
            F32Store -> store("f32")
            F64Store -> store("f64")
            // other operations
            I32EQZ -> beginNew("i32").append(pop("i32")).append(" == 0 ? 1 : 0").end()
            I64EQZ -> beginNew("i32").append(pop("i64")).append(" == 0 ? 1 : 0").end()
            I32ShrU -> {
                val i0 = pop("i32")
                val i1 = pop("i32")
                beginNew("i32").append("(u32) ").append(i1).append(" >> ").append(i0).end()
            }
            I32ShrS -> {
                val i0 = pop("i32")
                val i1 = pop("i32")
                beginNew("i32").append(i1).append(" >> ").append(i0).end()
            }
            Return -> {
                val offset = stack.size - function.results.size
                assertTrue(offset >= 0)
                begin().append("return")
                when (stack.size) {
                    0 -> {}
                    1 -> writer.append(' ').append(stack[offset].name)
                    else -> {
                        writer.append(" { ")
                        for (ri in function.results.indices) {
                            if (ri > 0) writer.append(", ")
                            writer.append(stack[ri + offset].name)
                        }
                        writer.append(" }")
                    }
                }
                writer.end()
            }
            Unreachable -> {
                if (function.results.isEmpty()) {
                    begin().append("return /* unreachable */").end()
                } else {
                    begin().append("return { /* unreachable */ }").end()
                }
            }
            is Const -> push(i.type, i.value)
            is UnaryInstruction -> {
                val i0 = pop(i.type)
                beginNew(i.type).append(i.call).append('(').append(i0).append(')').end()
            }
            is UnaryInstruction2 -> {
                val i0 = pop(i.popType)
                beginNew(i.pushType).append(i.prefix).append(i0).append(i.suffix).end()
            }
            is BinaryInstruction -> {
                val i0 = pop(i.type)
                val i1 = pop(i.type)
                beginNew(i.type).append(i1).append(' ')
                    .append(i.operator)
                    .append(' ').append(i0).end()
            }
            is CompareInstr -> {
                val i0 = pop(i.type)
                val i1 = pop(i.type)
                beginNew("i32")
                if (i.castType != null) writer.append("(").append(i.castType).append(") ")
                writer.append(i1).append(' ').append(i.operator).append(' ')
                if (i.castType != null) writer.append("(").append(i.castType).append(") ")
                writer.append(i0).append(" ? 1 : 0").end()
            }
            is IfBranch -> {

                // get running parameters...
                val baseSize = stack.size - i.params.size - 1
                for (j in i.params) {
                    beginNew(j).append(pop(j)).end()
                }

                if (i.ifFalse.isEmpty()) {
                    assertEquals(i.params.size, i.results.size)
                }

                val resultVars = i.results.map { gen() }
                for (ri in resultVars.indices) {
                    begin().append(i.results[ri]).append(' ')
                        .append(resultVars[ri]).append(" = 0").end()
                }

                begin().append("if (").append(pop("i32")).append(") {\n")
                val stackSave = ArrayList(stack)
                stack.subList(0, baseSize).clear()

                // write stuff
                depth++
                for (instr in i.ifTrue) {
                    writeInstruction(instr)
                }

                // todo pack results into our stack somehow...
                if (i.results.isNotEmpty()) throw NotImplementedException()
                depth--

                // rescue stack
                stack.clear()
                stack.addAll(stackSave)

                if (i.results.isEmpty() && i.ifFalse.isEmpty()) {
                    begin().append("}\n")
                } else {
                    begin().append("} else {\n")

                    // write stuff
                    depth++
                    for (instr in i.ifFalse) {
                        writeInstruction(instr)
                    }

                    // todo pack results into our stack somehow...
                    if (i.results.isNotEmpty()) throw NotImplementedException()
                    depth--

                    stack.clear()
                    stack.addAll(stackSave)
                    begin().append("}\n")
                }
            }
            is Call -> {

                val func = functionsByName[i.name]
                    ?: throw IllegalStateException("Missing ${i.name}")

                val tmp = if (func.results.isNotEmpty()) gen() else ""
                when (func.results.size) {
                    0 -> begin()
                    1 -> begin().append(func.results[0]).append(' ').append(tmp).append(" = ")
                    else -> begin().append("auto ").append(tmp).append(" = ")
                }

                writer.append(func.funcName).append('(')
                for (pi in func.params.indices.reversed()) {
                    if (!writer.endsWith("(")) writer.append(", ")
                    val param = func.params[pi]
                    writer.append(pop(param))
                }
                writer.append(')').end()

                when (func.results.size) {
                    0 -> {}
                    1 -> push(func.results[0], tmp)
                    else -> {
                        for (j in func.results.indices) {
                            beginNew(func.results[j]).append(tmp).append(".v").append(j).end()
                        }
                    }
                }
            }
            is CallIndirect -> {
                val type = types[i.type]!!
                TODO("read class structure, and decide based on that")
                TODO("for now, index into jump table, and call based on that?")
            }
            is LoopInstr -> {
                assertTrue(i.results.isEmpty())
                // todo check if the label is used
                begin().append(i.label).append(": while (true) {\n")
                val stackSave = ArrayList(stack)
                stack.clear()
                depth++
                val lastIsContinue = (i.body.lastOrNull() as? Jump)?.label == i.label
                for (ii in 0 until i.body.size - lastIsContinue.toInt()) {
                    writeInstruction(i.body[ii])
                }
                if (!lastIsContinue) {
                    begin().append("break;\n")
                }
                depth--
                stack.clear()
                stack.addAll(stackSave)
                begin().append("}\n")
            }
            is Jump -> {
                begin().append("goto ").append(i.label).append(";\n")
            }
            is SwitchCase -> {
                // big monster, only 1 per function allowed, afaik
                // assertEquals(1, depth)
                begin().append("{\n")
                depth++
                assertEquals(0, i.cases[0].size)
                for (j in 1 until i.cases.size) {
                    depth -= 2
                    begin().append("l").append(j).append(":\n")
                    depth += 2
                    val instructions = i.cases[j]
                    assertTrue(instructions.size >= 2)
                    val realLast = instructions.last()
                    assertTrue(realLast is Jump) // for while(true)-branch
                    val last = instructions[instructions.size - 2]
                    assertTrue(last is LocalSet && last.name == "lbl")
                    val preLast = instructions[instructions.size - 3]
                    assertTrue(preLast is IfBranch || (preLast is Const && preLast.type == "i32"))
                    // find end:
                    //   - i32.const 2 local.set $lbl
                    //   - (if (result i32) (then i32.const 4) (else i32.const 7)) local.set $lbl
                    for (k in 0 until instructions.size - 3) {
                        writeInstruction(instructions[k])
                    }
                    if (preLast is IfBranch) {
                        assertEquals(1, preLast.ifTrue.size)
                        assertEquals(1, preLast.ifFalse.size)
                        begin().append("if (").append(pop("i32")).append(") {\n")
                        depth++
                        begin().append("goto l").append((preLast.ifTrue[0] as Const).value).end()
                        depth--
                        begin().append("} else {\n")
                        depth++
                        begin().append("goto l").append((preLast.ifFalse[0] as Const).value).end()
                        depth--
                        begin().append("}\n")
                    } else {
                        preLast as Const
                        begin().append("goto l").append(preLast.value).end()
                    }

                }
                depth--
                begin().append("}\n")
            }
            else -> throw NotImplementedError(i.toString())
        }
    }
    for (local in function.locals) {
        begin().append(local.type).append(' ').append(local.name).append(" = 0").end()
    }
    for (instr in function.body) {
        writeInstruction(instr)
    }
    when (function.body.lastOrNull()) {
        Return, Unreachable -> {}
        else -> writeInstruction(Return)
    }
    writer.append("}\n")
}

fun defineGlobals() {
    writer.append("// globals\n")
    for ((_, global) in globals.entries.sortedBy { it.key }) {
        writer.append(global.type).append(' ').append(global.name)
            .append(" = ").append(global.initialValue).append(";\n")
    }
    writer.append("\n")
}

fun main() {
    val clock = Clock("WASM2CPP")
    // load wasm.wat file
    val text = tmp.getChild("jvm2wasm.wat").readTextSync()
    // tokenize it
    val tokens = parseTokens(text)
    // parse it
    val endI = parseBlock(tokens, 0)
    assertEquals(tokens.size, endI)
    clock.stop("Parsing")

    for (func in functions) {
        functionsByName[func.funcName] = func
    }

    // todo produce a compilable .cpp from it
    writer.append("// header\n")
    writer.append("void* memory = nullptr;\n")
    defineTypes()
    defineGlobals()
    defineReturnStructs()
    defineImports()
    defineFunctionHeads()

    try {
        defineFunctionImplementations()
    } catch (e: Throwable) {
        e.printStackTrace()
    }

    tmp.getChild("jvm2wasm.cpp")
        .writeBytes(writer.values, 0, writer.size)

    // todo run it in a helper Java environment for libraries???
}