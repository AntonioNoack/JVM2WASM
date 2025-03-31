package wasm

import me.anno.utils.Clock
import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertNull
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.arrays.ByteArrayList
import utils.WASMTypes.*
import utils.wasmFolder
import utils.wasmTextFile
import wasm.instr.*
import wasm.instr.Const.Companion.i32Const
import wasm.parser.FunctionImpl
import wasm.parser.GlobalVariable
import wasm.parser.WATParser
import wasm.writer.*
import wasm.writer.FuncType
import wasm.writer.Function

fun FunctionImpl.toFuncType(): wasm.instr.FuncType {
    return wasm.instr.FuncType(params.map { it.wasmType }, results)
}

lateinit var functionNameToIndex: Map<String, Int>

val typeToIndex = HashMap<wasm.instr.FuncType, Int>(64)
val typesList = ArrayList<wasm.instr.FuncType>()

lateinit var globalToIndex: Map<String, GlobalVariable>

fun getTypeIndex(funcType: wasm.instr.FuncType): Int {
    return typeToIndex.getOrPut(funcType) {
        typesList.add(funcType)
        typeToIndex.size
    }
}

val branchDepth = HashMap<String, Int>()
fun insertIndicesAndDepths(instr: Instruction, depth: Int) {
    when (instr) {
        is Call -> instr.index = functionNameToIndex[instr.name]
            ?: throw IllegalStateException("Missing index for ${instr.name}")
        is CallIndirect -> instr.typeIndex = getTypeIndex(instr.type)
        is LoopInstr -> {
            instr.typeIndex = getTypeIndex(wasm.instr.FuncType(instr.params, instr.results))
            assertNull(branchDepth.put(instr.label, depth))
            insertIndicesAndDepths(instr.body, depth + 1)
            assertEquals(depth, branchDepth.remove(instr.label))
        }
        is IfBranch -> {
            instr.typeIndex = getTypeIndex(wasm.instr.FuncType(instr.params, instr.results))
            // todo do we need to increase the depth???
            val inc = 0
            insertIndicesAndDepths(instr.ifTrue, depth + inc)
            insertIndicesAndDepths(instr.ifFalse, depth + inc)
        }
        // depth is relative, and -1
        is Jump -> instr.depth = depth - branchDepth[instr.label]!! - 1
        is JumpIf -> instr.depth = depth - branchDepth[instr.label]!! - 1
        is GlobalGet -> instr.index = globalToIndex[instr.name]!!.index
        is GlobalSet -> instr.index = globalToIndex[instr.name]!!.index
    }
}

fun insertIndicesAndDepths(instructions: List<Instruction>, depth: Int) {
    for (i in instructions.indices) {
        insertIndicesAndDepths(instructions[i], depth)
    }
}

// this isn't working yet, and not fully implemented
fun main() {
    // load wasm.wat file
    val clock = Clock("WASMWriter")
    val text = wasmTextFile.readTextSync()
    clock.stop("Read WAT")
    // tokenize it
    val parser = WATParser()
    parser.parse(text)
    clock.stop("Parsing")

    val functions = parser.functions
    val imports = parser.imports
    for (i in functions.indices) {
        getTypeIndex(functions[i].toFuncType())
    }
    for (i in imports.indices) {
        getTypeIndex(imports[i].toFuncType())
    }

    val typeToType = mapOf(
        i32 to Type(TypeKind.I32),
        i64 to Type(TypeKind.I64),
        f32 to Type(TypeKind.F32),
        f64 to Type(TypeKind.F64),
    )

    val memory = Memory(
        Limits(
            false, false, false,
            parser.memorySizeInBlocks.toLong(), 0L
        )
    )
    val table = Table(
        Type(TypeKind.FUNC_REF),
        Limits(false, false, false, parser.functionTable.size.toLong(), 0L)
    )
    // imports come first
    for (i in parser.imports.indices) {
        parser.imports[i].index = i
    }
    for (i in parser.functions.indices) {
        parser.functions[i].index = i + parser.imports.size
    }

    functionNameToIndex =
        parser.imports.associate { import ->
            import.funcName to import.index
        } + parser.functions.associate { func ->
            func.funcName to func.index
        }

    globalToIndex = parser.globals
    for ((idx, global) in parser.globals.values.withIndex()) {
        global.index = idx
    }

    for (i in parser.functions.indices) {
        insertIndicesAndDepths(parser.functions[i].body, 0)
    }

    val module = Module(
        types = typeToIndex.entries
            .sortedBy { it.value }.map { it.key }
            .map { func ->
                FuncType(Sig(func.params.map { param ->
                    typeToType[param]!!
                }, func.results.map { result ->
                    typeToType[result]!!
                }))
            },
        imports = listOf(MemoryImport("js", "mem", memory)) +
                parser.imports.map {
                    // todo what is declIndex???
                    // (import "jvm" "java_io_BufferedInputStream_close_V" (func $java_io_BufferedInputStream_close_V (param i32) (result i32)))
                    FuncImport("jvm", it.funcName, 0)
                },
        functions = (parser.imports + parser.functions).map { func ->
            Function(typeToIndex[wasm.instr.FuncType(func.params.map { it.wasmType }, func.results)]!!)
        },
        tables = listOf(table),
        memories = listOf(memory),
        tags = emptyList(), // tags???
        globals = globalToIndex.entries
            .sortedBy { it.key }.map { it.value }
            .map { global ->
                assertEquals(i32, global.wasmType)
                Global(listOf(i32Const(global.initialValue)), typeToType[global.wasmType]!!, global.isMutable)
            },
        exports = parser.functions
            .filter { it.isExported }
            .map { func ->
                assertTrue(func.index >= 0)
                Export(ExternalKind.FUNC, func.funcName, func.index)
            },
        starts = emptyList(), // starts???
        elemSegments = listOf(
            ElemSegment(
                0, 0, 0,
                parser.functionTable.map { name ->
                    val index = functionNameToIndex[name]
                        ?: throw IllegalStateException("Missing $name in functionTable")
                    assertTrue(index >= 0)
                    index
                }
            )
        ),
        code = parser.functions
    )

    val stream = ByteArrayList(1024)
    val writer = BinaryWriter(stream, module)
    try {
        writer.write()
    } catch (e: Throwable) {
        e.printStackTrace()
        for (i in 0 until 10_000) {
            stream.add(0)
        }
        writer.endSection()
    }

    clock.stop("Writing")

    wasmFolder.getChild("jvm2wasm.test.wasm")
        .writeBytes(stream.values, 0, stream.size)

    clock.total("Done :)")

}