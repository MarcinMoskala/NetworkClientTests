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
//    printHeader()
//    for (client in clients) {
//        testClient(client)
//    }
    delay(1000)
    val accumulatedThreadReports = mutableMapOf<ClientToTest, ThreadInfo>()
    repeat(5) {
        println("Round ${it + 1}")
        printHeader()
        for (client in clients.shuffled()) {
            val result = testClient(client)
            accumulatedThreadReports[client] = (accumulatedThreadReports[client] ?: ThreadInfo(0, 0, 0, 0, 0, 0)) + result
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
    val threadsActiveBefore = Thread.getAllStackTraces().keys.filter { it.isActiveAndUnparked() }.toSet()
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
            // TO TEST IF Dispatchers.IO IS BLOCKED
            delay(500)
            repeat(150 * 64) {
                launch(Dispatchers.IO) {
                    Thread.sleep(10)
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
        append("ActiveUnparkedNew".padEnd(20))
        append("ActiveUnparked".padEnd(20))
        append("ActiveNew".padEnd(15))
        append("Active".padEnd(15))
        append("All".padEnd(15))
        append("Time".padEnd(15))
        append("Threads before".padEnd(15))
    }.let(::println)
}

suspend fun printThreadInfo(
    client: ClientToTest,
    threadRaport: ThreadInfo,
    close: Boolean = false,
    threadsBefore: Set<Thread>? = null
) = buildString {
    append(client.name.plus(":").padEnd(15))
    append(threadRaport.activeUnparkedNew.toString().padEnd(20))
    append(threadRaport.activeUnparked.toString().padEnd(20))
    append(threadRaport.activeNew.toString().padEnd(15))
    append(threadRaport.active.toString().padEnd(15))
    append(threadRaport.all.toString().padEnd(15))
    append(threadRaport.time.toString().padEnd(15))
    if (close) {
        client.close()
        restartCoroutineDispatchers()
        forceGC()
        delay(1000)
        val unparkedBefore = threadsBefore.orEmpty().filter { it.isActiveAndUnparked() }
        val threadsAfterRestart = Thread.getAllStackTraces().keys.filter { it.isActiveAndUnparked() }
        val threadsAfterRestartCount = threadsAfterRestart.count().toString().padEnd(15)
        append(("${threadRaport.activeBefore} -> $threadsAfterRestartCount").padEnd(15))
        val newThreads = threadsAfterRestart - unparkedBefore
        if (newThreads.isNotEmpty()) {
            append("New threads: $newThreads")
        }
    } else {
        append(("${threadRaport.activeBefore}").padEnd(15))
    }
}.let(::println)

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

data class ThreadInfo(
    val all: Int,
    val active: Int,
    val activeNew: Int,
    val activeUnparkedNew: Int,
    val activeUnparked: Int,
    val activeBefore: Int,
    var time: Long = 0
) {
    operator fun plus(other: ThreadInfo) = ThreadInfo(
        all = all + other.all,
        active = active + other.active,
        activeNew = activeNew + other.activeNew,
        activeBefore = activeBefore + other.activeBefore,
        activeUnparkedNew = activeUnparkedNew + other.activeUnparkedNew,
        activeUnparked = activeUnparked + other.activeUnparked,
        time = time + other.time
    )

    operator fun div(divider: Int) = ThreadInfo(
        all = all / divider,
        active = active / divider,
        activeNew = activeNew / divider,
        activeBefore = activeBefore / divider,
        activeUnparkedNew = activeUnparkedNew / divider,
        activeUnparked = activeUnparked / divider,
        time = time / divider
    )
}

fun threadRaport(threadsActiveBefore: Set<Thread>): ThreadInfo {
    val allThreads = Thread.getAllStackTraces().keys
    val activeAndNew = allThreads.filter { it.isActive() && it !in threadsActiveBefore }
    val activeUnparkedNew = allThreads.filter { it.isActiveAndUnparked() && it !in threadsActiveBefore }
    val activeUnparked = allThreads.filter { it.isActiveAndUnparked() }
    val activeThreads = allThreads.filter { it.isActive() }
    return ThreadInfo(
        all = allThreads.size,
        active = activeThreads.size,
        activeNew = activeAndNew.size,
        activeBefore = threadsActiveBefore.size,
        activeUnparkedNew = activeUnparkedNew.size,
        activeUnparked = activeUnparked.size,
    )
}

fun Thread.isActiveAndUnparked() = isActive() && stackTrace.firstOrNull()?.methodName != "park"
fun Thread.isActive() = isAlive && !isInterrupted
