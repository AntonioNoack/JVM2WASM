package wasm.writer

class MemoryImport(moduleName: String, fieldName: String, val memory: Memory) :
    Import(ExternalKind.TABLE, moduleName, fieldName)