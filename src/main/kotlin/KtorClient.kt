import fuel.FuelBuilder
import fuel.Request
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.engine.apache5.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.java.*
import io.ktor.client.engine.jetty.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

val apacheClient = HttpClient(Apache) {
    engine {
        followRedirects = true
        socketTimeout = 10_000
        connectTimeout = 10_000
        connectionRequestTimeout = 20_000
        customizeClient {
            setMaxConnTotal(1000)
            setMaxConnPerRoute(100)
        }
        customizeRequest {
            // TODO: request transformations
        }
    }
}

val apache5Client = HttpClient(Apache5) {
    engine {
        followRedirects = true
        socketTimeout = 10_000
        connectTimeout = 10_000
        connectionRequestTimeout = 20_000
        customizeClient {

        }
        customizeRequest {

        }
    }
}

val cioClient = HttpClient(CIO) {
    engine {
        maxConnectionsCount = 1000
        endpoint.connectTimeout = 100_000
        requestTimeout = 100_000
    }
}

// 62 sec
val ktorOkHttpClient = HttpClient(OkHttp) {
    engine {
        config {
            this.connectTimeout(100_000, java.util.concurrent.TimeUnit.MILLISECONDS)
            this.readTimeout(100_000, java.util.concurrent.TimeUnit.MILLISECONDS)
            this.writeTimeout(100_000, java.util.concurrent.TimeUnit.MILLISECONDS)
            this.callTimeout(100_000, java.util.concurrent.TimeUnit.MILLISECONDS)
            dispatcher(Dispatcher().apply {
                maxRequests = 100
                maxRequestsPerHost = 1000
            })
        }
    }
}

val javaClient = HttpClient(Java) {
    engine {
        config {
            connectTimeout(100_000.milliseconds.toJavaDuration())
        }
    }
}

suspend fun ktorOkHttpRequest(seconds: Int, a: Int) {
    ktorOkHttpClient.get("https://api.kt.academy/delay") {
        parameter("delay", seconds)
        parameter("a", a)
    }
}

suspend fun ktorCioRequest(seconds: Int, a: Int) {
    cioClient.get("https://api.kt.academy/delay") {
        parameter("delay", seconds)
        parameter("a", a)
    }
}

suspend fun ktorApacheRequest(seconds: Int, a: Int) {
    apacheClient.get("https://api.kt.academy/delay") {
        parameter("delay", seconds)
        parameter("a", a)
    }
}

suspend fun ktorApache5Request(seconds: Int, a: Int) {
    apache5Client.get("https://api.kt.academy/delay") {
        parameter("delay", seconds)
        parameter("a", a)
    }
}

suspend fun ktorJavaRequest(seconds: Int, a: Int) {
    javaClient.get("https://api.kt.academy/delay") {
        parameter("delay", seconds)
        parameter("a", a)
    }
}

val fuel = FuelBuilder().config(
    OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply {
            maxRequests = 100
            maxRequestsPerHost = 1000
        }).build()
).build()

suspend fun fuelRequest(seconds: Int, a: Int) {
    fuel.get(Request.Builder().url("https://api.kt.academy/delay?delay=$seconds&a=$a").build()).body
}
