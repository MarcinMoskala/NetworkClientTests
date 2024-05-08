import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.measureTime

fun main() = runBlocking {
    testClient(clients.random()) // Warmup

    repeat(10) {
        println("Round ${it + 1}")
        println(
            "Client".padEnd(15) +
                    "Threads".padEnd(15) +
                    "Active".padEnd(15) +
                    "ActiveAndNew".padEnd(15) +
                    "ActiveBefore".padEnd(15) +
                    "Time".padEnd(15)
        )
        for (client in clients.shuffled()) {
            testClient(client)
        }
    }
}

suspend fun testClient(client: ClientToTest) {
    val finished = AtomicInteger(0)
    val threadsActiveBefore = Thread.getAllStackTraces().keys.filter { it.isAlive }.toSet()
    val threadRaport: ThreadInfo
    val requestsToStart = 1000
    client.start(requestsToStart)
    measureTime {
        coroutineScope {
            repeat(requestsToStart) {
                launch {
                    client.request(2000, it)
                    finished.incrementAndGet()
                }
            }
            delay(1000)

            threadRaport = threadRaport(threadsActiveBefore)
            require(finished.get() == 0)
        }
        require(finished.get() == requestsToStart) { "All coroutines should be finished, but finished only ${finished.get()} for ${client.name}" }
    }.let { time ->
        val clientName = client.name.plus(":").padEnd(15)
        val all = threadRaport.all.toString().padEnd(15)
        val active = threadRaport.active.toString().padEnd(15)
        val activeAndNew = threadRaport.activeAndNew.toString().padEnd(15)
        val activeBefore = threadRaport.activeBefore.toString().padEnd(15)
        val time = time.toString().padEnd(15)
        client.close()
        restartCoroutineDispatchers()
        forceGC()
        val threadsAfterRestart = Thread.getAllStackTraces().keys.filter { it.isAlive }
        val threadsAfterRestartCount = threadsAfterRestart.count().toString().padEnd(15)
        println("$clientName$all$active$activeAndNew$activeBefore$time$threadsAfterRestartCount   $threadsAfterRestart")
    }
}

// Hack
suspend fun restartCoroutineDispatchers() {
    // Restart Dispatchers from Kotlin Coroutines
    val DefaultScheduler = Dispatchers.Default
    val members = DefaultScheduler::class.members
    members.find { it.name == "shutdown" && it.parameters.size == 2 }?.call(DefaultScheduler, 2000L)
    members.find { it.name == "restore" }?.call(DefaultScheduler)
    delay(2000)
}

fun forceGC() {
    System.gc()
    System.runFinalization()
    System.gc()
}

data class ThreadInfo(val all: Int, val active: Int, val activeAndNew: Int, val activeBefore: Int)

fun threadRaport(threadsActiveBefore: Set<Thread>): ThreadInfo {
    val activeAndNew = Thread.getAllStackTraces().keys.filter {
        it.isAlive &&
                it !in threadsActiveBefore &&
                it.stackTrace.firstOrNull()?.methodName != "park"
    }
    val allThreads = Thread.getAllStackTraces().keys
    val activeThreads = allThreads.filter { it.isAlive && it.stackTrace.firstOrNull()?.methodName != "park" }
    return ThreadInfo(
        all = allThreads.size,
        active = activeThreads.size,
        activeAndNew = activeAndNew.size,
        activeBefore = threadsActiveBefore.size
    )
}
