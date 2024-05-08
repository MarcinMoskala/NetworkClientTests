import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.measureTime

fun main() = runBlocking {
    testClient(KtorOkHttp) // This is to print an annoying warning about SLF4J
    println("Warmup")
    printHeader()
    for (client in clients) {
        testClient(client)
    }
    delay(1000)
    val accumulatedThreadReports = mutableMapOf<ClientToTest, ThreadInfo>()
    repeat(10) {
        println("Round ${it + 1}")
        printHeader()
        for (client in clients.shuffled()) {
            val result = testClient(client)
            accumulatedThreadReports[client] = (accumulatedThreadReports[client] ?: ThreadInfo(0, 0, 0, 0)) + result
        }
    }
    println("Averages")
    printHeader()
    clients.forEach { client ->
        printThreadInfo(client, accumulatedThreadReports[client]!! / 10)
    }
}

suspend fun testClient(client: ClientToTest): ThreadInfo {
    val finished = AtomicInteger(0)
    val threadsActiveBefore = Thread.getAllStackTraces().keys.filter { it.isActive() }.toSet()
    var threadRaport: ThreadInfo
    val requestsToStart = 500
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
        threadRaport = threadRaport.copy(time = time.inWholeMilliseconds)
        printThreadInfo(
            client,
            threadRaport,
            close = true,
            threadsBefore = threadsActiveBefore
        )
    }
    return threadRaport
}

fun printHeader() {
    buildString {
        append("Client".padEnd(15))
        append("ActiveAndNew".padEnd(15))
        append("Active".padEnd(15))
        append("Threads".padEnd(15))
        append("ActiveBefore".padEnd(15))
        append("ActiveAfter".padEnd(15))
        append("Time".padEnd(15))
    }.let(::println)
}

suspend fun printThreadInfo(
    client: ClientToTest,
    threadRaport: ThreadInfo,
    close: Boolean = false,
    threadsBefore: Set<Thread>? = null
) {
    val clientName = client.name.plus(":").padEnd(15)
    val all = threadRaport.all.toString().padEnd(15)
    val active = threadRaport.active.toString().padEnd(15)
    val activeAndNew = threadRaport.activeAndNew.toString().padEnd(15)
    val activeBefore = threadRaport.activeBefore.toString().padEnd(15)
    val time = threadRaport.time.toString().padEnd(15)
    if (close) {
        client.close()
        restartCoroutineDispatchers()
        forceGC()
        delay(1000)
        val threadsAfterRestart = Thread.getAllStackTraces().keys.filter { it.isActive() }
        val threadsAfterRestartCount = threadsAfterRestart.count().toString().padEnd(15)
        val newThreads = threadsAfterRestart - (threadsBefore ?: emptySet())
        println("$clientName$activeAndNew$active$all$activeBefore$threadsAfterRestartCount$time   $newThreads")
    } else {
        println("$clientName$activeAndNew$active$all$activeBefore$time")
    }
}

// Hack
suspend fun restartCoroutineDispatchers() {
    // Restart Dispatchers from Kotlin Coroutines
    val DefaultScheduler = Dispatchers.Default
    val members = DefaultScheduler::class.members
    members.find { it.name == "shutdown" && it.parameters.size == 2 }?.call(DefaultScheduler, 2000L)
    members.find { it.name == "restore" }?.call(DefaultScheduler)
}

fun forceGC() {
    System.gc()
    System.runFinalization()
    System.gc()
}

data class ThreadInfo(val all: Int, val active: Int, val activeAndNew: Int, val activeBefore: Int, var time: Long = 0) {
    operator fun plus(other: ThreadInfo) = ThreadInfo(
        all = all + other.all,
        active = active + other.active,
        activeAndNew = activeAndNew + other.activeAndNew,
        activeBefore = activeBefore + other.activeBefore,
        time = time + other.time
    )

    operator fun div(divider: Int) = ThreadInfo(
        all = all / divider,
        active = active / divider,
        activeAndNew = activeAndNew / divider,
        activeBefore = activeBefore / divider,
        time = time / divider
    )
}

fun threadRaport(threadsActiveBefore: Set<Thread>): ThreadInfo {
    val activeAndNew = Thread.getAllStackTraces().keys.filter {
        it.isActive() && it !in threadsActiveBefore
    }
    val allThreads = Thread.getAllStackTraces().keys
    val activeThreads = allThreads.filter { it.isActive() }
    return ThreadInfo(
        all = allThreads.size,
        active = activeThreads.size,
        activeAndNew = activeAndNew.size,
        activeBefore = threadsActiveBefore.size
    )
}

fun Thread.isActive() = isAlive && !isInterrupted && stackTrace.firstOrNull()?.methodName != "park"
