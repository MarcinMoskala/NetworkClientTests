import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import javax.net.ssl.*
import kotlin.time.measureTime


fun createService(): ApiService {
    val retrofit: Retrofit = Retrofit.Builder()
        .client(
            OkHttpClient.Builder().dispatcher(Dispatcher().apply {
                maxRequests = 100
                maxRequestsPerHost = 1000
            }).build()
        )
        .baseUrl("https://api.kt.academy/")
        .addConverterFactory(ScalarsConverterFactory.create())
        .build()
    return retrofit.create(ApiService::class.java)
}


interface ApiService {
    @GET("delay")
    suspend fun getDelayedResponse(@Query("delay") seconds: Int, @Query("a") a: Int): String
}

suspend fun main() = measureTime {
    runBlocking {
        val apiService: ApiService = createService()
        repeat(1000) {
            launch {
                apiService.getDelayedResponse(10000, it)
            }
        }
        delay(1000)
        Thread.getAllStackTraces().keys.map { it.name }.let {
            println("Active threads: ${it.size}")
            println(it)
        }
    }
}.let { println("Took ${it.inWholeSeconds}") }

val apiService: ApiService by lazy { createService() }

suspend fun retrofitRequest(time: Int, a: Int) {
    apiService.getDelayedResponse(time, a)
}
