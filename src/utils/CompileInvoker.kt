package utils

import me.anno.utils.Clock
import org.apache.logging.log4j.LogManager
import targetsFolder
import translator.GeneratorIndex
import wasm.parser.FunctionImpl
import wasm.parser.WATParser
import wasm.writer.WASMWriter.writeWASM

private val LOGGER = LogManager.getLogger("CompileInvoker")

val wasmFolder = targetsFolder.getChild("wasm")
val wasmTextFile = wasmFolder.getChild("jvm2wasm.wat")
val wasmOutputFile = wasmFolder.getChild("jvm2wasm.wasm")
val debugFolder = wasmFolder.getSibling("debug").apply {
    delete()
    mkdirs()
}

fun compileToWASM(printer: StringBuilder2, clock: Clock) {
    LOGGER.info("[Writing Output]")
    wasmTextFile.writeBytes(printer.values, 0, printer.size)
    clock.stop("Write WAT")

    // todo why/how is there a difference between using the values directly, and parsing them????
    val parser = WATParser()
    parser.parse(printer.toString())
    val wasmBytes = writeWASM(parser)
    wasmOutputFile.writeBytes(wasmBytes.values, 0, wasmBytes.size)
    clock.stop("Write WASM")
}

fun collectAllMethods(clock: Clock): ArrayList<FunctionImpl> {
    val normalMethods = GeneratorIndex.translatedMethods.values
    val helperMethods = helperMethods.values
    val getNthMethods = GeneratorIndex.nthGetterMethods.values
    val size = normalMethods.size + helperMethods.size + getNthMethods.size
    val functions = ArrayList<FunctionImpl>(size)
    functions.addAll(normalMethods)
    functions.addAll(helperMethods)
    functions.addAll(getNthMethods)
    clock.stop("Collecting Methods")
    return functions
}

