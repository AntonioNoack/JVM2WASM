package wasm.writer

import wasm.writer.BinaryWriter.Companion.kInvalidIndex

open class Type(val kind: TypeKind, val referenceIndex: Int = kInvalidIndex) {
    override fun toString(): String {
        return if (kind == TypeKind.REFERENCE) "Type($kind,$referenceIndex)" else "Type($kind)"
    }
}