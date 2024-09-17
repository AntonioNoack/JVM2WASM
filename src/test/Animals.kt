@file:Suppress("unused")

package test

import jvm.JVM32.getNextPtr
import jvm.JVM32.log
import me.anno.animation.Interpolation
import me.anno.config.DefaultConfig
import me.anno.ecs.Entity
import me.anno.ecs.prefab.Prefab
import me.anno.engine.ECSRegistry
import me.anno.engine.ScenePrefab
import me.anno.gpu.texture.Clamping
import me.anno.io.saveable.SaveableArray
import me.anno.io.base.BaseReader.Companion.getNewClassInstance
import me.anno.io.utils.StringMap
import org.jbox2d.collision.shapes.ShapeType
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.World
import org.jbox2d.dynamics.contacts.Contact
import org.jbox2d.dynamics.contacts.ContactRegister
import org.jbox2d.pooling.IDynamicStack
import org.jbox2d.pooling.normal.DefaultWorldPool
import org.joml.Vector3i
import java.util.*

var ctr = 0

abstract class Animal {

    fun say(mode: Int) {

        /*println(toFixed2(0.1, 2))
        println(File("A/V").parent)
        println(File("A/V").parentFile)

        val absolutePath = "\\user\\virtual\\.config\\RemsEngine"
        val p0 = absolutePath.replace('/', '\\')
        println("p0: $p0")
        val p1 = absolutePath.replace('\\', '/')
        println("p1: $p1")

        val map = HashMap<String, Int>()
        println("created map")
        map[p0] = p0.hashCode()
        println("set p0")
        map[p1] = p1.hashCode()
        println("set p1")
        println(map)
        println(map.entries.joinToString())
        println("done")
        // println(saying())

        println(LastModifiedCache[absolutePath].exists)
*/

        // println(CharBuffer.allocate(16)[0])

        /**/

        /*when (mode) {
            -1 -> {
                val testMap = hashMapOf(
                    1 to "A",
                    2 to "V",
                    3 to "LOl",
                    15 to "xx"
                )

                for ((key, value) in testMap) {
                    println("$key: $value")
                    println("contains? ${key in testMap}")
                    println("contains? ${key in testMap.keys}")
                    println("contains? ${value in testMap.values}")
                    println("contains? ${key in testMap.entries.map { it.key }}")
                    println("contains? ${value in testMap.entries.map { it.value }}")
                    println("get? ${testMap[key]}")
                }
            }
            /*0 -> {
                registerCustomClass { StringMap() }
                registerCustomClass { SaveableArray() }
                registerCustomClass { StringMap() }
                registerCustomClass { SaveableArray() }
                println(getNewClassInstance(StringMap().className))
            }*/
            1 -> {
                println(DefaultConfig["x", 17])
            }
            /*2 -> {
                UIRegistry.init()
            }
            3 -> {
                ECSRegistry.init()
            }*/
            4 -> {
                // test EnumMap, it was broken
                val clazz = Clamping::class.java
                /*val access = SharedSecrets.getJavaLangAccess()
                println("access: $access")
                val constants = access.getEnumConstantsShared(clazz)
                println("constants: $constants")*/
                Clamping.CLAMP
                val instance = EnumMap<Clamping, Int>(clazz)
                println(instance)
            }
            6 -> {
                println(me.anno.utils.pooling.Stack {
                    println("creating a new stack")
                    ctr++
                }.borrow())
            }
            7 -> {
                val vs = Interpolation.values()
                for (v in vs) {
                    println("${v.name}: ${v.ordinal}")
                }
                val ws = IntArray(vs.size)
                for (i in vs.indices) {
                    ws[i] = vs[i].id
                }
                println(ws.joinToString())
            }
            8 -> {

                println("size: ${ShapeType.values().size}²")

                val contactStacks = AnimalsJava.create()

                fun addType(creator: IDynamicStack<Contact>, type1: ShapeType, type2: ShapeType) {
                    val register = ContactRegister()
                    register.creator = creator
                    register.primary = true
                    println("adding ${type1.ordinal},${type2.ordinal}, ${type1.name},${type2.name}")
                    contactStacks[type1.ordinal][type2.ordinal] = register
                    if (type1 != type2) {
                        val register2 = ContactRegister()
                        register2.creator = creator
                        register2.primary = false
                        contactStacks[type2.ordinal][type1.ordinal] = register2
                    }
                }

                val pool = DefaultWorldPool(100, 10)
                addType(pool.circleContactStack, ShapeType.CIRCLE, ShapeType.CIRCLE)
                addType(pool.polyCircleContactStack, ShapeType.POLYGON, ShapeType.CIRCLE)
                addType(pool.polyContactStack, ShapeType.POLYGON, ShapeType.POLYGON)
                addType(pool.edgeCircleContactStack, ShapeType.EDGE, ShapeType.CIRCLE)
                addType(pool.edgePolyContactStack, ShapeType.EDGE, ShapeType.POLYGON)
                addType(pool.chainCircleContactStack, ShapeType.CHAIN, ShapeType.CIRCLE)
                addType(pool.chainPolyContactStack, ShapeType.CHAIN, ShapeType.POLYGON)

                val world = World(Vec2())
                println(world)
            }
           /* 9 -> {
                registerCustomClass(Entity())
                ScenePrefab.prefab.value.getSampleInstance()
            }
            10 -> {
                Prefab("Entity").apply {

                    ensureMutableLists()

                    set("name", "Root")
                    set("isCollapsed", false)

                }
            }*/
            11 -> {
                val sample = Entity()
                sample.getReflections()
            }
            12 -> {
                CachedReflections(Entity::class)
            }
            13 -> {
                val f = 67f
                println("float: $f")
                println("toInt: ${f.toInt()}")
                println("double: ${f.toDouble()}")
                println("toLong: ${f.toLong()}")
                println("int bits: ${f.toBits()}, ${f.toBits().toUInt().toString(16)}")
                println("long bits: ${f.toDouble().toBits()}, ${f.toDouble().toBits().toULong().toString(16)}")
                val v = floatArrayOf(f)
                println(v[0])
                val w = doubleArrayOf(f.toDouble(), 0.0)
                w[1] = w[0]
                println(w[0])
                println(w[1])
            }
            14 -> {
                val mem0 = getNextPtr()
                var sum = 0
                for (i in 0 until 10000) {
                    sum += Vector3i(1, 2, 3).distance(3, 2, 1).toInt()
                }
                log("sum", sum)
                val mem1 = getNextPtr()
                log("allocated:", mem1 - mem0)
            }
            15 -> {
                val testMap = hashMapOf(1 to 2L, 2 to 4L, 4 to 7L)
                println(testMap.entries.joinToString())
                println(testMap.remove(1))
                println(testMap.remove(2))
                println(testMap.remove(3))
                println(testMap.entries.joinToString())
                println(testMap.put(3, 9L))
                println(testMap.entries.joinToString())
            }
            else -> log("Unknown test case!", mode)
        }*/
    }

    abstract fun saying(): String

}

open class Cat : Animal() {
    override fun saying() = "miau"
}

class Dog : Animal() {
    override fun saying() = "woff"
}

class Fish : Animal() {
    override fun saying() = "bloop"
}

class SiameseCat : Cat() {
    override fun saying() = super.saying() + " meoow"
}

class HashCat : Cat() {
    override fun saying() = listOf(1, 2, 3).withIndex().toHashSet().toString()
}