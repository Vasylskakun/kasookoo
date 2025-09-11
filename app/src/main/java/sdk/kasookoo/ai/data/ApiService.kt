package sdk.kasookoo.ai.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    companion object {
        const val BASE_URL = "https://voiceai.kasookoo.com/"
    }
    
    
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

    // Reject incoming call (callee-side)
    @POST("api/v1/bot/sdk/calls/reject")
    suspend fun rejectIncomingCall(@Body request: RejectCallRequest): Response<RejectCallResponse>

    // Logout/unregister device token
    @POST("api/v1/bot/notifications/unregister-token")
    suspend fun unregisterCallerOrCalledForFirebaseToken(@Body request: UnregisterCallerRequest): Response<UnregisterCallerResponse>
}

// Removed legacy and unused models/endpoints: TokenRequest/Response, DriverDetails/DriverLocation,
// and legacy SupportCallRequest/Response.

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
    val caller_user_id: String,
    val called_user_id: String,
    val device_type: String = "android",
    val is_push_notification: Boolean = false,
    val is_call_recording: Boolean = true
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
    val called_user_id: String,
    val device_type: String = "android",
    val is_call_recording: Boolean = true
)

data class CalledTokenResponse(
    val accessToken: String,
    val wsUrl: String
) 

// Reject incoming call models
data class RejectCallRequest(
    val room_name: String,
    val participant_identity: String,
    val participant_identity_name: String,
    val participant_identity_type: String,
    val device_type: String = "android",
    val caller_user_id: String,
    val called_user_id: String
)

data class RejectCallResponse(
    val success: Boolean,
    val message: String,
    val data: String?,
    val error: String?
)

// Unregister (logout) models
data class UnregisterCallerRequest(
    val user_type: String,
    val user_id: String,
    val device_token: String,
    val device_type: String = "android"
)

data class UnregisterCallerResponse(
    val success: Boolean,
    val message: String,
    val data: String?,
    val error: String?
)