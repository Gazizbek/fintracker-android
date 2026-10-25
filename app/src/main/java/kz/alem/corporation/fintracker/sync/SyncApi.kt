package kz.alem.corporation.fintracker.sync

import androidx.annotation.StringRes
import kotlinx.serialization.json.Json
import kz.alem.corporation.fintracker.BuildConfig
import kz.alem.corporation.fintracker.R
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit

object SyncConfig {
    const val MONTHLY_PRODUCT_ID = "kz.alem.corporation.fintracker.sync.monthly"
    const val YEARLY_PRODUCT_ID = "kz.alem.corporation.fintracker.sync.yearly"
    val ALL_PRODUCT_IDS = listOf(MONTHLY_PRODUCT_ID, YEARLY_PRODUCT_ID)
    const val PUSH_PLATFORM = "android"
}

interface SyncApi {
    @GET("subscription")
    suspend fun getSubscription(): SubscriptionDto

    @POST("subscription/android")
    suspend fun verifyAndroidSubscription(@Body body: AndroidVerifyRequest): SubscriptionDto

    @POST("sync/upload")
    suspend fun upload(@Body payload: UploadPayload): UploadResponse

    @GET("sync/pull")
    suspend fun pull(@Query("since") since: Long): PullResponse

    @GET("sync/download")
    suspend fun download(): PullResponse

    @DELETE("sync/data")
    suspend fun deleteCloudData()

    @POST("devices/register")
    suspend fun registerDevice(@Body body: RegisterDeviceRequest)

    @POST("devices/unregister")
    suspend fun unregisterDevice(@Body body: UnregisterDeviceRequest)
}

sealed class SyncApiException(@get:StringRes val messageRes: Int?, message: String) : Exception(message) {
    class NotSignedIn : SyncApiException(R.string.notSignedIn, "Not signed in")
    class SubscriptionRequired : SyncApiException(R.string.subscriptionRequired, "Subscription required")
    class Server(code: Int) : SyncApiException(null, "Server error $code")
}

/** Thrown inside OkHttp (which only tolerates IOExceptions) when there is no user. */
private class NoUserException : IOException("Not signed in")

/** Runs an API call, translating HTTP failures into [SyncApiException]s. */
suspend fun <T> apiCall(block: suspend () -> T): T = try {
    block()
} catch (_: NoUserException) {
    throw SyncApiException.NotSignedIn()
} catch (e: HttpException) {
    throw when (e.code()) {
        401 -> SyncApiException.NotSignedIn()
        403 -> SyncApiException.SubscriptionRequired()
        else -> SyncApiException.Server(e.code())
    }
}

val syncJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
}

/**
 * Adds the Firebase ID token to every request. A 401 usually means a stale token (clock
 * skew, revoked session) rather than a dead account, so retry once with a force-refreshed
 * token before giving up.
 */
private class AuthInterceptor(private val tokenProvider: (forceRefresh: Boolean) -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider(false) ?: throw NoUserException()
        val response = chain.proceed(chain.request().withToken(token))
        if (response.code != 401) return response
        response.close()
        val fresh = tokenProvider(true) ?: throw NoUserException()
        return chain.proceed(chain.request().withToken(fresh))
    }

    private fun okhttp3.Request.withToken(token: String) = newBuilder()
        .header("Authorization", "Bearer $token")
        .header("Content-Type", "application/json")
        .build()
}

fun createSyncApi(tokenProvider: (forceRefresh: Boolean) -> String?): SyncApi {
    val client = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(tokenProvider))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    return Retrofit.Builder()
        .baseUrl(BuildConfig.BACKEND_URL)
        .client(client)
        .addConverterFactory(syncJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SyncApi::class.java)
}
