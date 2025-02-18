package wasm2cpp

import jvm2wasm

fun main() {
    // todo use internal representation instead of (de)serialization
    // todo create 16 C++ files by package and compile them in parallel
    // todo create nicely-readable C++
    jvm2wasm()
    wasm2cpp()
}