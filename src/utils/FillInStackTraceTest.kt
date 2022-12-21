package utils

fun main() {

    try {
        val ex = NullPointerException()
        ex.printStackTrace()
        throw ex
    } catch (e: Exception) {
        e.printStackTrace()
        e.fillInStackTrace()
        e.printStackTrace()
    }

}