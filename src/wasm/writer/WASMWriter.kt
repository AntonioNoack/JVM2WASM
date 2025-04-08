package wasm.writer

import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertNull
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.arrays.ByteArrayList
import translator.JavaTypes.convertTypeToWASM
import utils.WASMType
import utils.WASMTypes.*
import wasm.instr.*
import wasm.instr.Const.Companion.f32Const
import wasm.instr.Const.Companion.f64Const
import wasm.instr.Const.Companion.i32Const
import wasm.instr.Const.Companion.i64Const
import wasm.parser.DataSection
import wasm.parser.FunctionImpl
import wasm.parser.GlobalVariable
import wasm.parser.Import

object WASMWriter {

    private fun FunctionImpl.toFuncType(): FuncType {
        return FuncType(params.map { it.wasmType }, results.map { convertTypeToWASM(it) })
    }

    private lateinit var functionNameToIndex: Map<String, Int>

    private val typeToIndex = HashMap<FuncType, Int>(64)
    private val typesList = ArrayList<FuncType>()

    private lateinit var globalToIndex: Map<String, Int>

    private fun getTypeIndex(funcType: FuncType): Int {
        return typeToIndex.getOrPut(funcType) {
            typesList.add(funcType)
            typeToIndex.size
        }
    }

    private val branchDepth = HashMap<String, Int>()
    private fun insertIndicesAndDepths(instr: Instruction, depth: Int) {
        when (instr) {
            is Call -> instr.index = functionNameToIndex[instr.name]
                ?: throw IllegalStateException("Missing index for ${instr.name}")
            is CallIndirect -> instr.typeIndex = getTypeIndex(instr.type)
            is LoopInstr -> {
                instr.typeIndex = getTypeIndex(FuncType(instr.params, instr.results, Unit))
                assertNull(branchDepth.put(instr.label, depth))
                insertIndicesAndDepths(instr.body, depth + 1)
                assertEquals(depth, branchDepth.remove(instr.label))
            }
            is IfBranch -> {
                instr.typeIndex = getTypeIndex(FuncType(instr.params, instr.results, Unit))
                val nextDepth = depth + 1
                insertIndicesAndDepths(instr.ifTrue, nextDepth)
                insertIndicesAndDepths(instr.ifFalse, nextDepth)
            }
            // depth is relative, and -1
            is Jump -> instr.depth = depth - branchDepth[instr.label]!! - 1
            is JumpIf -> instr.depth = depth - branchDepth[instr.label]!! - 1
            is GlobalGet -> instr.index = globalToIndex[instr.name]!!
            is GlobalSet -> instr.index = globalToIndex[instr.name]!!
        }
    }

    private fun insertIndicesAndDepths(instructions: List<Instruction>, depth: Int) {
        for (i in instructions.indices) {
            insertIndicesAndDepths(instructions[i], depth)
        }
    }

    fun writeWASM(parser: wasm.parser.Module): ByteArrayList {
        return writeWASM(
            parser.functions, parser.functionTable, parser.globals,
            parser.imports, parser.memorySizeInBlocks, parser.dataSections
        )
    }

    fun writeWASM(
        functions: List<FunctionImpl>,
        functionTable: List<String>,
        globals: Map<String, GlobalVariable>,
        imports: List<Import>,
        memorySizeInBlocks: Int,
        dataSections: List<DataSection>
    ): ByteArrayList {

        // write it as binary WASM
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
                memorySizeInBlocks.toLong(), 0L
            )
        )
        val table = Table(
            Type(TypeKind.FUNC_REF),
            Limits(false, false, false, functionTable.size.toLong(), 0L)
        )

        // imports come first
        for (i in imports.indices) {
            imports[i].index = i
        }
        for (i in functions.indices) {
            functions[i].index = i + imports.size
        }

        functionNameToIndex =
            imports.associate { import ->
                import.funcName to import.index
            } + functions.associate { func ->
                func.funcName to func.index
            }

        val globalsList = globals.values.toList()
        globalToIndex = globalsList.withIndex().associate {
            it.value.name to it.index
        }

        for (i in functions.indices) {
            insertIndicesAndDepths(functions[i].body, 0)
        }

        val module = Module(
            types = typeToIndex.entries
                .sortedBy { it.value }.map { it.key }
                .map { func ->
                    FuncTypeI(func.toString(), func.params.map { param ->
                        typeToType[param.wasmName]!!
                    }, func.results.map { result ->
                        typeToType[result.wasmName]!!
                    })
                },
            imports = listOf(MemoryImport("js", "mem", memory)) +
                    imports.map {
                        // (import "jvm" "java_io_BufferedInputStream_close_V" (func $java_io_BufferedInputStream_close_V (param i32) (result i32)))
                        FuncImport("jvm", it.funcName, getTypeIndex(it.toFuncType()))
                    },
            functions = (imports + functions).map { func ->
                Function(typeToIndex[FuncType(func.params.map { it.jvmType }, func.results, Unit)]!!)
            },
            tables = listOf(table),
            memories = listOf(memory),
            tags = emptyList(), // tags???
            globals = globalsList.mapIndexed { i, global ->
                assertEquals(i, globalToIndex[global.name])
                val constInstr = when (global.wasmType) {
                    WASMType.I32 -> i32Const(global.initialValue.toInt())
                    WASMType.I64 -> i64Const(global.initialValue.toLong())
                    WASMType.F32 -> f32Const(global.initialValue.toFloat())
                    WASMType.F64 -> f64Const(global.initialValue.toDouble())
                }
                Global(
                    global.name,
                    listOf(constInstr),
                    typeToType[global.wasmType.wasmName]!!,
                    global.isMutable
                )
            },
            exports = functions
                .filter { it.isExported }
                .map { func ->
                    assertTrue(func.index >= 0)
                    Export(ExternalKind.FUNC, func.funcName, func.index)
                },
            starts = emptyList(), // starts???
            elemSegments = listOf(
                ElemSegment(
                    0, 0, 0,
                    functionTable.map { name ->
                        val index = functionNameToIndex[name]
                            ?: throw IllegalStateException("Missing $name in functionTable")
                        assertTrue(index >= 0)
                        index
                    }
                )
            ),
            code = functions,
            dataSections = dataSections
        )

        val stream = ByteArrayList(1024)
        BinaryWriter(stream, module).write()
        return stream
    }

}