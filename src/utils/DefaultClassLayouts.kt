package utils

import byteStrings
import gIndex
import hIndex
import jvm.JVMFlags.ptrSize
import jvm.JVMFlags.ptrSizeBits
import jvm.JVMShared.*
import me.anno.utils.assertions.assertEquals
import utils.Descriptor.Companion.voidDescriptor
import utils.StaticFieldOffsets.*
import wasm.instr.FuncType

object DefaultClassLayouts {

    const val GC_FIELD_NAME = "__classId_gcGen"

    fun <V> eq(a: V, b: V) {
        if (a != b) throw IllegalStateException("$a != $b")
    }

    fun eq(clazz: String, name: String, descriptor: String, offset: Int, check: Int) {
        eq(gIndex.getFieldOffset(clazz, name, descriptor, false), objectOverhead + offset)
        assertEquals(objectOverhead + offset, check)
    }

    /**
     * checks and registers offsets to be what we expect:
     * they might be used elsewhere in the project hardcoded to certain addresses - we should clean that up in the future
     * */
    fun registerDefaultOffsets() {

        val object0 = "java/lang/Object"
        val string = "java/lang/String"
        val system = "java/lang/System"
        val clazz = "java/lang/Class"
        val field = "java/lang/reflect/Field"
        val method = "java/lang/reflect/Method"
        val thread = "java/lang/Thread"
        val throwable = "java/lang/Throwable"
        val ste = "java/lang/StackTraceElement"

        val executable = "java/lang/reflect/Executable"
        val constructor = "java/lang/reflect/Constructor"
        val accessibleObject = "java/lang/reflect/AccessibleObject"
        val annotation = "java/lang/Annotation"

        val reader = "java/io/Reader"

        // todo we might be able to remove instance-init, because we have proper constructors now
        eq(gIndex.getDynMethodIdx(MethodSig.c(object0, INSTANCE_INIT, voidDescriptor)), 0)
        eq(gIndex.getType(true, voidDescriptor, true), FuncType(listOf(), listOf(ptrTypeI)))

        // prepare String properties
        gIndex.stringClass = gIndex.getClassId(string)
        gIndex.stringArrayClass = gIndex.getClassId(if (byteStrings) "[B" else "[C")

        // object overhead
        eq(object0, GC_FIELD_NAME, "int", -objectOverhead, OFFSET_OBJECT_CLASS_ID)

        // instance arrays
        eq("[]", "length", "int", 0, OFFSET_ARRAY_LENGTH)
        for (name in NativeTypes.nativeArrays) {
            eq(name, "length", "int", 0, OFFSET_ARRAY_LENGTH)
        }

        // strings, first the hash for alignment
        eq(string, "hash", "int", 0, OFFSET_STRING_HASH)
        eq(string, "value", if (byteStrings) "[B" else "[C", intSize, OFFSET_STRING_VALUE)
        gIndex.stringInstanceSize = gIndex.getInstanceSize(string)

        hIndex.registerSuperClass(accessibleObject, object0)
        hIndex.registerSuperClass(field, accessibleObject)
        hIndex.registerSuperClass(executable, accessibleObject)
        hIndex.registerSuperClass(constructor, executable)
        hIndex.registerSuperClass(method, executable)
        hIndex.registerSuperClass(reader, object0)

        gIndex.getFieldOffset(system, "in", "java/io/InputStream", true)
        gIndex.getFieldOffset(system, "out", "java/io/PrintStream", true)
        gIndex.getFieldOffset(system, "err", "java/io/PrintStream", true)
        eq(throwable, "padding", "int", 0, objectOverhead) // for 64-bits :/
        eq(throwable, "detailMessage", string, intSize, OFFSET_THROWABLE_MESSAGE)
        eq(throwable, "stackTrace", "[$ste", intSize + ptrSize, OFFSET_THROWABLE_STACKTRACE)

        eq(ste, "lineNumber", "int", 0, OFFSET_STE_LINE)
        eq(ste, "declaringClass", string, intSize, OFFSET_STE_CLASS)
        eq(ste, "methodName", string, intSize + ptrSize, OFFSET_STE_METHOD)
        eq(ste, "fileName", string, intSize + 2 * ptrSize, OFFSET_STE_FILE)

        eq(clazz, "index", "int", 0, OFFSET_CLASS_INDEX)
        eq(clazz, "name", string, intSize, OFFSET_CLASS_NAME)
        eq(clazz, "simpleName", string, ptrSize + intSize, OFFSET_CLASS_SIMPLE_NAME)
        eq(clazz, "fields", "[$field", ptrSize * 2 + intSize, OFFSET_CLASS_FIELDS)
        eq(clazz, "methods", "[$method", ptrSize * 3 + intSize, OFFSET_CLASS_METHODS)
        eq(clazz, "constructors", "[$constructor", ptrSize * 4 + intSize, OFFSET_CLASS_CONSTRUCTORS)
        eq(clazz, "modifiers", "int", ptrSize * 5 + intSize, OFFSET_CLASS_MODIFIERS)

        // remove securityCheckCache and override, we don't need them
        eq(field, "slot", "int", 0, OFFSET_FIELD_SLOT)
        eq(field, "name", string, intSize, OFFSET_FIELD_NAME)
        eq(field, "type", clazz, ptrSize + intSize, OFFSET_FIELD_TYPE) // class of stored values
        eq(field, "clazz", clazz, 2 * ptrSize + intSize, OFFSET_FIELD_CLAZZ) // declaring class
        eq(field, "annotations", "[$annotation", 3 * ptrSize + intSize, OFFSET_FIELD_ANNOTATIONS)
        eq(field, "modifiers", "int", 4 * ptrSize + intSize, OFFSET_FIELD_MODIFIERS)

        eq(method, "slot", "int", 0, OFFSET_METHOD_SLOT)
        eq(method, "name", string, intSize, OFFSET_METHOD_NAME)
        eq(method, "returnType", clazz, ptrSize + intSize, OFFSET_METHOD_RETURN_TYPE)
        eq(method, "parameterTypes", "[$clazz", ptrSize * 2 + intSize, OFFSET_METHOD_PARAMETER_TYPES)
        eq(method, "callSignature", string, ptrSize * 3 + intSize, OFFSET_METHOD_CALL_SIGNATURE)
        eq(method, "clazz", clazz, ptrSize * 4 + intSize, OFFSET_METHOD_DECLARING_CLASS)
        eq(method, "modifiers", "int", ptrSize * 5 + intSize, OFFSET_METHOD_MODIFIERS)

        eq(constructor, "slot", "int", 0, OFFSET_CONSTRUCTOR_SLOT)
        eq(constructor, "parameterTypes", "[$clazz", intSize, OFFSET_CONSTRUCTOR_PARAMETER_TYPES)
        eq(constructor, "callSignature", string, ptrSize + intSize, OFFSET_CONSTRUCTOR_CALL_SIGNATURE)
        eq(constructor, "clazz", clazz, ptrSize * 2 + intSize, OFFSET_CONSTRUCTOR_DECLARING_CLASS)
        eq(constructor, "modifiers", "int", ptrSize * 3 + intSize, OFFSET_CONSTRUCTOR_MODIFIERS)

        eq(reader, "lock", object0, 0, OFFSET_READER_LOCK)

        // for sun/misc
        gIndex.getFieldOffset(thread, "threadLocalRandomSeed", "long", false)
        gIndex.getFieldOffset(thread, "threadLocalRandomSecondarySeed", "long", false)
        gIndex.getFieldOffset(thread, "threadLocalRandomProbe", "int", false)

        // reduce number of requests to <clinit> (was using 11% CPU time according to profiler)
        hIndex.finalFields[FieldSig("jvm/JVMShared", "objectOverhead", "int", true)] = objectOverhead
        hIndex.finalFields[FieldSig("jvm/JVMShared", "arrayOverhead", "int", true)] = arrayOverhead
        hIndex.finalFields[FieldSig("jvm/JVMShared", "trackAllocations", "boolean", true)] = trackAllocations
        hIndex.finalFields[FieldSig("jvm/JVMFlags", "ptrSize", "int", true)] = ptrSize
        hIndex.finalFields[FieldSig("jvm/JVMFlags", "ptrSizeBits", "int", true)] = ptrSizeBits

        eq(gIndex.getInterfaceIndex(InterfaceSig.c(STATIC_INIT, voidDescriptor)), 0)
        gIndex.getFieldOffset(constructor, "clazz", clazz, false)

    }
}