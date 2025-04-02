package wasm2cpp

import jvm2wasm
import me.anno.utils.Clock

fun main() {
    val clock = Clock("JVM2CPP")
    jvm2wasm()
    val testWATParser = true
    if (testWATParser) {
        validate()
    }
    wasm2cppFromMemory()
    clock.total("JVM2CPP")
}
