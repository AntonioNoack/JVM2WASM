package wasm.instr

class Call(val name: String) : Instruction {

    init {
        if (name.startsWith('$')) throw IllegalArgumentException(name)
    }

    override fun toString(): String = "call \$$name"

    companion object {
        val s8ArrayLoad = Call("s8ArrayLoad")
        val s8ArrayLoadU = Call("s8ArrayLoadU")

        val u16ArrayLoad = Call("u16ArrayLoad")
        val u16ArrayLoadU = Call("u16ArrayLoadU")

        val s16ArrayLoad = Call("s16ArrayLoad")
        val s16ArrayLoadU = Call("s16ArrayLoadU")

        val i32ArrayLoad = Call("i32ArrayLoad")
        val i32ArrayLoadU = Call("i32ArrayLoadU")

        val i64ArrayLoad = Call("i64ArrayLoad")
        val i64ArrayLoadU = Call("i64ArrayLoadU")

        val f32ArrayLoad = Call("f32ArrayLoad")
        val f32ArrayLoadU = Call("f32ArrayLoadU")

        val f64ArrayLoad = Call("f64ArrayLoad")
        val f64ArrayLoadU = Call("f64ArrayLoadU")

        val i8ArrayStore = Call("i8ArrayStore")
        val i8ArrayStoreU = Call("i8ArrayStoreU")

        val i16ArrayStore = Call("i16ArrayStore")
        val i16ArrayStoreU = Call("i16ArrayStoreU")

        val i32ArrayStore = Call("i32ArrayStore")
        val i32ArrayStoreU = Call("i32ArrayStoreU")

        val i64ArrayStore = Call("i64ArrayStore")
        val i64ArrayStoreU = Call("i64ArrayStoreU")

        val f32ArrayStore = Call("f32ArrayStore")
        val f32ArrayStoreU = Call("f32ArrayStoreU")

        val f64ArrayStore = Call("f64ArrayStore")
        val f64ArrayStoreU = Call("f64ArrayStoreU")

        val al = Call("al")
        val alU = Call("alU")

        val resolveIndirect = Call("resolveIndirect")
        val resolveIndirectFail = Call("resolveIndirectFail")

    }
}