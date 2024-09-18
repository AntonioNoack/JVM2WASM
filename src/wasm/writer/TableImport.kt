package wasm.writer

class TableImport(moduleName: String, fieldName: String, val table: Table) :
    Import(ExternalKind.TABLE, moduleName, fieldName)