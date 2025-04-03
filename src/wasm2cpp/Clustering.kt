package wasm2cpp

import highlevel.HighLevelInstruction
import me.anno.utils.structures.lists.Lists.sumOfInt
import me.anno.utils.types.Strings.isBlank2
import wasm.instr.*
import wasm.parser.FunctionImpl
import kotlin.math.max

class Clustering(val functions: List<FunctionImpl>, var complexity: Int) {
    var imports: Collection<FunctionImpl> = emptySet()

    override fun toString(): String {
        return "{ $complexity, ${functions.size}x }"
    }

    companion object {

        fun splitFunctionsIntoClusters(functions: List<FunctionImpl>, numClusters: Int): List<Clustering> {

            if (numClusters > 1 && disableClustersForInspection) {
                val result = ArrayList<Clustering>(numClusters)
                val clustering = Clustering(functions, 1)
                result.add(clustering)

                findDependencies(functions, result)

                val emptyCluster = Clustering(ArrayList(0), 0)
                while (result.size < numClusters) {
                    result.add(emptyCluster)
                }
                return result
            }

            val root = Cluster(null)
            for (function in functions) {
                val funcName = function.funcName
                val firstUpper = funcName.indexOfFirst { it in 'A'..'Z' }
                val firstValid = when {
                    funcName.startsWith("static_") -> "static_".length
                    funcName.startsWith("new_") -> "new_".length
                    funcName.startsWith("tree_") -> "tree_".length
                    else -> 0
                }
                val path = funcName
                    .substring(firstValid, max(firstUpper - 1, firstValid))
                    .split('_')
                val cluster = getCluster(root, path)
                cluster.functions.add(function)
                addComplexity(cluster, getComplexity(function))
            }

            val targetComplexity = root.complexity / (2 * numClusters)
            val result = ArrayList<Clustering>(numClusters * 2)
            collectNodes(root, targetComplexity, result)
            findDependencies(functions, result)

            while (result.size > numClusters) {
                val smallest = result.minBy { it.complexity }
                result.remove(smallest)
                val secondSmallest = result.minBy { it.complexity }
                (secondSmallest.functions as ArrayList).addAll(smallest.functions)
                (secondSmallest.imports as HashSet<FunctionImpl>).addAll(smallest.imports)
            }

            while (result.size < numClusters) {
                result.add(Clustering(ArrayList(0), 0))
            }

            return result
        }

        private fun findDependencies(functions: List<FunctionImpl>, clusters: List<Clustering>) {
            val funcByName = functions.associateBy { it.funcName }
            for (cluster in clusters) {
                val usedClusters = HashSet<FunctionImpl>()
                for (func in cluster.functions) {
                    findDependencies(func.body, funcByName, usedClusters)
                }
                cluster.imports = usedClusters
            }
        }

        private fun findDependencies(
            instr: Instruction,
            funcByName: Map<String, FunctionImpl>,
            dst: HashSet<FunctionImpl>
        ) {
            when (instr) {
                is LoopInstr -> findDependencies(instr.body, funcByName, dst)
                is IfBranch -> {
                    findDependencies(instr.ifTrue, funcByName, dst)
                    findDependencies(instr.ifFalse, funcByName, dst)
                }
                is SwitchCase -> {
                    for (case in instr.cases) {
                        findDependencies(case, funcByName, dst)
                    }
                }
                is Call -> {
                    val func = funcByName[instr.name]
                    if (func != null) dst.add(func)
                }
                is CallIndirect -> {}
                is HighLevelInstruction -> {
                    findDependencies(instr.toLowLevel(), funcByName, dst)
                }
            }
        }

        private fun findDependencies(
            instructions: List<Instruction>,
            funcByName: Map<String, FunctionImpl>,
            dst: HashSet<FunctionImpl>
        ) {
            for (instr in instructions) {
                findDependencies(instr, funcByName, dst)
            }
        }

        /**
         * build nice clusters from this one node
         * */
        private fun collectNodes(cluster: Cluster, targetComplexity: Int, dst: ArrayList<Clustering>) {

            if (cluster.complexity <= targetComplexity) {
                dst.add(Clustering(cluster.collect(ArrayList()), cluster.complexity))
                return
            }

            var selfComplexity = cluster.complexity
            var currentComplexity = 0
            var currentList = ArrayList<FunctionImpl>()
            for (child in cluster.children.values) {
                selfComplexity -= child.complexity
                if (child.complexity < targetComplexity) {
                    child.collect(currentList)
                    currentComplexity += child.complexity
                    if (currentComplexity > targetComplexity) {
                        dst.add(Clustering(currentList, currentComplexity))
                        currentList = ArrayList()
                        currentComplexity = 0
                    }
                } else {
                    // too big to handle -> split itself
                    collectNodes(child, targetComplexity, dst)
                }
            }

            currentList.addAll(cluster.functions)
            if (currentList.isNotEmpty()) {
                currentComplexity += selfComplexity
                val prevDst = dst.minByOrNull { it.complexity }
                if (prevDst != null && prevDst.complexity + currentComplexity <= targetComplexity * 3 / 2) {
                    (prevDst.functions as ArrayList).addAll(currentList)
                    prevDst.complexity += currentComplexity
                } else {
                    dst.add(Clustering(currentList, currentComplexity))
                }
            }
        }

        private fun getComplexity(function: FunctionImpl): Int {
            return getComplexity(function.body)
        }

        private fun getComplexity(instr: Instruction): Int {
            return when (instr) {
                is LoopInstr -> 2 + getComplexity(instr.body)
                is IfBranch -> 1 + getComplexity(instr.ifTrue) + getComplexity(instr.ifFalse)
                is SwitchCase -> 5 + instr.cases.sumOfInt { getComplexity(it) }
                else -> 1
            }
        }

        private fun getComplexity(instr: List<Instruction>): Int {
            return instr.sumOfInt(::getComplexity)
        }

        private fun addComplexity(node0: Cluster, complexity: Int) {
            var node = node0
            while (true) {
                node.complexity += complexity
                node = node.parent ?: break
            }
        }

        private class Cluster(val parent: Cluster?) {

            val children = HashMap<String, Cluster>()
            val functions = ArrayList<FunctionImpl>()
            var complexity = 0

            fun collect(dst: ArrayList<FunctionImpl>): ArrayList<FunctionImpl> {
                dst.addAll(functions)
                for (child in children.values) {
                    child.collect(dst)
                }
                return dst
            }
        }

        private fun getCluster(root: Cluster, path: List<String>): Cluster {
            var node = root
            for (i in 0 until path.lastIndex) {
                val name = path[i]
                if (name.isBlank2()) continue
                node = node.children.getOrPut(name) { Cluster(node) }
            }
            return node
        }

    }

}
