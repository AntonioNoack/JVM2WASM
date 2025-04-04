package wasm2js

import jvm2wasm
import me.anno.utils.Clock
import wasm2cpp.validate

fun main() {
    val clock = Clock("JVM2JS")
    jvm2wasm()
    val testWATParser = true
    if (testWATParser) {
        validate()
    }
    wasm2jsFromMemory()
    clock.total("JVM2JS")
}

fun wasm2jsFromMemory() {
    // todo implement high-level JavaScript generation
    TODO()
}