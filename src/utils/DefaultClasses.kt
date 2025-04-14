package utils

import gIndex
import hIndex

object DefaultClasses {
    fun registerDefaultClasses(): List<String> {
        val predefinedClasses = listOf(
            "java/lang/Object", "[]", // 4
            "[I", "[F", // 4
            "[Z", "[B", // 1
            "[C", "[S", // 2
            "[J", "[D", // 8
            // do we really need to hardcode them? yes, for convenience in JavaScript
            // todo -> use named constants in JavaScript
            "java/lang/String", // #10
            "java/lang/Class", // #11
            "java/lang/System", // #12
            "java/lang/Number", // #13, must be before number child classes
            "java/lang/Throwable", // #14
            "java/lang/StackTraceElement", // #15
            "int", "long", "float", "double",
            "boolean", "byte", "short", "char", "void", // #24
            "java/lang/Integer", "java/lang/Long", "java/lang/Float", "java/lang/Double",
            "java/lang/Boolean", "java/lang/Byte", "java/lang/Short", "java/lang/Character", "java/lang/Void", // #33
        )

        for (i in 1 until 16) {
            hIndex.registerSuperClass(predefinedClasses[i], "java/lang/Object")
        }

        for (i in predefinedClasses.indices) {
            val clazz = predefinedClasses[i]
            gIndex.classIndex[clazz] = i
            gIndex.classNames.add(clazz)
        }

        StaticClassIndices.validateClassIndices()

        for (i in StaticClassIndices.FIRST_NATIVE..StaticClassIndices.LAST_NATIVE) {
            hIndex.registerSuperClass(predefinedClasses[i], "java/lang/Object")
        }

        return predefinedClasses
    }
}