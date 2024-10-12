package wasm.writer

import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.arrays.ByteArrayList
import me.anno.utils.types.Booleans.hasFlag
import me.anno.utils.types.Booleans.toInt

class BinaryWriter(
    val stream: ByteArrayList,
    val module: Module
) {

    companion object {
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

    private val BINARY_MAGIC = 0x6d736100 // \0asm
    private val BINARY_VERSION = 1

    var lastSectionPayloadOffset = 0
    val LEB_SECTION_SIZE_GUESS = 1
    val MAX_U32_LEB128_BYTES = 5

    fun writeSectionHeader(type: SectionType) {
        if (lastSectionLebSizeGuess != 0) throw IllegalStateException()
        // writeHeader()
        stream.write(type.ordinal)
        lastSectionType = type
        lastSectionLebSizeGuess = LEB_SECTION_SIZE_GUESS
        lastSectionOffset = writeU32Leb128Space(LEB_SECTION_SIZE_GUESS)
        lastSectionPayloadOffset = stream.size
    }

    val canonicalize_lebs = false
    fun writeU32Leb128Space(lebSizeGuess: Int): Int {
        val result = stream.size
        val bytesToWrite = if (canonicalize_lebs) lebSizeGuess else MAX_U32_LEB128_BYTES
        stream.ensureExtra(bytesToWrite)
        for (i in 0 until bytesToWrite) {
            stream.addUnsafe(0)
        }
        return result
    }

    private fun u32Leb128Length(i: Int): Int {
        var size = 0
        var x = i
        do {
            x = x ushr 7
            size++
        } while (x != 0)
        return size
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
        stream.ensureExtra(MAX_U32_LEB128_BYTES)
        var x = v
        do {
            stream.addUnsafe(x.toByte())
            x = x ushr 7
        } while (x != 0L)
    }

    private fun writeU32Leb128At(o: Int, v: Int) {
        stream.ensureExtra(MAX_U32_LEB128_BYTES)
        var x = v
        var i = o
        do {
            stream[i++] = x.toByte()
            x = x ushr 7
        } while (x != 0)
    }

    private fun writeFixupU32Leb128Size(offset: Int, lebSizeGuess: Int): Int {
        if (canonicalize_lebs) {
            val size = stream.size - offset - lebSizeGuess
            val lebSize = u32Leb128Length(size)
            val delta = lebSize - lebSizeGuess
            if (delta != 0) {
                val srcOffset = offset + lebSizeGuess
                val dstOffset = offset + lebSize
                System.arraycopy(stream.values, srcOffset, stream.values, dstOffset, size)
            }
            writeU32Leb128At(offset, size)
            stream.size += delta
            return delta
        } else {
            val size = stream.size - offset - MAX_U32_LEB128_BYTES
            writeFixedU32Leb128At(offset, size)
            return 0
        }
    }

    private fun writeFixedU32Leb128At(offset: Int, x: Int) {
        stream.ensureCapacity(offset + 5)
        stream[offset] = (x or 0x80).toByte()
        stream[offset + 1] = ((x ushr 7) or 0x80).toByte()
        stream[offset + 2] = ((x ushr 14) or 0x80).toByte()
        stream[offset + 3] = ((x ushr 21) or 0x80).toByte()
        stream[offset + 4] = (x ushr 28).and(0x7f).toByte()
    }

    var lastSectionType = SectionType.CUSTOM

    fun beginKnownSection(type: SectionType) {
        writeSectionHeader(type)
    }

    fun beginSection(type: SectionType, numElements: Int) {
        println("Writing section $type, x$numElements @${stream.size}")
        beginKnownSection(type)
        writeU32Leb128(numElements)
    }

    var lastSectionOffset = 0
    var lastSectionLebSizeGuess = 0

    fun endSection() {
        if (lastSectionLebSizeGuess == 0) throw IllegalStateException()
        writeFixupU32Leb128Size(lastSectionOffset, lastSectionLebSizeGuess)
        lastSectionLebSizeGuess = 0
    }

    fun writeStr(name: String) {
        val bytes = name.toByteArray()
        writeU32Leb128(bytes.size)
        stream.write(bytes)
    }

    fun writeS32Leb128(v: Int) {
        var value = v
        if (v < 0) {
            while (true) {
                val byte = value.and(0x7f)
                value = value shr 7
                if (value == -1 && byte.hasFlag(0x40)) {
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
                if (value == 0 && !byte.hasFlag(0x40)) {
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
        writeS32Leb128(type.kind.id)
        if (type.kind == TypeKind.REFERENCE) {
            writeS32Leb128(type.index)
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
        writeU32Leb128(flags)
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

    fun writeExpr(func: Func?, expr: Expr) {
        when (expr) {
            is Unary -> writeOpcode(expr.opcode)
            is Binary -> writeOpcode(expr.opcode)
            is Ternary -> writeOpcode(expr.opcode)
            is Block -> {
                writeOpcode(Opcode.BLOCK)
                writeBlockDecl(expr.declIndex)
                writeExprList(func, expr.expr)
                writeOpcode(Opcode.END)
            }
            Return -> writeOpcode(Opcode.RETURN)
            // is Br -> {}
            else -> {
                TODO()
            }
        }
    }

    private fun writeExprList(func: Func?, expressions: List<Expr>) {
        for (i in expressions.indices) {
            writeExpr(func, expressions[i])
        }
    }

    fun writeOpcode(opcode: Opcode) {
        stream.write(opcode.opcode)
    }

    fun writeInitExpr(expr: List<Expr>) {
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
                else -> {}
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
            writeU32Leb128(module.functions[i].typeIndex)
        }
        endSection()
    }

    private fun writeTableSection() {
        val numTables = module.tables.size - numTableImports
        assertTrue(numTables >= 0)
        if (numTables == 0) return
        beginSection(SectionType.TABLE, numTables)
        for (i in numMemoryImports until module.tables.size) {
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
            stream.write(export.kind.ordinal)
            writeU32Leb128(export.valueIndex)
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
    }

}