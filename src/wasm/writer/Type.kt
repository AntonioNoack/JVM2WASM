package wasm.writer

import wasm.writer.BinaryWriter.Companion.K_INVALID_INDEX

open class Type(val kind: TypeKind, val referenceIndex: Int = K_INVALID_INDEX) {
    override fun toString(): String {
        return if (kind == TypeKind.REFERENCE) "Type($kind,$referenceIndex)" else "Type($kind)"
    }
}