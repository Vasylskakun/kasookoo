# 🎯 Demo Guide -  Kasookoo SDK App

## Quick Demo Flow

### 📱 **1. Launch App**
- App opens with **User Selection Screen**
- Two buttons: "I'm a Customer" and "I'm a Driver"

### 👤 **2. Customer Flow**
1. **Tap "I'm a Customer"** 
2. **Main Screen** shows with:
   - Ride-sharing interface
   - "Call Driver" button
   - "Call Support" button
3. **Tap "Call Driver"**
   - App calls real backend API: `https://voiceai.kasookoo.com/api/v1/bot/sdk/get-token`
   - Gets LiveKit token and WebSocket URL
   - Connects to LiveKit room "sdk-room"
   - Shows **Ringing Screen** (Screen 2)
   - Auto-answers after 3 seconds
   - Shows **Active Call Screen** (Screen 3) with timer

### 🚗 **3. Driver Flow**
1. **Tap "I'm a Driver"**
2. **Main Screen** shows:
   - Same interface but without call buttons
   - "Waiting for incoming calls..." message
3. **Driver automatically connects** to LiveKit room
4. **When customer calls**, driver sees **Ringing Screen**
5. **Driver can accept** → **Active Call Screen**

## 🔧 **API Integration**

**Real Backend API Used:**
```bash
curl --location 'https://voiceai.kasookoo.com/api/v1/bot/sdk/get-token' \
--header 'Content-Type: application/json' \
--data '{
    "room_name":"sdk-room",
    "participant_identity":"customer_123"
}'
```

**Response:**
```json
{
    "access_token": "eyJ...",
    "wsUrl": "wss://..."
}
```

## 🎮 **Test Scenarios**

### **Scenario 1: Customer-Driver Call**
1. Open app in **2 devices/emulators**
2. Device 1: Select "Customer"
3. Device 2: Select "Driver"
4. Device 1: Tap "Call Driver"
5. **Result**: Both devices connect to LiveKit room

### **Scenario 2: Single Device Demo**
1. Select "Customer"
2. Tap "Call Driver"
3. **Result**: Shows complete call flow (Ringing → Active Call)

## 📱 **Screen Breakdown**

- **Screen 1**: User Selection (Customer/Driver)
- **Screen 2**: Main interface with call buttons
- **Screen 3**: Ringing screen during call setup
- **Screen 4**: Active call with timer and controls
- **Screen 5**: Same as Screen 4 for support calls

## ✅ **What Works**
- ✅ Real LiveKit API integration
- ✅ Customer/Driver role selection
- ✅ WebRTC room connection
- ✅ Beautiful UI matching mockups
- ✅ Call flow navigation
- ✅ Auto-answer simulation
- ✅ Call timer and controls

## 🚀 **Ready to Test!**

Run the app and experience the complete voice calling demo with real backend integration! 