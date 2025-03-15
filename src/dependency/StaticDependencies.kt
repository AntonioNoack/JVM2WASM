package dependency

import dIndex
import dependency.DependencyIndex.getterDependencies
import dependency.DependencyIndex.interfaceDependencies
import dependency.DependencyIndex.methodDependencies
import dependency.DependencyIndex.setterDependencies
import hIndex
import me.anno.utils.assertions.assertTrue
import me.anno.utils.structures.Recursion
import me.anno.utils.structures.lists.TopologicalSort
import org.apache.logging.log4j.LogManager
import utils.FieldSig
import utils.MethodSig
import utils.STATIC_INIT

object StaticDependencies {

    private val LOGGER = LogManager.getLogger(StaticDependencies::class)

    @JvmStatic
    fun main(args: Array<String>) {
        val graph = mapOf(
            0 to setOf(),
            1 to setOf(0),
            2 to setOf(1),
            3 to setOf(2, 3), // a pseudo-cycle,
            4 to setOf(1, 2, 3),
            5 to setOf(4, 7), // actual cycle, should come last
            6 to setOf(5),
            7 to setOf(6),
            8 to setOf(8), // independent pseudo-cycle
        )
        println(partiallySortByDependencies(graph.keys.shuffled(), graph, true))
    }

    fun calculatePartialStaticCallOrder(): List<MethodSig> {
        val staticMethods = findAllUsedStaticMethods()
        val dependencies = staticMethods.associateWith(::findAllStaticDependencies)
        return partiallySortByDependencies(staticMethods, dependencies, true)
    }

    fun <K, V, DstK : MutableCollection<K>> Map<K, Collection<V>>.transpose(createDstK: () -> DstK): Map<V, DstK> {
        val result = HashMap<V, DstK>(size)
        for ((key, values) in this) {
            for (value in values) {
                result
                    .getOrPut(value, createDstK)
                    .add(key)
            }
        }
        return result
    }

    fun <V> partiallySortByDependencies(
        nodes: List<V>, dependencies: Map<V, Set<V>>,
        minimizeNumCycleBreaks: Boolean
    ): List<V> {

        val mutableDependencies = dependencies
            .mapValues { (node, dependenciesI) ->
                HashSet(dependenciesI).apply { remove(node) }
            }

        val reverseDependencies = mutableDependencies
            .transpose(::HashSet)

        fun hasNoDependencies(node: V): Boolean {
            return mutableDependencies[node]?.isEmpty() ?: true
        }

        val sortedList = ArrayList<V>(nodes.size)
        val unsortedNodes = HashSet<V>(nodes)

        val nodesWithoutDependencies = ArrayDeque<V>()
        fun removeNodeFromGraph(dependency: V) {

            if (!unsortedNodes.remove(dependency)) {
                // todo this really should not happen...
                //  how can a node enter this function twice???
                return
            }
            sortedList.add(dependency)

            val revDependencies = reverseDependencies[dependency] ?: return
            for (node in revDependencies) {
                val dependencies1 = mutableDependencies[node]!!
                assertTrue(dependencies1.remove(dependency))
                if (dependencies1.isEmpty()) {
                    nodesWithoutDependencies.add(node)
                }
            }
        }

        for (node in nodes) {
            if (hasNoDependencies(node)) {
                removeNodeFromGraph(node)
            }
        }

        fun numRevDependencies(node: V): Int {
            return reverseDependencies[node]?.size ?: 0
        }

        var numCycles = 0
        while (unsortedNodes.isNotEmpty()) {
            if (nodesWithoutDependencies.isNotEmpty()) {
                val node = nodesWithoutDependencies.removeFirst()
                assertTrue(hasNoDependencies(node))
                removeNodeFromGraph(node)
            } else {
                // the remainder has cycles... break them arbitrarily
                val cycleNode = if (minimizeNumCycleBreaks) {
                    unsortedNodes.maxBy(::numRevDependencies)
                } else unsortedNodes.first()
                println("Breaking cycle on $cycleNode (${numRevDependencies(cycleNode)})")
                removeNodeFromGraph(cycleNode)
                numCycles++
            }
        }

        println("Partially sorted, found $numCycles cycle(s)")

        return sortedList
    }

    fun calculateStaticCallOrder(): List<MethodSig> {

        // todo given all used static-init methods,
        //  find a valid order of initialization, so we can initialize it once, and
        //  just assume it was initialized later.

        val staticMethods = findAllUsedStaticMethods()
        val dependencies = staticMethods.associateWith(::findAllStaticDependencies)

        val sortResult = sortByDependency(staticMethods, dependencies)
        if (sortResult.solution != null) {
            LOGGER.info("Found valid static sorting!")
            return sortResult.solution
        }

        LOGGER.error("Cycle:")
        val cycle = sortResult.cycle!!
        for (i in cycle.indices) {
            val from = cycle[i]
            val to = cycle[(i + 1) % cycle.size]
            val path = findDependencyPath(from, to) {
                val tmp = ArrayList<MethodSig>()
                addMethodDependencies(from, it, tmp)
                tmp
            }
            val fromAlias = hIndex.getAlias(from)
            LOGGER.error(if (fromAlias != from) "  $from ($fromAlias)" else "  $from")
            for (pathI in path) {
                val pathIAlias = hIndex.getAlias(pathI)
                LOGGER.error(if (pathI != pathIAlias) "  -> $pathI ($pathIAlias)" else "  -> $pathI")
            }
        }
        //throw IllegalStateException("Static-Init isn't sortable")
        return emptyList()
    }

    class SortResult<V>(val solution: List<V>?, val cycle: List<V>?)

    private fun <V : Any> sortByDependency(list: List<V>, dependencies: Map<V, Set<V>>): SortResult<V> {
        val sorter = object : TopologicalSort<V, ArrayList<V>>(ArrayList(list)) {
            override fun visitDependencies(node: V): Boolean {
                return dependencies[node]!!.any { visit(it) }
            }
        }
        val solution = sorter.finish(false)
        if (solution != null) return SortResult(solution, null)
        return SortResult(null, sorter.findCycle())
    }

    private fun isStaticInit(sig: MethodSig): Boolean {
        return sig.name == STATIC_INIT
    }

    private fun findAllUsedStaticMethods(): List<MethodSig> {
        return dIndex.usedMethods.filter(::isStaticInit)
    }

    private fun findAllStaticDependencies(sig: MethodSig): Set<MethodSig> {
        return findAllDependenciesEndAtStatic(sig).filter(::isStaticInit).toSet()
    }

    private fun findAllDependenciesEndAtStatic(sig0: MethodSig): Set<MethodSig> {
        val solution = Recursion.collectRecursive(sig0) { sigI, remaining ->
            addMethodDependencies(sig0, sigI, remaining)
        }
        solution.remove(sig0)
        return solution
    }

    private fun addMethodDependencies(sig0: MethodSig, sigI: MethodSig, remaining: ArrayList<MethodSig>) {
        if (sigI == sig0 || !isStaticInit(sigI)) {
            val sig = hIndex.getAlias(sigI)
            remaining.addAll(methodDependencies[sig] ?: emptySet())
            fun handleField(field: FieldSig) {
                // filter them for static fields
                if (field.isStatic && !field.isFinal) { // todo do we need a differentiation like in DependencyIndex??
                    remaining.add(MethodSig.staticInit(field.clazz))
                }
            }
            for (field in (getterDependencies[sig] ?: emptySet())) {
                handleField(field)
            }
            for (field in (setterDependencies[sig] ?: emptySet())) {
                handleField(field)
            }
            // constructorDependencies[sig] -> doesn't matter
            // todo we could find a new subset of "constructable" classes, where we only consider
            //  the static entry points, not the others...
            interfaceDependencies[sig]
        }
    }

    private fun <V : Any> findDependencyPath(from: V, to: V, getDependencies: (V) -> Collection<V>): List<V> {

        if (from == to) return emptyList()

        val paths = HashMap<V, V>()
        val sources = ArrayList<V>()
        sources.add(from)

        val remaining = HashSet<V>()
        while (true) {
            if (sources.isEmpty()) {
                throw IllegalStateException("Path couldn't be found")
            }

            // run this turn
            for (i in sources.indices) {
                val fromI = sources[i]
                val dependencies = getDependencies(fromI)
                if (to in dependencies) {
                    paths[to] = fromI
                    return backtrack(from, to, paths)
                }
                // add all dependencies
                for (middleI in dependencies) {
                    if (middleI !in paths) {
                        paths[middleI] = fromI
                        remaining.add(middleI)
                    }
                }
            }

            // prepare next turn
            sources.clear()
            sources.addAll(remaining)
            remaining.clear()
        }
    }

    private fun <V> backtrack(from: V, to: V, paths: Map<V, V>): List<V> {
        val path = ArrayList<V>()
        var node = to
        while (node != from) {
            if (node != to) path.add(node)
            node = paths[node]!!
        }
        path.reverse()
        return path
    }

    // todo it would be also very interesting to run static-init at compile time:
    //  - how long does this take? (interpreted/JIT-ed)
    //  - how much extra memory does it produce?
    //  the bonus would be
    //  - less code in the resulting .exe,
    //  - extra memory, that is allocated anyway, not needing GC, potentially thousands of instances,

}