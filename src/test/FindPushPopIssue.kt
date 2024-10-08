package test

import me.anno.utils.types.Strings.indexOf2
import utils.wasmFolder
import utils.wasmTextFile

fun main() {
    // there was more pushes than pops...
    //  find out why, and fix it
    val text = wasmTextFile.readTextSync()
    var i = 0
    val prefix = "call \$stackPush"
    while (true) {
        i = text.indexOf(prefix, i)
        if (i < 0) break
        val j = text.indexOf2("call \$stackPop", i)
        val ln = text.subSequence(i, j).count { it == '\n' }
        if (ln > 10) {
            val lineNumber = text.subSequence(0, i).count { it == '\n' } + 1
            println("Weird!! #$lineNumber ($ln)")
        }
        i += prefix.length
    }
}