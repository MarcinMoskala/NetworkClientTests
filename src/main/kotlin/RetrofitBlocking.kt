//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.runBlocking
//import okhttp3.Dispatcher
//import okhttp3.OkHttpClient
//import retrofit2.Call
//import retrofit2.Response
//import retrofit2.Retrofit
//import retrofit2.converter.scalars.ScalarsConverterFactory
//import retrofit2.http.GET
//import retrofit2.http.Query
//import java.util.concurrent.Executors
//import javax.net.ssl.*
//import kotlin.time.measureTime
//
//
//fun createService(): ApiService {
//    val retrofit: Retrofit = Retrofit.Builder()
//        .client(OkHttpClient.Builder().build())
//        
//        .baseUrl("https://api.kt.academy/")
//        .addConverterFactory(ScalarsConverterFactory.create())
//        .build()
//    return retrofit.create(ApiService::class.java)
//}
//
//
//interface ApiService {
//    @GET("delay")
//    fun getDelayedResponse(@Query("delay") seconds: Int, @Query("a") a: Int): Call<String>
//}
//
//suspend fun main() = measureTime {
//    val dispatcher = Dispatchers.IO.limitedParallelism(1000)
//    val executorService = Executors.newFixedThreadPool(5) // Limit to 5 concurrent threads
//    val apiService: ApiService = createService()
//    runBlocking {
//        repeat(1000) {
//            launch(dispatcher) {
//                apiService.getDelayedResponse(2000, it).execute()
//            }
//        }
//        delay(1000)
//        Thread.getAllStackTraces().keys.map { it.name }.let {
//            println("Active threads: ${it.size}")
//            println(it)
//        }
//    }
//}.let { println("Took ${it.inWholeSeconds}") }
