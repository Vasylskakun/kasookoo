package com.yuave.kasookoo.data

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE_URL = "https://voiceai.kasookoo.com/"
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = GsonBuilder()
        .setLenient()
        .create()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
    
    val apiService: ApiService = retrofit.create(ApiService::class.java)
}

class CallRepository {
    private val apiService = ApiClient.apiService
    
    suspend fun getLiveKitToken(roomName: String, participantIdentity: String): Result<TokenResponse> {
        return try {
            val request = TokenRequest(roomName, participantIdentity)
            val response = apiService.getLiveKitToken(request)
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get token: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getDriverDetails(driverId: String): Result<DriverDetails> {
        return try {
            val response = apiService.getDriverDetails(driverId)
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get driver details: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Legacy support call function (keeping for backward compatibility)
    suspend fun initiateSupportCall(customerId: String, issueType: String): Result<SupportCallResponse> {
        return try {
            val request = SupportCallRequest(customerId, issueType)
            val response = apiService.initiateSupportCall(request)
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to initiate support call: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
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
        callerUserId: String
    ): Result<CallerTokenResponse> {
        return try {
            val request = CallerTokenRequest(roomName, participantIdentity, participantIdentityName, participantIdentityType, callerUserId)
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
            val request = CalledTokenRequest(roomName, participantIdentity, participantIdentityName, participantIdentityType, calledUserId)
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
} 