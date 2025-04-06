package utils

import gIndex
import hIndex
import hierarchy.Annota
import jvm.JVMShared.objectOverhead
import me.anno.io.Streams.writeLE32
import org.apache.logging.log4j.LogManager
import org.objectweb.asm.Opcodes.ACC_FINAL
import translator.GeneratorIndex.alignBuffer
import translator.LoadStoreHelper.getLoadCall
import utils.StaticClassIndices.OBJECT_ARRAY
import wasm.instr.Const.Companion.i32Const
import wasm.instr.Instructions.Return
import wasm.instr.ParamGet
import wasm.parser.FunctionImpl

object Annotations {

    private val LOGGER = LogManager.getLogger(Annotations::class)

    const val EXPORT_CLASS = "annotations/Export"
    const val JAVASCRIPT = "annotations/JavaScript"
    const val NO_INLINE = "annotations/NoInline"
    const val WASM = "annotations/WASM"
    const val USED_IF_DEFINED = "annotations/UsedIfIndexed"
    const val NO_THROW = "annotations/NoThrow"
    const val ALIAS = "annotations/Alias"
    const val REV_ALIAS = "annotations/RevAlias"

    private val annotationCache = HashMap<Annota, Int>()
    private val annotationListCache = HashMap<List<Annota>, Int>()
    private val annotationImplClasses = HashMap<String, String>()

    fun defineEmptyArray(ptr: Int) {
        annotationListCache[emptyList()] = ptr
    }

    fun getAnnotaImplClass(annotationClass: String): String {
        return annotationImplClasses.getOrPut(annotationClass) {
            val synthClassName = "$annotationClass\$Impl"
            hIndex.syntheticClasses.add(synthClassName)
            hIndex.superClass[synthClassName] = "java/lang/Object"
            hIndex.interfaces[synthClassName] = listOf(annotationClass)
            hIndex.childClasses.getOrPut(annotationClass, ::HashSet).add(synthClassName)
            hIndex.classFlags[synthClassName] = ACC_FINAL
            synthClassName
        }
    }

    private fun appendAnnotation(annota: Annota, indexStartPtr: Int, classData: ByteArrayOutputStream2): Int {
        return annotationCache.getOrPut(annota) {
            val className = annota.implClass
            val fields = gIndex.getFieldOffsets(className, false)
            val classId = gIndex.getClassId(className)
            if (fields.hasFields()) {
                alignBuffer(classData)
                val ptr = indexStartPtr + classData.size()
                val instanceSize = gIndex.getInstanceSize(className)
                classData.writeClass(classId)
                classData.fill(objectOverhead, instanceSize)
                for ((name, field) in fields.allFields()) {
                    // todo if not defined, use default value
                    val value = annota.properties[name] ?: continue // if not defined, just leave it null/0
                    val ptrI = ptr + field.offset - indexStartPtr
                    when (field.type) {
                        "java/lang/String" -> {
                            val str = gIndex.getString(value as String, indexStartPtr, classData)
                            classData.writePointerAt(str, ptrI)
                        }
                        "int" -> classData.writeLE32At(value as Int, ptrI)
                        "long" -> classData.writeLE64At(value as Long, ptrI)
                        "float" -> classData.writeLE32At(value as Float, ptrI)
                        "double" -> classData.writeLE64At(value as Double, ptrI)
                        else -> throw NotImplementedError("Unknown type for $annota, $className, $field, $value")
                    }
                }
                ptr
            } else {
                // append a pseudo-instance
                val classSize = gIndex.getInstanceSize("java/lang/Class")
                val classPtr = getClassInstancePtr(classId, indexStartPtr, classSize)
                classPtr + StaticFieldOffsets.OFFSET_CLASS_INDEX
            }
        }
    }

    fun appendAnnotations(annotations: List<Annota>, indexStartPtr: Int, classData: ByteArrayOutputStream2): Int {
        return annotationListCache.getOrPut(annotations) {
            val pointers = annotations.map { appendAnnotation(it, indexStartPtr, classData) }
            alignBuffer(classData)
            val ptr = indexStartPtr + classData.size()
            classData.writeClass(OBJECT_ARRAY)
            classData.writeLE32(pointers.size)
            for (i in pointers.indices) {
                classData.writePointer(pointers[i])
            }
            ptr
        }
    }

    fun indexFields(annotationClass: String, implClass: String) {
        val methods = hIndex.methodsByClass[annotationClass] ?: return
        for (method in methods) {
            if (method.descriptor.params.isNotEmpty()) {
                LOGGER.warn("Expected no params in annotation class $annotationClass")
                continue
            }

            val fieldType = method.descriptor.returnType
            if (fieldType == null) {
                LOGGER.warn("Expected returnType in annotation class $annotationClass")
                continue
            }

            val sig = method.withClass(implClass)
            hIndex.jvmImplementedMethods.add(sig)
            val fieldSig = FieldSig(implClass, method.name, fieldType, false)
            hIndex.getterMethods[sig] = fieldSig

            val fieldOffset = gIndex.getFieldOffset(implClass, method.name, fieldType, false)
                ?: throw IllegalStateException("Missing field $implClass.${method.name}: $fieldType")
            if (false) println("indexFields: $implClass.${method.name}, $fieldType: $fieldOffset")
        }
    }

    fun generateGetterMethods(annotationClass: String, implClass: String) {
        val methods = hIndex.methodsByClass[annotationClass] ?: return
        // todo how do we represent default values??? we must respect it when creating the instances...
        for (method in methods) {
            if (method.descriptor.params.isNotEmpty()) continue
            val fieldType = method.descriptor.returnType ?: continue

            val sig = method.withClass(implClass)
            val fieldOffset = gIndex.getFieldOffset(implClass, method.name, fieldType, false)
                ?: throw IllegalStateException("Missing field $implClass.${method.name}: $fieldType")

            val funcName = methodName2(sig)
            val selfParam = Param("self", "java/lang/Object", ptrTypeI)
            gIndex.translatedMethods[sig] = FunctionImpl(
                funcName, listOf(selfParam), listOf(fieldType),
                emptyList(), arrayListOf(
                    ParamGet(0, selfParam.name),
                    i32Const(fieldOffset),
                    getLoadCall(fieldType),
                    Return
                ),
                false
            )
        }
    }

}