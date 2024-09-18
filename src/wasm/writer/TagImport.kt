package wasm.writer

class TagImport(moduleName: String, fieldName: String, val tag: Tag) :
    Import(ExternalKind.TAG, moduleName, fieldName)