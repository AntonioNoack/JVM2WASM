package test

import jvm.NativeLog.log

open class InvokeTest {

    open fun a() {
        log(70, 0)
    }

    open fun b() {
        log(70, 1)
    }

    fun c() {
        log(70, 2)
    }

}

class A : InvokeTest() {
    override fun a() {
        log(70, 3)
        super.a()
        b()
    }

    override fun b() {
        log(70, 4)
        super.b()
        c()
    }
}

class B : InvokeTest() {
    override fun a() {
        log(70, 5)
        super.a()
        c()
    }

    override fun b() {
        log(70, 6)
        super.b()
        c()
    }
}