package wasm.writer

class FuncImport(moduleName: String, fieldName: String, val index: Int) :
        Import(ExternalKind.FUNC, moduleName, fieldName)