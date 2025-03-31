package wasm.writer

import me.anno.utils.assertions.assertEquals
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.arrays.ByteArrayList
import me.anno.utils.types.Booleans.hasFlag
import me.anno.utils.types.Booleans.toInt
import org.apache.logging.log4j.LogManager
import utils.WASMTypes.*
import wasm.instr.*
import wasm.instr.Const.Companion.i32Const0
import wasm.parser.FunctionImpl
import wasm.parser.LocalVariable

/**
 * Valid source code: https://github.com/WebAssembly/wabt/blob/main/src/binary-writer.cc
 *
 * */
class BinaryWriter(
    val stream: ByteArrayList,
    val module: Module,
) {

    companion object {
        private val LOGGER = LogManager.getLogger(BinaryWriter::class)
        val kInvalidIndex = -1
    }

    private fun ByteArrayList.write(v: Int) {
        add(v.toByte())
    }

    private fun ByteArrayList.write(v: ByteArray) {
        addAll(v, 0, v.size)
    }

    private fun ByteArrayList.writeLE32(v: Int) {
        ensureExtra(4)
        addUnsafe(v.toByte())
        addUnsafe((v ushr 8).toByte())
        addUnsafe((v ushr 16).toByte())
        addUnsafe((v ushr 24).toByte())
    }

    private fun ByteArrayList.writeLE64(v: Long) {
        ensureExtra(8)
        addUnsafe(v.toByte())
        addUnsafe((v ushr 8).toByte())
        addUnsafe((v ushr 16).toByte())
        addUnsafe((v ushr 24).toByte())
        addUnsafe((v ushr 32).toByte())
        addUnsafe((v ushr 40).toByte())
        addUnsafe((v ushr 48).toByte())
        addUnsafe((v ushr 56).toByte())
    }

    private val BINARY_MAGIC = 0x6d736100 // \0asm
    private val BINARY_VERSION = 1

    var lastSectionPayloadOffset = 0
    val MAX_U32_LEB128_BYTES = 5

    fun writeSectionHeader(type: SectionType) {
        stream.write(type.ordinal)
        lastSectionOffset = writeU32Leb128Space()
        lastSectionPayloadOffset = stream.size
    }

    fun writeU32Leb128Space(): Int {
        val startAddress = stream.size
        for (i in 0 until MAX_U32_LEB128_BYTES) {
            stream.add(0)
        }
        return startAddress
    }

    private fun writeU32Leb128(v: Int) {
        stream.ensureExtra(MAX_U32_LEB128_BYTES)
        var x = v
        do {
            stream.addUnsafe(x.toByte())
            x = x ushr 7
        } while (x != 0)
    }

    private fun writeU64Leb128(v: Long) {
        println("U --- writing $v (0x${v.toString(16)}) @${stream.size.toString(16)}")
        stream.ensureExtra(MAX_U32_LEB128_BYTES)
        var x = v
        do {
            stream.addUnsafe(x.toByte())
            x = x ushr 7
        } while (x != 0L)
    }

    private fun writeFixupU32Leb128Size(offset: Int) {
        val size = stream.size - offset - MAX_U32_LEB128_BYTES
        assertTrue(size > 0)
        writeFixedU32Leb128At(offset, size)
    }

    private fun writeFixedU32Leb128At(offset: Int, x: Int) {
        stream.ensureCapacity(offset + 5)
        stream[offset] = (x or 0x80).toByte()
        stream[offset + 1] = ((x ushr 7) or 0x80).toByte()
        stream[offset + 2] = ((x ushr 14) or 0x80).toByte()
        stream[offset + 3] = ((x ushr 21) or 0x80).toByte()
        stream[offset + 4] = (x ushr 28).and(0x7f).toByte()
        assertEquals(5, MAX_U32_LEB128_BYTES)
    }

    fun beginKnownSection(type: SectionType) {
        writeSectionHeader(type)
    }

    fun beginSection(type: SectionType, numElements: Int) {
        LOGGER.info("Writing section $type, x$numElements @${stream.size}")
        beginKnownSection(type)
        writeS32Leb128(numElements)
    }

    var lastSectionOffset = 0

    fun endSection() {
        writeFixupU32Leb128Size(lastSectionOffset)
    }

    fun writeStr(name: String) {
        val bytes = name.toByteArray()
        writeU32Leb128(bytes.size)
        stream.write(bytes)
    }

    fun writeS32Leb128(v: Int) {
        writeS64Leb128(v.toLong())
    }

    fun writeS64Leb128(v: Long) {
        var value = v
        if (v < 0) {
            while (true) {
                val byte = value.and(0x7f)
                value = value shr 7
                if (value == -1L && byte.hasFlag(0x40)) {
                    stream.add(byte.toByte())
                    break
                } else {
                    stream.add((byte or 0x80).toByte())
                }
            }
        } else {
            while (true) {
                val byte = value.and(0x7f)
                value = value shr 7
                if (value == 0L && !byte.hasFlag(0x40)) {
                    stream.add(byte.toByte())
                    break
                } else {
                    stream.add((byte or 0x80).toByte())
                }
            }
        }
    }

    fun writeType(type: TypeKind) {
        writeS32Leb128(type.id)
    }

    fun writeType(type: Type) {
        writeType(type.kind)
        if (type.kind == TypeKind.REFERENCE) {
            writeS32Leb128(type.referenceIndex)
        }
    }

    val BINARY_LIMITS_HAS_MAX_FLAG = 1
    val BINARY_LIMITS_IS_SHARED_FLAG = 2
    val BINARY_LIMITS_IS_64_FLAG = 4

    fun writeLimits(limits: Limits) {
        var flags = 0
        if (limits.hasMax) flags = flags or BINARY_LIMITS_HAS_MAX_FLAG
        if (limits.isShared) flags = flags or BINARY_LIMITS_IS_SHARED_FLAG
        if (limits.is64Bit) flags = flags or BINARY_LIMITS_IS_64_FLAG
        writeS32Leb128(flags)
        if (limits.is64Bit) {
            writeU64Leb128(limits.initial)
            if (limits.hasMax) writeU64Leb128(limits.max)
        } else {
            writeU32Leb128(limits.initial.toInt())
            if (limits.hasMax) writeU32Leb128(limits.max.toInt())
        }
    }

    fun writeMemory(memory: Memory) {
        writeLimits(memory.pageLimits)
    }

    fun writeTable(table: Table) {
        writeType(table.elemType)
        writeLimits(table.elemLimits)
    }

    fun writeGlobalHeader(global: Global) {
        writeType(global.type)
        stream.write(if (global.mutable) 1 else 0)
    }

    fun writeBlockDecl(decl: Int) {
        TODO()
    }

    fun writeExpr(func: FunctionImpl?, instr: Instruction) {
        when (instr) {
            is Comment -> {}
            is LoadInstr, is StoreInstr -> {
                // must be handled differently than SimpleInstr
                writeOpcode((instr as SimpleInstr).opcode)
                val alignment = when(instr){
                    is LoadInstr -> instr.numBytes
                    is StoreInstr -> instr.numBytes
                    else -> 0
                }.countTrailingZeroBits()
                writeU32Leb128(alignment) // todo is this alignment ok???
                writeU32Leb128(0) // todo is this address/offset ok???
            }
            is SimpleInstr -> writeOpcode(instr.opcode)
            is ParamGet -> {
                writeOpcode(Opcode.LOCAL_GET)
                writeS32Leb128(instr.index)
            }
            is ParamSet -> {
                writeOpcode(Opcode.LOCAL_SET)
                writeS32Leb128(instr.index)
            }
            is GlobalGet -> {
                assertTrue(instr.index >= 0)
                writeOpcode(Opcode.GLOBAL_GET)
                writeS32Leb128(instr.index)
            }
            is GlobalSet -> {
                assertTrue(instr.index >= 0)
                writeOpcode(Opcode.GLOBAL_GET)
                writeS32Leb128(instr.index)
            }
            is LocalGet -> {
                writeOpcode(Opcode.LOCAL_GET)
                val index = func!!.params.size +
                        func.locals.indexOfFirst { it.name == instr.name }
                writeS32Leb128(index)
            }
            is LocalSet -> {
                writeOpcode(Opcode.LOCAL_SET)
                val index = func!!.params.size +
                        func.locals.indexOfFirst { it.name == instr.name }
                writeS32Leb128(index)
            }
            is Const -> {
                when (instr.type) {
                    ConstType.I32 -> {
                        writeOpcode(Opcode.I32_CONST)
                        writeS32Leb128(instr.value as Int)
                    }
                    ConstType.I64 -> {
                        writeOpcode(Opcode.I64_CONST)
                        writeS64Leb128(instr.value as Long)
                    }
                    ConstType.F32 -> {
                        writeOpcode(Opcode.F32_CONST)
                        stream.writeLE32((instr.value as Float).toRawBits())
                    }
                    ConstType.F64 -> {
                        writeOpcode(Opcode.F64_CONST)
                        stream.writeLE64((instr.value as Double).toRawBits())
                    }
                    else -> throw NotImplementedError()
                }
            }
            is Call -> {
                assertTrue(instr.index >= 0)
                writeOpcode(Opcode.CALL)
                writeS32Leb128(instr.index)
            }
            is CallIndirect -> {
                assertTrue(instr.typeIndex >= 0)
                writeOpcode(Opcode.CALL_INDIRECT)
                writeS32Leb128(instr.typeIndex)
                writeS32Leb128(0) // table index
            }
            is IfBranch -> {
                assertTrue(instr.typeIndex >= 0)
                writeOpcode(Opcode.IF)
                writeS32Leb128(instr.typeIndex)
                writeExprList(func, instr.ifTrue)
                if (instr.ifFalse.isNotEmpty()) {
                    writeOpcode(Opcode.ELSE)
                    writeExprList(func, instr.ifFalse)
                    writeOpcode(Opcode.END)
                } else writeOpcode(Opcode.END)
            }
            is LoopInstr -> {
                assertTrue(instr.typeIndex >= 0)
                writeOpcode(Opcode.LOOP)
                writeS32Leb128(instr.typeIndex)
                writeExprList(func, instr.body)
                writeOpcode(Opcode.END)
            }
            is Jump -> {
                assertTrue(instr.depth >= 0)
                writeOpcode(Opcode.BR)
                writeS32Leb128(instr.depth)
            }
            is JumpIf -> {
                assertTrue(instr.depth >= 0)
                writeOpcode(Opcode.BR_IF)
                writeS32Leb128(instr.depth)
            }
            else -> throw NotImplementedError("Unknown instruction $instr")
        }
    }

    private fun writeExprList(func: FunctionImpl?, expressions: List<Instruction>) {
        for (i in expressions.indices) {
            writeExpr(func, expressions[i])
        }
    }

    fun writeOpcode(opcode: Opcode) {
        stream.write(opcode.opcode)
    }

    fun writeInitExpr(expr: List<Instruction>) {
        writeExprList(null, expr)
        writeOpcode(Opcode.END)
    }

    private fun writeParams(params: List<Type>) {
        writeU32Leb128(params.size)
        for (i in params.indices) {
            writeType(params[i])
        }
    }

    private fun writeTypeSection() {
        val types = module.types
        if (types.isEmpty()) return
        beginSection(SectionType.TYPE, types.size)
        for (i in types.indices) {
            val type = types[i]
            when (type.kind) {
                TypeKind.FUNC -> {
                    val funcType = type as FuncType
                    val sig = funcType.sig
                    writeType(TypeKind.FUNC)
                    writeParams(sig.paramTypes)
                    writeParams(sig.resultTypes)
                }
                TypeKind.STRUCT -> {
                    val type2 = type as StructType
                    writeType(TypeKind.STRUCT)
                    writeU32Leb128(type2.fields.size)
                    for (field in type2.fields) {
                        writeType(field.type)
                        stream.write(field.mutable.toInt())
                    }
                }
                TypeKind.ARRAY -> {
                    val type2 = type as ArrayType
                    writeType(TypeKind.ARRAY)
                    writeType(type2.field.type)
                    stream.write(type2.field.mutable.toInt())
                }
                else -> throw NotImplementedError()
            }
        }
        endSection()
    }

    private fun writeImportSection() {
        val imports = module.imports
        if (imports.isEmpty()) return
        beginSection(SectionType.IMPORT, imports.size)
        for (i in imports.indices) {
            val import = imports[i]
            writeStr(import.moduleName)
            writeStr(import.fieldName)
            stream.write(import.kind.ordinal)
            when (import.kind) {
                ExternalKind.FUNC -> {
                    numFuncImports++
                    writeU32Leb128((import as FuncImport).declIndex)
                }
                ExternalKind.TABLE -> {
                    numTableImports++
                    writeTable((import as TableImport).table)
                }
                ExternalKind.MEMORY -> {
                    numMemoryImports++
                    writeMemory((import as MemoryImport).memory)
                }
                ExternalKind.GLOBAL -> {
                    numGlobalImports++
                    writeGlobalHeader((import as GlobalImport).global)
                }
                ExternalKind.TAG -> {
                    numTagImports++
                    writeU32Leb128((import as TagImport).tag.funcTypeIndex)
                }
            }
        }
        endSection()
    }

    private fun writeFunctionSection() {
        val numFunctions = module.functions.size - numFuncImports
        assertTrue(numFunctions >= 0)
        if (numFunctions == 0) return
        beginSection(SectionType.FUNCTION, numFunctions)
        for (i in numFuncImports until module.functions.size) {
            writeS32Leb128(module.functions[i].typeIndex)
        }
        endSection()
    }

    private fun writeTableSection() {
        val numTables = module.tables.size - numTableImports
        assertTrue(numTables >= 0)
        if (numTables == 0) return
        beginSection(SectionType.TABLE, numTables)
        for (i in numTableImports until module.tables.size) {
            writeTable(module.tables[i])
        }
        endSection()
    }

    private fun writeMemorySection() {
        val numMemories = module.memories.size - numMemoryImports
        assertTrue(numMemories >= 0)
        if (numMemories == 0) return
        beginSection(SectionType.MEMORY, numMemories)
        for (i in numMemoryImports until module.memories.size) {
            writeMemory(module.memories[i])
        }
        endSection()
    }

    private fun writeTagsSection() {
        val numTags = module.tags.size - numTagImports
        assertTrue(numTags >= 0)
        if (numTags == 0) return
        beginSection(SectionType.TAG, numTags)
        for (i in numTagImports until module.tags.size) {
            writeU32Leb128(module.tags[i].funcTypeIndex)
        }
        endSection()
    }

    private fun writeGlobalsSection() {
        val numGlobals = module.globals.size - numGlobalImports
        assertTrue(numGlobals >= 0)
        if (numGlobals == 0) return
        beginSection(SectionType.GLOBAL, numGlobals)
        for (i in numGlobalImports until module.globals.size) {
            val global = module.globals[i]
            writeGlobalHeader(global)
            writeInitExpr(global.initExpr)
        }
        endSection()
    }

    private fun writeExportsSection() {
        if (module.exports.isEmpty()) return
        beginSection(SectionType.EXPORT, module.exports.size)
        for (export in module.exports) {
            writeStr(export.name)
            stream.write(export.kind.ordinal)
            writeS32Leb128(export.valueIndex)
        }
        endSection()
    }

    fun writeElemSection() {
        if (module.elemSegments.isEmpty()) return
        beginSection(SectionType.ELEM, module.elemSegments.size)
        for (elemSegment in module.elemSegments) {
            stream.write(elemSegment.flags)
            assertEquals(0, elemSegment.flags) // nothing else was implemented
            writeInitExpr(listOf(i32Const0))
            writeU32Leb128(elemSegment.functionTable.size)
            for (i in elemSegment.functionTable.indices) {
                writeS32Leb128(elemSegment.functionTable[i])
            }
        }
        endSection()
    }

    private fun writeFuncLocals(locals: List<LocalVariable>) {
        writeS32Leb128(locals.size)
        // todo could be compressed by using local-type-count
        //   when we do that, sort the local variables by type
        for (i in locals.indices) {
            val local = locals[i]
            writeU32Leb128(1)
            writeType(
                when (local.type) {
                    i32 -> TypeKind.I32
                    i64 -> TypeKind.I64
                    f32 -> TypeKind.F32
                    f64 -> TypeKind.F64
                    else -> throw NotImplementedError()
                }
            )
        }
    }

    private fun writeFunc(func: FunctionImpl) {
        writeFuncLocals(func.locals)
        try {
            writeExprList(func, func.body)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        writeOpcode(Opcode.END)
    }

    fun writeCodeSection() {
        if (module.code.isEmpty()) return
        beginSection(SectionType.CODE, module.code.size)
        for (func in module.code) {
            val start = writeU32Leb128Space()
            writeFunc(func)
            writeFixupU32Leb128Size(start)

            // todo relocations???
        }
        endSection()
    }

    private var numFuncImports = 0
    private var numTableImports = 0
    private var numMemoryImports = 0
    private var numTagImports = 0
    private var numGlobalImports = 0

    private fun writeHeader() {
        stream.writeLE32(BINARY_MAGIC)
        stream.writeLE32(BINARY_VERSION)
    }

    fun write() {
        writeHeader()
        writeTypeSection()
        writeImportSection() // must come before the rest
        writeFunctionSection()
        writeTableSection()
        writeMemorySection()
        writeTagsSection()
        writeGlobalsSection()
        writeExportsSection()
        // writeStartsSection()
        writeElemSection()
        // bulk-memory-DataCount-section
        writeCodeSection()
    }

}