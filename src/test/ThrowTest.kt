package test

fun main() {
    try {
        handleFinally()
    } catch (e: Throwable) {
        e.printStackTrace()
    }
    try {
        handleRethrow()
    } catch (e: Throwable) {
        e.printStackTrace()
    }
}

fun createFunc(): Throwable {
    return Throwable()
}

fun throwFunc() {
    throw createFunc()
}

fun handleFinally() {
    try {
        throwFunc()
    } finally {
        println("finally")
    }
}

fun handleRethrow() {
    try {
        throwFunc()
    } catch (e: Throwable) {
        println("rethrow")
        throw e
    }
}