package translator

import me.anno.utils.assertions.assertNull
import me.anno.utils.assertions.assertTrue
import me.anno.utils.types.Strings.toInt
import translator.JavaTypes.convertTypeToWASM
import translator.MethodTranslator.Companion.renameVariables
import utils.*
import utils.Param.Companion.getSampleJVMType
import wasm.instr.*
import wasm2cpp.language.HighLevelJavaScript.Companion.jsKeywords
import wasm2cpp.language.LowLevelCpp.Companion.cppKeywords
import kotlin.math.max

class LocalVariables {

    companion object {
        fun isKeyword(name: String): Boolean {
            return name in cppKeywords || name in jsKeywords
        }
    }

    // name,type -> newName, because types can change in JVM, but they can't in WASM
    val localVars = ArrayList<LocalVariableOrParam>()
    val localVarsAndParams = ArrayList<LocalVariableOrParam>()
    val localVarInfos = HashSet<LocalVarInfo>()
    val parameterByIndex = ArrayList<LocalVariableOrParam?>()
    private val localVarsLookup = ListMap<LocalVariableOrParam>()

    fun defineParamVariables(clazz: String, descriptor: Descriptor, isStatic: Boolean) {
        // special rules in Java:
        // double and long use two slots in localVariables
        var idx = 0
        if (!isStatic) {
            defineParamVariable(clazz, ptrTypeI, "self", 0)
            idx++
        }
        for (i in descriptor.params.indices) {
            val jvmType = descriptor.params[i]
            val wasmType = descriptor.wasmParams[i]
            val k = idx++
            defineParamVariable(jvmType, wasmType, "p$k", k)
            if (jvmType == "double" || jvmType == "long") {
                parameterByIndex.add(null) // "skip" a slot
            }
        }
    }

    private fun defineParamVariable(jvmType: String, wasmType: WASMType, name: String, k: Int) {
        val localVar = LocalVariableOrParam(jvmType, wasmType, name, k, true)
        localVarsAndParams.add(localVar)
        assertNull(localVarsLookup.set(getLookupIdx(k, wasmType), localVar))
        parameterByIndex.add(localVar)
    }

    fun addLocalVariable(name: String, wasmType: WASMType, jvmType: String): LocalVariableOrParam {
        return defineLocalVar(nextLocalVarIndex(), name, wasmType, jvmType)
    }

    fun addPrefixedLocalVariable(prefix: String, wasmType: WASMType, jvmType: String): LocalVariableOrParam {
        return defineLocalVar(prefix, nextLocalVarIndex(), wasmType, jvmType)
    }

    private val stackVariables = HashMap<String, LocalVariableOrParam>()
    fun getStackVarName(i: Int, jvmType: String): LocalVariableOrParam {
        val wasmType = convertTypeToWASM(jvmType)
        val name = "s$i$wasmType"
        return stackVariables.getOrPut(name) {
            addLocalVariable(name, wasmType, getSampleJVMType(wasmType))
        }
    }

    fun initializeLocalVariables(printer: Builder) {
        val instructions = ArrayList<Instruction>(localVars.size * 2)
        for (variable in localVars) {
            instructions.add(Const.zero[variable.wasmType]!!)
            instructions.add(variable.localSet)
        }
        printer.prepend(instructions)
    }

    private fun getLookupIdx(i: Int, wasmType: WASMType): Int {
        assertTrue(i >= 0)
        return i * WASMTypes.numWASMTypes + WASMTypes.getWASMTypeIndex(wasmType)
    }

    fun findOrDefineLocalVar(i: Int, wasmType: WASMType, descriptor: String): LocalVariableOrParam {
        return localVarsLookup[getLookupIdx(i, wasmType)]
            ?: defineLocalVar("l", i, wasmType, descriptor)
    }

    private var nextLocalVar = -1
    private fun nextLocalVarIndex(): Int {
        return nextLocalVar--
    }

    fun defineLocalVar(prefix: String, wasmType: WASMType, descriptor: String): LocalVariableOrParam {
        return defineLocalVar(prefix, nextLocalVarIndex(), wasmType, descriptor)
    }

    private fun defineLocalVar(prefix: String, i: Int, wasmType: WASMType, descriptor: String): LocalVariableOrParam {
        assertTrue(prefix.isNotEmpty())
        val prefix0 = prefix[0]
        assertTrue(prefix0 in 'A'..'Z' || prefix0 in 'a'..'z')
        val maxIndex = localVars.maxOfOrNull {
            if (it.name.startsWith(prefix)) {
                (it.name as CharSequence).toInt(prefix.length, it.name.length)
            } else -1
        } ?: -1
        val nextIndex = max(maxIndex + 1, 0)
        val wasmName = "$prefix$nextIndex"
        return defineLocalVar(i, wasmName, wasmType, descriptor)
    }

    private fun defineLocalVar(i: Int, name: String, wasmType: WASMType, descriptor: String): LocalVariableOrParam {
        assertTrue(localVars.none { it.name == name }) { "Duplicate variable $name" }
        val variable = LocalVariableOrParam(descriptor, wasmType, name, i, false)
        localVarsAndParams.add(variable)
        localVars.add(variable)
        if (i >= 0) {
            assertNull(localVarsLookup.set(getLookupIdx(i, wasmType), variable))
        }
        return variable
    }

    private fun sanitizeVariableName(name: String): String? {
        if (name.length > 32) return null // we want it readable
        if (name == "this") return "self"
        if (isKeyword(name)) return "_$name"
        // check if name is fine
        if (name.all { it in 'A'..'Z' || it in 'a'..'z' }) {
            return name
        }
        val builder = StringBuilder2()
        if (name.startsWith("tmp")) builder.append('_')
        if (name.startsWith("global_")) builder.append('_')
        for (char in name) {
            val char2 = when (char) {
                in 'A'..'Z', in 'a'..'z' -> char
                in '0'..'9' -> {
                    if (builder.length == 0) builder.append('_') // really should not happen
                    char
                }
                '$' -> {
                    // skip dollar at start
                    if (builder.length == 0) continue
                    'X'
                }
                '<', '>' -> continue // skip them
                else -> '_'
            }
            builder.append(char2)
        }
        if (builder.length == 0) return null
        val name1 = builder.toString()
        if (name1 == "this") return "self"
        if (isKeyword(name1) ||
            name1.startsWith("tmp") ||
            name1.startsWith("global_")
        ) return "_$name1"
        return name1
    }

    private fun validateRenamedVariablesAreNotUsed(nodes: List<TranslatorNode>, renamedVariables: Set<String>) {
        for (node in nodes) {
            for (instr in node.printer.instrs) {
                val name = when (instr) {
                    is LocalGet -> instr.name
                    is LocalSet -> instr.name
                    else -> continue
                }
                assertTrue(name !in renamedVariables) {
                    "$instr was renamed!, cannot still be used"
                }
            }
        }
    }

    fun renameLocalVariables(
        sig: MethodSig, nodes: List<TranslatorNode>,
        numLabels: Int
    ) {

        if (!renameVariables) return

        // todo if a slot is used multiple times, we can replace a variable partially:
        //  for that, we need the order
        //  can we assume that the TranslatorNode-order is correct???
        //  if so, label -> TranslatorNodeIndex, and that can be used for order-comparisons
        if (localVarInfos.isEmpty()) return
        val localVarsByIndex = localVarInfos
            .groupBy { it.index }

        val usedNames = HashSet<String>(localVarsAndParams.size + 16)
        for (v in localVarsAndParams) usedNames.add(v.name)
        // we must also avoid the names of any called function
        for (node in nodes) {
            for (inst in node.printer.instrs) {
                if (inst is Call) usedNames.add(inst.name)
            }
        }

        // add labels to usedNames to prevent using them twice
        // -> not possible here, we don't have usedNames yet

        val renamedVariables = HashMap<String, String>()
        for ((idx, group) in localVarsByIndex) {
            if (group.size == 1) {
                val varInfo = group.first()
                if (varInfo.name == null) continue
                val wasmType = varInfo.wasmType
                val variable = localVarsLookup[getLookupIdx(idx, wasmType)]
                if (variable != null) {
                    val oldName = variable.name
                    val newName = sanitizeVariableName(varInfo.name) ?: continue
                    if (newName == oldName) continue // weird coincidence
                    if (usedNames.add(newName)) {
                        variable.renameTo(newName)
                        if (!variable.isParam) {
                            renamedVariables[oldName] = newName
                        }
                    } // else find alternative names???
                } // else :/
            }
        }

        // validate renamed variables aren't used in their original form
        validateRenamedVariablesAreNotUsed(nodes, renamedVariables.keys)

        /*if (localVarsByIndex.any { it.value.size > 1 }) {
            println(sig)
            println(localVars.map { "${it.wasmType} ${it.name}" })
            println("nodes: ${nodes.size}, labels: $numLabels")
            for ((idx, group) in localVarsByIndex
                .entries.sortedBy { it.key }) {
                println("idx[$idx]:")
                for (entry in group) {
                    println("  $entry")
                }
            }
        }*/
    }

}