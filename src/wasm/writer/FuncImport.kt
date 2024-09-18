package wasm.writer

class FuncImport(moduleName: String, fieldName: String, val declIndex: Int) :
        Import(ExternalKind.FUNC, moduleName, fieldName)