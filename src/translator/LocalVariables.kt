package translator

import me.anno.utils.assertions.assertTrue
import utils.Builder
import utils.Descriptor
import utils.MethodSig
import utils.WASMTypes.i32
import utils.ptrType
import wasm.instr.Const
import wasm.instr.Instruction

class LocalVariables {

    // name,type -> newName, because types can change in JVM, but they can't in WASM
    private val localVars = HashMap<Pair<Int, String>, String>()
    val localVariables1 = ArrayList<LocalVariableOrParam>()
    val localVarsWithParams = ArrayList<LocalVariableOrParam>()
    val localVarInfos = HashSet<LocalVarInfo>()
    val parameterByIndex = ArrayList<LocalVariableOrParam?>()

    val tmpI32 by lazy { defineLocalVar(i32, "?") }

    fun defineParamVariables(clazz: String, descriptor: Descriptor, isStatic: Boolean) {
        // special rules in Java:
        // double and long use two slots in localVariables
        var idx = 0
        if (!isStatic) {
            val localVar = LocalVariableOrParam(clazz, ptrType, "self", 0, true)
            localVarsWithParams.add(localVar)
            parameterByIndex.add(localVar)
            idx++
        }
        for (i in descriptor.params.indices) {
            val jvmType = descriptor.params[i]
            val wasmType = descriptor.wasmParams[i]
            val k = idx++
            val localVar = LocalVariableOrParam(jvmType, wasmType, "param$k", k, true)
            localVarsWithParams.add(localVar)
            parameterByIndex.add(localVar)
            if (jvmType == "double" || jvmType == "long") {
                parameterByIndex.add(null) // "skip" a slot
            }
        }
    }

    fun addLocalVariable(name: String, type: String, descriptor: String): LocalVariableOrParam {
        assertTrue(localVariables1.none { it.wasmName == name }) { "Duplicate variable $name" }
        val localVar = LocalVariableOrParam(descriptor, type, name, -1000 - localVarsWithParams.size, false)
        localVariables1.add(localVar)
        localVarsWithParams.add(localVar)
        return localVar
    }

    private val stackVariables = HashSet<String>()
    fun getStackVarName(i: Int, type: String): String {
        val name = "s$i$type"
        if (stackVariables.add(name)) {
            addLocalVariable(name, type, "?")
        }
        return name
    }

    fun initializeLocalVariables(printer: Builder) {
        val instructions = ArrayList<Instruction>(localVariables1.size * 2)
        for (variable in localVariables1) {
            instructions.add(Const.zero[variable.wasmType]!!)
            instructions.add(variable.localSet)
        }
        printer.prepend(instructions)
    }

    fun findOrDefineLocalVar(i: Int, wasmType: String, descriptor: String): LocalVariableOrParam {
        return localVarsWithParams.firstOrNull { it.index == i && it.wasmType == wasmType }
            ?: defineLocalVar(i, wasmType, descriptor)
    }

    private var nextLocalVar = -1
    fun defineLocalVar(wasmType: String, descriptor: String): LocalVariableOrParam {
        val i = nextLocalVar--
        return defineLocalVar(i, wasmType, descriptor)
    }

    fun defineLocalVar(i: Int, wasmType: String, descriptor: String): LocalVariableOrParam {
        // todo this will always be "put"
        val wasmName = localVars.getOrPut(Pair(i, wasmType)) {
            // register local variable
            val name2 = "l${localVars.size}"
            name2
        }
        val v = LocalVariableOrParam(descriptor, wasmType, wasmName, i, false)
        localVarsWithParams.add(v)
        localVariables1.add(v)
        return v
    }

    fun renameLocalVariables(
        sig: MethodSig, nodes: List<TranslatorNode>,
        numLabels: Int
    ) {
        // todo if a slot is used multiple times, we can replace a variable partially:
        //  for that, we need the order
        //  can we assume that the TranslatorNode-order is correct???
        //  if so, label -> TranslatorNodeIndex, and that can be used for order-comparisons
        val localVarsByIndex = localVarInfos
            .groupBy { it.index }
        if (localVarInfos.size > 1 &&
            localVarsByIndex.any { it.value.size > 1 }
        ) {
            println(sig)
            println(localVariables1.map { "${it.wasmType} ${it.wasmName}" })
            println("nodes: ${nodes.size}, labels: $numLabels")
            for ((idx, group) in localVarsByIndex
                .entries.sortedBy { it.key }) {
                println("idx[$idx]:")
                for (entry in group) {
                    println("  $entry")
                }
            }
            // TODO()
        }
    }

}