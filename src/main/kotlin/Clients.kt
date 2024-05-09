import fuel.FuelBuilder
import fuel.HttpLoader
import fuel.Request
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.engine.apache5.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.java.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

interface ClientToTest {
    val name: String
    fun start(requestsToStart: Int)
    suspend fun request(seconds: Int, a: Int)
    fun close() {}
}

object RetrofitClient : ClientToTest {
    override val name: String = "Retrofit"
    var dispatcher: Dispatcher? = null
    var apiService: ApiService? = null
    var client: OkHttpClient? = null

    override fun start(requestsToStart: Int) {
        dispatcher = Dispatcher().apply {
            maxRequests = requestsToStart
            maxRequestsPerHost = requestsToStart
        }
        client = OkHttpClient.Builder().dispatcher(dispatcher!!).build()
        val retrofit: Retrofit = Retrofit.Builder()
            .client(client!!)
            .baseUrl("https://api.kt.academy/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
        apiService = retrofit.create(ApiService::class.java)
    }

    interface ApiService {
        @GET("delay")
        suspend fun getDelayedResponse(@Query("delay") seconds: Int, @Query("a") a: Int): String
    }

    override suspend fun request(seconds: Int, a: Int) {
        apiService!!.getDelayedResponse(seconds, a)
    }

    override fun close() {
        interruptOkHttpThreads(dispatcher!!, client!!)
        dispatcher = null
        apiService = null
        client = null
    }
}

abstract class KtorClient(override val name: String) : ClientToTest {
    var client: HttpClient? = null
    abstract fun createClient(requestsToStart: Int): HttpClient

    override fun start(requestsToStart: Int) {
        client = createClient(requestsToStart)
    }

    override suspend fun request(seconds: Int, a: Int) {
        client!!.get("https://api.kt.academy/delay") {
            parameter("delay", seconds)
            parameter("a", a)
        }
    }

    override fun close() {
        runCatching { client!!.close() }
        client = null
    }
}

object KtorApacheClient : KtorClient("Ktor Apache") {
    override fun createClient(requestsToStart: Int): HttpClient = HttpClient(Apache) {
        engine {
            followRedirects = true
            socketTimeout = 10_000
            connectTimeout = 10_000
            connectionRequestTimeout = 20_000
            customizeClient {
                setMaxConnTotal(requestsToStart)
                setMaxConnPerRoute(requestsToStart)
            }
        }
    }
}

object KtorApache5Client : KtorClient("Ktor Apache5") {
    override fun createClient(requestsToStart: Int): HttpClient = HttpClient(Apache5) {
        engine {
            followRedirects = true
            socketTimeout = 10_000
            connectTimeout = 10_000
            connectionRequestTimeout = 20_000
        }
    }
}

object KtorCioClient : KtorClient("Ktor CIO") {
    override fun createClient(requestsToStart: Int): HttpClient = HttpClient(CIO) {
        engine {
            maxConnectionsCount = requestsToStart
            endpoint.connectTimeout = 100_000
            requestTimeout = 100_000
        }
    }
}

object KtorOkHttp : KtorClient("Ktor OkHttp") {
    private var dispatcher: Dispatcher? = null

    override fun createClient(requestsToStart: Int): HttpClient {
        dispatcher = Dispatcher().apply {
            maxRequests = requestsToStart
            maxRequestsPerHost = requestsToStart
        }
        return HttpClient(OkHttp) {
            engine {
                config {
                    this.connectTimeout(100_000, java.util.concurrent.TimeUnit.MILLISECONDS)
                    this.readTimeout(100_000, java.util.concurrent.TimeUnit.MILLISECONDS)
                    this.writeTimeout(100_000, java.util.concurrent.TimeUnit.MILLISECONDS)
                    this.callTimeout(100_000, java.util.concurrent.TimeUnit.MILLISECONDS)
                    dispatcher(dispatcher!!)
                }
            }
        }
    }

    override fun close() {
        super.close()
        runCatching { dispatcher?.executorService?.shutdown() }
        runCatching { dispatcher?.executorService?.shutdownNow() }
        interruptOkHttpThreads(dispatcher!!)
        dispatcher = null
    }
}

object KtorJavaClient : KtorClient("Ktor Java") {
    override fun createClient(requestsToStart: Int): HttpClient = HttpClient(Java) {
        engine {
            config {
                connectTimeout(100_000.milliseconds.toJavaDuration())
            }
        }
    }
}

object FuelClient : ClientToTest {
    override val name: String = "Fuel"
    private var dispatcher: Dispatcher? = null
    private var client: OkHttpClient? = null
    private var fuel: HttpLoader? = null

    override fun start(requestsToStart: Int) {
        dispatcher = Dispatcher().apply {
            maxRequests = requestsToStart
            maxRequestsPerHost = requestsToStart
        }
        client = OkHttpClient.Builder().dispatcher(dispatcher!!).build()
        fuel = FuelBuilder().config(client!!).build()
    }

    override suspend fun request(seconds: Int, a: Int) {
        fuel!!.get(Request.Builder().url("https://api.kt.academy/delay?delay=$seconds&a=$a").build()).body
    }

    override fun close() {
        interruptOkHttpThreads(dispatcher!!, client!!)
        dispatcher = null
        client = null
        fuel = null

    }
}

fun interruptOkHttpThreads(dispatcher: Dispatcher, client: OkHttpClient? = null) {
    runCatching { dispatcher.executorService.shutdown() }
    runCatching { dispatcher.executorService.shutdownNow() }
    client?.cache?.close()
    client?.connectionPool?.evictAll()
    for (thread in Thread.getAllStackTraces().keys) {
        if (thread != null && thread.name.contains("OkHttp")) {
            thread.interrupt()
        }
    }
}

val clients: List<ClientToTest> = listOf(
    RetrofitClient,
    KtorOkHttp,
    KtorCioClient,
    KtorApacheClient,
    KtorApache5Client,
    KtorJavaClient,
    FuelClient,
)
