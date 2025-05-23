package wasm.instr

import interpreter.WASMEngine
import me.anno.utils.assertions.assertFalse
import me.anno.utils.structures.lists.Lists.createList

data class Call(val name: String) : Instruction {

    var index = -1

    init {
        assertFalse(name.startsWith('$'))
    }

    override fun toString(): String = "call \$$name"

    override fun execute(engine: WASMEngine): String? {
        throw NotImplementedError()
    }

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

        val arrayLength = Call("arrayLength")
        val arrayLengthU = Call("arrayLengthU")

        val resolveIndirect = Call("resolveIndirect")
        val resolveIndirectFail = Call("resolveIndirectFail")

        val setFieldI8 = Call("setFieldI8")
        val setFieldI16 = Call("setFieldI16")
        val setFieldI32 = Call("setFieldI32")
        val setFieldI64 = Call("setFieldI64")
        val setFieldF32 = Call("setFieldF32")
        val setFieldF64 = Call("setFieldF64")

        // value-instance-offset setters
        val setVIOFieldI8 = Call("setVIOFieldI8")
        val setVIOFieldI16 = Call("setVIOFieldI16")
        val setVIOFieldI32 = Call("setVIOFieldI32")
        val setVIOFieldI64 = Call("setVIOFieldI64")
        val setVIOFieldF32 = Call("setVIOFieldF32")
        val setVIOFieldF64 = Call("setVIOFieldF64")

        val setStaticFieldI8 = Call("setStaticFieldI8")
        val setStaticFieldI16 = Call("setStaticFieldI16")
        val setStaticFieldI32 = Call("setStaticFieldI32")
        val setStaticFieldI64 = Call("setStaticFieldI64")
        val setStaticFieldF32 = Call("setStaticFieldF32")
        val setStaticFieldF64 = Call("setStaticFieldF64")

        val getFieldS8 = Call("getFieldS8")
        val getFieldS16 = Call("getFieldS16")
        val getFieldU16 = Call("getFieldU16")
        val getFieldI32 = Call("getFieldI32")
        val getFieldI64 = Call("getFieldI64")
        val getFieldF32 = Call("getFieldF32")
        val getFieldF64 = Call("getFieldF64")

        val getStaticFieldS8 = Call("getStaticFieldS8")
        val getStaticFieldS16 = Call("getStaticFieldS16")
        val getStaticFieldU16 = Call("getStaticFieldU16")
        val getStaticFieldI32 = Call("getStaticFieldI32")
        val getStaticFieldI64 = Call("getStaticFieldI64")
        val getStaticFieldF32 = Call("getStaticFieldF32")
        val getStaticFieldF64 = Call("getStaticFieldF64")

        val panic = Call("panic")
        val readClass = Call("readClass")
        val findClass = Call("findClass")
        val instanceOf = Call("instanceOf")
        val instanceOfNonInterface = Call("instanceOfNonInterface")
        val instanceOfExact = Call("instanceOfExact")

        val createObjectArray = Call("createObjectArray")
        val createNativeArray = createList(7) { Call("createNativeArray$it") }

        val createInstance = Call("createInstance")

        /**
         * Creates a pseudo-instance based on a single i32.
         * Only allowed for instances, which aren't used as locks, and which have no fields.
         * */
        val getClassIdPtr = Call("getClassIdPtr")

        val checkCast = Call("checkCast")
        val checkCastExact = Call("checkCastExact")

        val stackPush = Call("stackPush")
        val stackPop = Call("stackPop")

        val lcmp = Call("lcmp")
        val fcmpl = Call("fcmpl") // -1 if NaN
        val fcmpg = Call("fcmpg") // +1 if NaN
        val dcmpl = Call("dcmpl") // -1 if NaN
        val dcmpg = Call("dcmpg") // +1 if NaN

        val dupI32 = Call("dupi32")
        val dupI64 = Call("dupi64")

        val i32Neg = Call("i32neg")
        val i64Neg = Call("i64neg")

        val f32Rem = Call("f32rem")
        val f64Rem = Call("f64rem")

        val checkNonZero32 = Call("checkNonZero32")
        val checkNonZero64 = Call("checkNonZero64")

        val safeDiv32 = Call("safeDiv32")
        val safeDiv64 = Call("safeDiv64")

        val f2i = Call("f2i")
        val f2l = Call("f2l")
        val d2i = Call("d2i")
        val d2l = Call("d2l")

        val resolveInterface = Call("resolveInterface")
    }
}