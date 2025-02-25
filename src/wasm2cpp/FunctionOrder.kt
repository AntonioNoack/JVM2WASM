package wasm2cpp

import me.anno.utils.structures.Compare.ifSame
import wasm.parser.FunctionImpl

object FunctionOrder : Comparator<FunctionImpl> {
    override fun compare(a: FunctionImpl, b: FunctionImpl): Int {
        return a.results.size.compareTo(b.results.size)
            .ifSame { a.funcName.compareTo(b.funcName) }
    }
}