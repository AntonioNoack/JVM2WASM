package wasm.parser

data class Import(val funcName: String, val params: List<String>, val results: List<String>)