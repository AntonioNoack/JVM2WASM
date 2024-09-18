package wasm.writer

class GlobalImport(moduleName: String, fieldName: String, val global: Global) :
    Import(ExternalKind.GLOBAL, moduleName, fieldName)