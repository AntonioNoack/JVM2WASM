package wasm2cpp

import dependency.StaticDependencies.transpose
import me.anno.utils.Clock
import org.apache.logging.log4j.LogManager
import wasm.parser.FunctionImpl

/**
 * Find functions, which can be called out of order, because they are just reading memory or globals
 * */
class PureFunctions(
    private val imports: List<FunctionImpl>,
    private val functions: List<FunctionImpl>,
    private val functionByName: Map<String, FunctionImpl>
) {

    companion object {
        private val LOGGER = LogManager.getLogger(PureFunctions::class)
    }

    fun findPureFunctions(): Set<String> {

        val clock = Clock("PureFunctions")
        val impureFunctions = ArrayList<String>(functions.size)
        for (i in imports.indices) {
            impureFunctions.add(imports[i].funcName)
        }
        clock.stop("Init")

        val pureFunctions = HashSet<String>()
        val dependencies = HashMap<String, Collection<String>>(functions.size)
        val detector = MayBePureDetector(functionByName, HashSet())
        for (i in functions.indices) {
            val func = functions[i]
            detector.canBePure = true
            detector.process(func)
            val mayBePure = detector.canBePure
            val name = func.funcName
            if (mayBePure) pureFunctions.add(name)
            else impureFunctions.add(name)
            if (detector.dst.isNotEmpty()) {
                dependencies[name] = detector.dst
                detector.dst = HashSet()
            }
        }
        clock.stop("MayBePure")

        // recursively remove calls to non-pure functions
        val calledBy = dependencies.transpose(::HashSet)
        while (true) {
            val impure = impureFunctions.removeLastOrNull() ?: break
            val called = calledBy[impure] ?: continue
            for (calledI in called) {
                if (pureFunctions.remove(calledI)) {
                    // new impure function was found
                    impureFunctions.add(calledI)
                }
            }
        }
        clock.stop("Recursion")

        LOGGER.info("Pure functions: ${pureFunctions.size}/(${functions.size}+${imports.size})")
        return pureFunctions

    }
}