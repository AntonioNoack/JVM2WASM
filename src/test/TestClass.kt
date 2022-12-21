package test

import kotlin.math.sqrt

class TestClass : Runnable {

    @JvmField
    var x = 0

    @JvmField
    var y = 9.0

    @JvmField
    var z = "Hello World"

    @JvmField
    var k = false

    fun add(a: Int, b: Int) = a + b

    fun sin(f: Float) = kotlin.math.sin(f)

    fun create() = TestClass()

    /*fun print(str: String) {
        println(str)
    }*/

    /*fun print(str: String, arg: Any?) {
        println("$str$arg")
    }*/

    fun idx(n: Int) = IntArray(n) { it }

    override fun run() {
        // println(arrayOf("Hi").joinToString())
    }

    fun k(a: Int, b: Int) = if (a < b) b else -a

    fun j(a: Float, b: Float) = a > b || a.toDouble() > 0.0

    fun length(a: Float, b: Float) = sqrt(a * a + b * b)

    fun x(a: Any?) = a is TestClass

}