package sdk.kasookoo.ai.data

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.ConnectionSpec
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object ApiClient {
    // API base URL for Kasookoo SDK
    private const val BASE_URL = "https://voiceai.kasookoo.com/"
    // Alternative: Use direct IP if DNS fails
    // private const val BASE_URL = "https://51.89.134.139/"

    // Temporary toggle to bypass SSL chain issues on dev/test devices.
    // Set to false once server certificates are fixed.
    private const val TRUST_ALL_SSL_TEMP = true
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val okHttpClient = createOkHttpClient()
    
    private val gson = GsonBuilder()
        .setLenient()
        .create()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
    
    val apiService: ApiService = retrofit.create(ApiService::class.java)

    /**
     * Create OkHttpClient. In debug builds, relax SSL verification to bypass
     * backend certificate chain issues during development. Release builds use
     * strict TLS by default.
     */
    private fun createOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        // Detect debug at runtime without compile-time dependency on BuildConfig
        val isDebug = try {
            val clazz = Class.forName("sdk.kasookoo.ai.BuildConfig")
            val field = clazz.getField("DEBUG")
            field.getBoolean(null)
        } catch (_: Exception) { false }

        if (isDebug || TRUST_ALL_SSL_TEMP) {
            try {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })

                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, SecureRandom())
                val sslSocketFactory = sslContext.socketFactory

                builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }
                // Prefer HTTP/1.1 to avoid strict HTTP/2 TLS requirements on older devices
                builder.protocols(listOf(Protocol.HTTP_1_1))
                // Allow modern and compatible TLS cipher suites
                builder.connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
                builder.retryOnConnectionFailure(true)
            } catch (_: Exception) {
                // If anything fails, fall back to default secure client
            }
        }

        return builder.build()
    }
}

class CallRepository {
    private val apiService = ApiClient.apiService
    
    
    // New SIP-based support call functions
    suspend fun makeSupportCall(roomName: String, participantName: String): Result<SupportCallMakeResponse> {
        return try {
            val request = SupportCallMakeRequest(
                phone_number = "+443333054030", // Keep hardcoded phone number
                room_name = roomName,
                participant_name = participantName
            )
            val response = apiService.makeSupportCall(request)
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to make support call: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun endSupportCall(participantIdentity: String, roomName: String): Result<SupportCallEndResponse> {
        return try {
            val request = SupportCallEndRequest(participantIdentity, roomName)
            val response = apiService.endSupportCall(request)
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to end support call: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // New registration and login functions
    suspend fun getRandomUserForCallerIdentity(): Result<RandomUserResponse> {
        return try {
            val response = apiService.getRandomUserForCallerIdentity()
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get random user: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getRandomCustomerLeadForCallerIdentity(): Result<RandomCustomerLeadResponse> {
        return try {
            val response = apiService.getRandomCustomerLeadForCallerIdentity()
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get random customer lead: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun registerCallerOrCalledForFirebaseToken(
        userType: String,
        userId: String,
        deviceToken: String,
        deviceInfo: Map<String, Any>
    ): Result<RegisterCallerResponse> {
        return try {
            val request = RegisterCallerRequest(userType, userId, deviceToken, deviceInfo)
            val response = apiService.registerCallerOrCalledForFirebaseToken(request)
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to register caller: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateCallerOrCalledForFirebaseToken(
        userType: String,
        userId: String,
        deviceToken: String,
        newDeviceToken: String,
        deviceInfo: Map<String, Any>
    ): Result<UpdateCallerResponse> {
        return try {
            val request = UpdateCallerRequest(userType, userId, deviceToken, newDeviceToken, deviceInfo)
            val response = apiService.updateCallerOrCalledForFirebaseToken(request)
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to update caller: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // New WebRTC calling methods
    suspend fun getCallerLiveKitToken(
        roomName: String,
        participantIdentity: String,
        participantIdentityName: String,
        participantIdentityType: String,
        callerUserId: String,
        calledUserId: String
    ): Result<CallerTokenResponse> {
        return try {
            val request = CallerTokenRequest(
                room_name = roomName,
                participant_identity = participantIdentity,
                participant_identity_name = participantIdentityName,
                participant_identity_type = participantIdentityType,
                caller_user_id = callerUserId,
                called_user_id = calledUserId,
                device_type = "android",
                is_push_notification = false,
                is_call_recording = true
            )
            val response = apiService.getCallerLiveKitToken(request)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get caller token: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getCalledLiveKitToken(
        roomName: String,
        participantIdentity: String,
        participantIdentityName: String,
        participantIdentityType: String,
        calledUserId: String
    ): Result<CalledTokenResponse> {
        return try {
            val request = CalledTokenRequest(
                room_name = roomName,
                participant_identity = participantIdentity,
                participant_identity_name = participantIdentityName,
                participant_identity_type = participantIdentityType,
                called_user_id = calledUserId,
                device_type = "android",
                is_call_recording = true
            )
            val response = apiService.getCalledLiveKitToken(request)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get called token: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
        }

    // Callee rejects incoming call
    suspend fun rejectIncomingCall(
        roomName: String,
        participantIdentity: String,
        participantIdentityName: String,
        participantIdentityType: String,
        callerUserId: String,
        calledUserId: String
    ): Result<RejectCallResponse> {
        return try {
            val request = RejectCallRequest(
                room_name = roomName,
                participant_identity = participantIdentity,
                participant_identity_name = participantIdentityName,
                participant_identity_type = participantIdentityType,
                device_type = "android",
                caller_user_id = callerUserId,
                called_user_id = calledUserId
            )
            val response = apiService.rejectIncomingCall(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to reject call: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Unregister device token (logout)
    suspend fun unregisterCallerOrCalledForFirebaseToken(
        userType: String,
        userId: String,
        deviceToken: String
    ): Result<UnregisterCallerResponse> {
        return try {
            val request = UnregisterCallerRequest(
                user_type = userType,
                user_id = userId,
                device_token = deviceToken,
                device_type = "android"
            )
            val response = apiService.unregisterCallerOrCalledForFirebaseToken(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to unregister token: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 