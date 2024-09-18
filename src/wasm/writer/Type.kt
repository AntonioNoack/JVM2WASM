package wasm.writer

import wasm.writer.BinaryWriter.Companion.kInvalidIndex

open class Type(val kind: TypeKind, val index: Int = kInvalidIndex)