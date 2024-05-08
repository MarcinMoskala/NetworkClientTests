package simple

import clients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.measureTime
import ClientToTest

fun main() = runBlocking {
    val threadsActiveBefore = Thread.getAllStackTraces().keys.filter { it.isActive() }.toSet()
    for (client in clients) {
        testClient(client, threadsActiveBefore)
    }
}

suspend fun testClient(client: ClientToTest, threadsActiveBefore: Set<Thread>) {
    val requestsToStart = 1000
    client.start(requestsToStart)
    val activeAndNew: Int
    measureTime {
        coroutineScope {
            repeat(requestsToStart) {
                launch {
                    client.request(2000, it)
                }
            }
            delay(1000)
            activeAndNew = Thread.getAllStackTraces().keys.filter { it.isActive() && it !in threadsActiveBefore }.size
        }
    }.let { 
        println("For client ${client.name} active threads: $activeAndNew, time: ${it.inWholeSeconds} seconds")
    }
    client.close()
    restartCoroutineDispatchers()
    forceGC()
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

fun Thread.isActive() = isAlive && !isInterrupted && stackTrace.firstOrNull()?.methodName != "park"
