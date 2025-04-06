package utils

import me.anno.utils.structures.lists.Lists.arrayListOfNulls
import java.lang.reflect.Modifier

object OpCode {

    private val opcodeNames = arrayListOfNulls<String>(256)

    init {
        val clazz = org.objectweb.asm.Opcodes::class.java
        val intType = Int::class.javaPrimitiveType
        for (field in clazz.declaredFields) {
            val name = field.name
            if (name.startsWith("ACC_") || name.startsWith("T_") ||
                name.startsWith("H_") || name.startsWith("F_") || name.startsWith("V") ||
                name.startsWith("ASM") || name.startsWith("SOURCE_") ||
                field.type != intType || !Modifier.isStatic(field.modifiers)
            ) continue
            val index = field.getInt(null)
            val prevName = opcodeNames[index]
            if (prevName != null) throw IllegalStateException("Duplicate value $name/$prevName,$index")
            opcodeNames[index] = name
        }
    }

    operator fun get(opcode: Int): String {
        val base = opcodeNames[opcode]
        if (base != null) return base
        return when (opcode) {
            0x01 -> "aconst_null"
            0x13 -> "ldc_w"
            0x14 -> "ldc2_w"
            0x1a -> "iload_0"
            0x1b -> "iload_1"
            0x1c -> "iload_2"
            0x1d -> "iload_3"
            0x1e -> "lload_0"
            0x1f -> "lload_1"
            0x20 -> "lload_2"
            0x21 -> "lload_3"
            0x22 -> "fload_0"
            0x23 -> "fload_1"
            0x24 -> "fload_2"
            0x25 -> "fload_3"
            0x26 -> "dload_0"
            0x27 -> "dload_1"
            0x28 -> "dload_2"
            0x29 -> "dload_3"
            0x2a -> "aload_0"
            0x2b -> "aload_1"
            0x2c -> "aload_2"
            0x2d -> "aload_3"
            0x3b -> "istore_0"
            0x3c -> "istore_1"
            0x3d -> "istore_2"
            0x3e -> "istore_3"
            0x3f -> "lstore_0"
            0x40 -> "lstore_1"
            0x41 -> "lstore_2"
            0x42 -> "lstore_3"
            0x43 -> "fstore_0"
            0x44 -> "fstore_1"
            0x45 -> "fstore_2"
            0x46 -> "fstore_3"
            0x47 -> "dstore_0"
            0x48 -> "dstore_1"
            0x49 -> "dstore_2"
            0x4a -> "dstore_3"
            0x4b -> "astore_0"
            0x4c -> "astore_1"
            0x4d -> "astore_2"
            0x4e -> "astore_3"
            0xc4 -> "wide"
            0xc8 -> "goto_w"
            0xc9 -> "jsr_w"
            0xca -> "breakpoint"
            0xfe -> "impdep1"
            0xff -> "impdep2"
            in 0xcb..0xfd -> "(no name)"
            else -> "#$opcode"
        }
    }
}