package kjhgf

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val d = Dispatchers.IO.limitedParallelism(50)
private val scope = CoroutineScope(d)

suspend fun main() {
    repeat(50) {
        scope.launch { Thread.sleep(2000) }
    }
    delay(1000)
//    Thread.getAllStackTraces().keys.forEach { println(it.stackTrace.toList()) }
    Thread.getAllStackTraces().keys.forEach { println(it.stackTrace.firstOrNull()?.methodName) }
    
    delay(4000)
//    Thread.getAllStackTraces().keys.forEach { println(it.stackTrace.toList()) }
    Thread.getAllStackTraces().keys.forEach { println(it.stackTrace.firstOrNull()?.methodName) }
    
}
