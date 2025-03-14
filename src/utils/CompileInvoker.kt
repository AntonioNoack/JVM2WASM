package utils

import clock
import dependency.DependencyIndex.constructableClasses
import gIndex
import me.anno.utils.OS
import me.anno.utils.OS.documents
import me.anno.utils.files.Files.formatFileSize
import org.apache.logging.log4j.LogManager
import translator.GeneratorIndex.classNamesByIndex
import java.io.InputStream
import kotlin.concurrent.thread

private val LOGGER = LogManager.getLogger("CompileInvoker")

val wasmFolder = documents.getChild("IdeaProjects/JVM2WASM/wasm")
val wasmTextFile = wasmFolder.getChild("jvm2wasm.wat")
val debugFolder = wasmFolder.getChild("debug").apply {
    delete()
    mkdirs()
}

fun compileToWASM(printer: StringBuilder2) {

    LOGGER.info("[compileToWASM]")
    wasmTextFile.writeBytes(printer.values, 0, printer.size)
    clock.total("Kotlin")

    when {
        OS.isWindows -> {
            if (false) {
                clock.start()
                val process = Runtime.getRuntime().exec("wsl")
                printAsync(process.inputStream, false)
                printAsync(process.errorStream, true)
                val stream = process.outputStream
                stream.write("cd ~ && ./comp.sh\n".toByteArray())
                stream.close()
                process.waitFor()
                clock.stop("WAT2WASM")
            } else {
                LOGGER.warn("Compiling via WSL is broken :/")
            }
        }
        else -> {
            LOGGER.warn("Automatic compilation hasn't been implemented yet for this platform")
        }
    }

}

private fun printAsync(input: InputStream, err: Boolean) {
    thread(name = "CompilerInvoker-async") {
        val reader = input.bufferedReader()
        while (true) {
            var line = reader.readLine() ?: break
            line = " - $line"
            if (err) {
                LOGGER.warn(line)
            } else {
                LOGGER.info(line)
            }
        }
    }
}