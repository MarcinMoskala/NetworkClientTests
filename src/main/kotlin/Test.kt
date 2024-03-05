import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.measureTime

fun main() =
    runBlocking {
        val finished = AtomicInteger(0)
        val threadsActiveBefore = Thread.getAllStackTraces().keys.filter { it.isAlive }.toSet()
        measureTime {
            coroutineScope {
                repeat(100) {
                    launch {
//                retrofitRequest(3_000, it) // 101, 3
//                ktorOkHttpRequest(3_000, it) // 106, 3
//                ktorCioRequest(3_000, it) // 6, 3
//                ktorApacheRequest(3_000, it) // 5, 3
//                ktorApache5Request(3_000, it) // 9, 3
//                ktorJavaRequest(3_000, it) // 6, 3
//                fuelRequest(3_000, it) // 106, 3
                        println("Finished $it")
                        finished.incrementAndGet()
                    }
                }
                delay(2000)

                Thread.getAllStackTraces().keys.filter {
                    it.isAlive &&
                            it !in threadsActiveBefore &&
                            it.stackTrace.firstOrNull()?.methodName != "park"
                }.let {
                    println("Active threads: ${it.size}")
                    println(it.map { it.name })
                }
                require(finished.get() == 0)
            }
        }.let { println("Took ${it.inWholeSeconds}") }
    }

