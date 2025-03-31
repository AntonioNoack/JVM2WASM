package wasm.writer

import me.anno.utils.Clock
import utils.wasmFolder
import utils.wasmTextFile
import wasm.parser.WATParser
import wasm.writer.WASMWriter.writeWASM

fun main() {

    // load wasm.wat file
    val clock = Clock("WASMWriter")
    val text = wasmTextFile.readTextSync()
    clock.stop("Read WAT")

    // tokenize it
    val parser = WATParser()
    parser.parse(text)
    clock.stop("Parsing")

    val wasmBytes = writeWASM(parser)
    clock.stop("Writing")

    wasmFolder.getChild("jvm2wasm.wasm")
        .writeBytes(wasmBytes.values, 0, wasmBytes.size)

    clock.total("Done :)")

}