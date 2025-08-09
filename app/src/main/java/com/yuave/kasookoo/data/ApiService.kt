package com.yuave.kasookoo.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    companion object {
        const val BASE_URL = "https://voiceai.kasookoo.com/"
    }
    
    @POST("api/v1/bot/sdk/get-token")
    suspend fun getLiveKitToken(@Body request: TokenRequest): Response<TokenResponse>
    
    @GET("api/v1/drivers/{driverId}/details")
    suspend fun getDriverDetails(@Path("driverId") driverId: String): Response<DriverDetails>
    
    // Legacy support call API (keeping for backward compatibility)
    @POST("api/v1/support/initiate-call")
    suspend fun initiateSupportCall(@Body request: SupportCallRequest): Response<SupportCallResponse>
    
    // New SIP-based call support APIs
    @POST("api/v1/bot/sdk-sip/calls/make")
    suspend fun makeSupportCall(@Body request: SupportCallMakeRequest): Response<SupportCallMakeResponse>
    
    @POST("api/v1/bot/sdk-sip/calls/end")
    suspend fun endSupportCall(@Body request: SupportCallEndRequest): Response<SupportCallEndResponse>
    
    // New registration and login APIs
    @GET("api/v1/bot/random-user")
    suspend fun getRandomUserForCallerIdentity(): Response<RandomUserResponse>
    
    @GET("api/v1/bot/random-lead")
    suspend fun getRandomCustomerLeadForCallerIdentity(): Response<RandomCustomerLeadResponse>
    
    @POST("api/v1/bot/notifications/register-token")
    suspend fun registerCallerOrCalledForFirebaseToken(@Body request: RegisterCallerRequest): Response<RegisterCallerResponse>
    
    @POST("api/v1/bot/notifications/update-token")
    suspend fun updateCallerOrCalledForFirebaseToken(@Body request: UpdateCallerRequest): Response<UpdateCallerResponse>
    
    // New WebRTC calling APIs
    @POST("api/v1/bot/sdk/get-caller-livekit-token")
    suspend fun getCallerLiveKitToken(@Body request: CallerTokenRequest): Response<CallerTokenResponse>
    
    @POST("api/v1/bot/sdk/get-called-livekit-token")
    suspend fun getCalledLiveKitToken(@Body request: CalledTokenRequest): Response<CalledTokenResponse>
}

// Request/Response models for LiveKit token API
data class TokenRequest(
    val room_name: String,
    val participant_identity: String
)

data class TokenResponse(
    val accessToken: String,  // Changed to match backend response
    val wsUrl: String
)

// Driver details models (placeholder)
data class DriverDetails(
    val driverId: String,
    val name: String,
    val phoneNumber: String,
    val status: String,
    val location: DriverLocation?
)

data class DriverLocation(
    val latitude: Double,
    val longitude: Double
)

// Legacy support call models (keeping for backward compatibility)
data class SupportCallRequest(
    val customerId: String,
    val issueType: String,
    val priority: String = "normal"
)

data class SupportCallResponse(
    val callId: String,
    val estimatedWaitTime: Int,
    val queuePosition: Int
)

// New SIP-based support call models
data class SupportCallMakeRequest(
    val phone_number: String = "+443333054030",  // Keep hardcoded phone number as requested
    val room_name: String,                       // Dynamic room name
    val participant_name: String                 // Dynamic participant name from local saved username
)

data class SupportCallMakeResponse(
    val success: Boolean,
    val message: String,
    val data: SupportCallMakeData?,
    val error: String?
)

data class SupportCallMakeData(
    val success: Boolean,
    val call_details: SupportCallDetails,
    val room_token: String,
    val room_name: String,
    val room_session_id: String? = null,  // Add room session ID
    val wsUrl: String? = null  // Add WebSocket URL field
)

data class SupportCallDetails(
    val participant_id: String,
    val participant_identity: String,
    val room_name: String,
    val phone_number: String
)

data class SupportCallEndRequest(
    val participant_identity: String,
    val room_name: String  // Dynamic room name
)

data class SupportCallEndResponse(
    val success: Boolean,
    val message: String,
    val data: String?,
    val error: String?
)

// New registration and login models
data class RandomUserResponse(
    val id: String,
    val email: String,
    val phone_number: String,
    val clerk_id: String?,
    val first_name: String,
    val last_name: String
)

data class RandomCustomerLeadResponse(
    val id: String,
    val full_name: String,
    val email: String,
    val phone_number: String,
    val status: String,
    val user_id: String
)

data class RegisterCallerRequest(
    val user_type: String, // "customer" or "driver"
    val user_id: String,
    val device_token: String,
    val device_info: Map<String, Any>, // Changed to Map for JSON object
    val device_type: String = "android"
)

data class RegisterCallerResponse(
    val success: Boolean,
    val message: String,
    val data: String?,
    val error: String?
)

data class UpdateCallerRequest(
    val user_type: String, // "customer" or "driver"
    val user_id: String,
    val device_token: String,
    val new_device_token: String,
    val device_info: Map<String, Any>, // Changed to Map for JSON object
    val device_type: String = "android"
)

data class UpdateCallerResponse(
    val success: Boolean,
    val message: String,
    val data: String?,
    val error: String?
)

// New WebRTC calling models
data class CallerTokenRequest(
    val room_name: String,
    val participant_identity: String,
    val participant_identity_name: String,
    val participant_identity_type: String,
    val caller_user_id: String
)

data class CallerTokenResponse(
    val accessToken: String,
    val wsUrl: String
)

data class CalledTokenRequest(
    val room_name: String,
    val participant_identity: String,
    val participant_identity_name: String,
    val participant_identity_type: String,
    val called_user_id: String
)

data class CalledTokenResponse(
    val accessToken: String,
    val wsUrl: String
) 