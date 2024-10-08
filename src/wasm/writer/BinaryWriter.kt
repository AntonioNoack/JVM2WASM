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

    var lastSectionOffset = 0
    var lastSectionLebSizeGuess = 0
    var sectionCount = 0

    fun endSection() {
        if (lastSectionLebSizeGuess == 0) throw IllegalStateException()
        writeFixupU32Leb128Size(lastSectionOffset, lastSectionLebSizeGuess)
        lastSectionLebSizeGuess = 0
        sectionCount++
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

    fun writeExprList(func: Func?, expr: List<Expr>) {
        for (exp in expr) {
            writeExpr(func, exp)
        }
    }

    fun writeOpcode(opcode: Opcode) {
        if (opcode.prefix != 0) {
            stream.write(opcode.prefix)
            writeU32Leb128(opcode.opcode)
        } else {
            stream.write(opcode.opcode)
        }
    }

    fun writeInitExpr(expr: List<Expr>) {
        writeExprList(null, expr)
        writeOpcode(Opcode.END)
    }

    fun write() {
        stream.writeLE32(BINARY_MAGIC)
        stream.writeLE32(BINARY_VERSION)
        if (module.types.isNotEmpty()) {
            beginKnownSection(SectionType.TYPE)
            writeU32Leb128(module.types.size)
            for (type in module.types) {
                when (type.kind) {
                    TypeKind.FUNC -> {
                        val funcType = type as FuncType
                        writeType(TypeKind.FUNC)
                        val sig = funcType.sig
                        writeU32Leb128(sig.paramTypes.size)
                        for (t in sig.paramTypes) {
                            writeType(t)
                        }
                        writeU32Leb128(sig.resultTypes.size)
                        for (t in sig.resultTypes) {
                            writeType(t)
                        }
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
        var numFuncImports = 0
        var numTableImports = 0
        var numMemoryImports = 0
        var numTagImports = 0
        var numGlobalImports = 0
        if (module.imports.isNotEmpty()) {
            beginKnownSection(SectionType.IMPORT)
            for (i in module.imports.indices) {
                val import = module.imports[i]
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
        val numFunctions = module.functions.size - numFuncImports
        assertTrue(numFunctions >= 0)
        if (numFunctions > 0) {
            beginKnownSection(SectionType.FUNCTION)
            writeU32Leb128(numFunctions)
            for (i in numFuncImports until module.functions.size) {
                writeU32Leb128(module.functions[i].typeIndex)
            }
            endSection()
        }
        val numTables = module.tables.size - numTableImports
        assertTrue(numTables >= 0)
        if (numFunctions > 0) {
            beginKnownSection(SectionType.TABLE)
            writeU32Leb128(numTables)
            for (i in numMemoryImports until module.tables.size) {
                writeTable(module.tables[i])
            }
            endSection()
        }
        val numMemories = module.memories.size - numMemoryImports
        assertTrue(numMemories >= 0)
        if (numMemories > 0) {
            beginKnownSection(SectionType.MEMORY)
            writeU32Leb128(numMemories)
            for (i in numMemoryImports until module.memories.size) {
                writeMemory(module.memories[i])
            }
            endSection()
        }
        val numTags = module.tags.size - numTagImports
        assertTrue(numTags >= 0)
        if (numTags > 0) {
            beginKnownSection(SectionType.TAG)
            writeU32Leb128(numTags)
            for (i in numTagImports until module.tags.size) {
                writeU32Leb128(module.tags[i].funcTypeIndex)
            }
            endSection()
        }
        val numGlobals = module.globals.size - numGlobalImports
        assertTrue(numGlobals >= 0)
        if (numGlobals > 0) {
            beginKnownSection(SectionType.GLOBAL)
            writeU32Leb128(numGlobals)
            for (i in numGlobalImports until module.globals.size) {
                val global = module.globals[i]
                writeGlobalHeader(global)
                writeInitExpr(global.initExpr)
            }
            endSection()
        }
        if (module.exports.isNotEmpty()) {
            beginKnownSection(SectionType.EXPORT)
            writeU32Leb128(module.exports.size)
            for (export in module.exports) {
                stream.write(export.kind.ordinal)
                when (export.kind) {
                    ExternalKind.FUNC -> {
                        TODO()
                    }
                    ExternalKind.TABLE -> {
                        TODO()
                    }
                    ExternalKind.MEMORY -> {
                        TODO()
                    }
                    ExternalKind.GLOBAL -> {
                        TODO()
                    }
                    ExternalKind.TAG -> {
                        TODO()
                    }
                }
            }
            endSection()
        }
        /*if (module.starts.isNotEmpty()) {
            val startFuncIndex = module.getFuncIndex(module.starts[0])
            if (startFuncIndex != kInvalidIndex) {
                beginKnownSection(SectionType.START)
                writeU32Leb128(startFuncIndex)
                endSection()
            }
        }
        if (module.elemSegments.isNotEmtpty()) {
            TODO()
        }*/
        TODO()
    }


}