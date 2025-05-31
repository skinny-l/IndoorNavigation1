# 🏫 Campus WiFi BSSID Collection Guide

## **Your Situation: Campus-Wide Shared SSIDs**

Since your campus uses the same WiFi SSID (e.g., "Campus_WiFi") for every faculty building, we need
to use **BSSID fingerprinting** to distinguish your specific building from others.

## 🔍 **What is BSSID?**

- **SSID**: The WiFi network name (e.g., "Campus_WiFi") - same across all buildings ❌
- **BSSID**: The unique MAC address of each WiFi access point (e.g., "AA:BB:CC:DD:EE:FF") - unique
  to each building ✅

## 📱 **How to Collect BSSID Data**

### **Step 1: Download a WiFi Analyzer App**

**Android:**

- "WiFi Analyzer" by VREM (free, highly recommended)
- "Network Analyzer" by Jiri Techet
- "WiFi Explorer" by Adrian Granados

**iPhone:**

- "WiFi Explorer" by Adrian Granados ($1.99, worth it)
- "Network Analyzer" by Techet
- "WiFi Scanner" by LiznTech

### **Step 2: Survey Your Building**

Walk to these key locations **inside your specific building**:

1. **Main Entrance/Lobby**
2. **Your Floor/Department Area**
3. **Central Corridor**
4. **Library/Study Area** (if applicable)
5. **Cafeteria/Common Area** (if applicable)

### **Step 3: Record BSSID Data**

At each location, open your WiFi analyzer app and record:

#### **Example Data Collection:**

**Location: Main Entrance**

```
Campus_WiFi → BSSID: AA:BB:CC:DD:EE:FF, Signal: -45 dBm
Campus_WiFi → BSSID: 11:22:33:44:55:66, Signal: -52 dBm
Student_WiFi → BSSID: 22:33:44:55:66:77, Signal: -48 dBm
```

**Location: Your Floor**

```
Campus_WiFi → BSSID: BB:CC:DD:EE:FF:AA, Signal: -50 dBm
Campus_WiFi → BSSID: 33:44:55:66:77:88, Signal: -55 dBm
```

### **Step 4: Fill Out This Template**

```
Building: ________________________________

Location 1: Main Entrance
BSSID: _________________________ Signal: _______ dBm
BSSID: _________________________ Signal: _______ dBm  
BSSID: _________________________ Signal: _______ dBm

Location 2: [Your Floor/Department]
BSSID: _________________________ Signal: _______ dBm
BSSID: _________________________ Signal: _______ dBm
BSSID: _________________________ Signal: _______ dBm

Location 3: Central Area
BSSID: _________________________ Signal: _______ dBm
BSSID: _________________________ Signal: _______ dBm
BSSID: _________________________ Signal: _______ dBm

Location 4: [Other Important Area]
BSSID: _________________________ Signal: _______ dBm
BSSID: _________________________ Signal: _______ dBm
BSSID: _________________________ Signal: _______ dBm
```

## ⚙️ **How to Configure the App**

### **Step 1: Update WiFi SSIDs (General Campus Detection)**

```kotlin
private val BUILDING_WIFI_SSIDS = listOf(
    "Campus_WiFi",        // Your actual campus SSID
    "Student_WiFi",       // Any other campus-wide networks
    "YourUniversity_WiFi" // Add all campus networks you found
)
```

### **Step 2: Update BSSID Fingerprints (Building-Specific Detection)**

```kotlin
private val KNOWN_BUILDING_BSSIDS = mapOf(
    // From your data collection above:
    "AA:BB:CC:DD:EE:FF" to -45, // Main entrance access point
    "11:22:33:44:55:66" to -52, // Main entrance access point 2
    "BB:CC:DD:EE:FF:AA" to -50, // Your floor access point
    "33:44:55:66:77:88" to -55, // Your floor access point 2
    "22:33:44:55:66:77" to -48, // Central area access point
    // Add 6-10 BSSIDs for best accuracy
)
```

## 🎯 **Why This Works Better**

### **Old Method (SSID only):**

- User in Building A: Detects "Campus_WiFi" → Thinks inside ❌
- User in Building B: Detects "Campus_WiFi" → Thinks inside ❌
- User in Building C: Detects "Campus_WiFi" → Thinks inside ❌

### **New Method (BSSID fingerprinting):**

- User in Building A: Detects BSSIDs AA:BB:CC... → Not your building ✅
- User in Building B: Detects BSSIDs 11:22:33... → Not your building ✅
- User in YOUR building: Detects BSSIDs from your list → Inside! ✅

## 🧪 **Testing Strategy**

### **Phase 1: Collect Data**

1. Walk around your building collecting BSSIDs
2. Also walk to 1-2 nearby buildings and collect their BSSIDs
3. Compare - they should be completely different

### **Phase 2: Configure App**

1. Add your building's BSSIDs to the code
2. Set SSID list to campus-wide networks
3. Build and test

### **Phase 3: Validate**

1. Test inside your building → Should detect "inside"
2. Test in nearby building → Should detect "outside"
3. Test far from campus → Should detect "outside"

## 💡 **Pro Tips for Campus Environments**

### **Collect More BSSIDs:**

- Aim for **6-10 unique BSSIDs** from your building
- Include access points from different floors/areas
- Prioritize the strongest signals (closer to -30 dBm is better)

### **Signal Strength Notes:**

- **-30 to -50 dBm**: Excellent signal (very close to access point)
- **-50 to -70 dBm**: Good signal (normal room distance)
- **-70 to -85 dBm**: Weak signal (far from access point)
- **Below -85 dBm**: Very weak (might be unreliable)

### **Update Periodically:**

- Campus IT may replace access points
- Survey your building every 6 months
- Add new BSSIDs if you find them

## 🔧 **Configuration Summary**

```kotlin
// BuildingDetector.kt configuration for campus environments:

// 1. Campus-wide SSIDs (for general campus detection)
private val BUILDING_WIFI_SSIDS = listOf(
    "YOUR_CAMPUS_WIFI_NAME", // Replace with actual name
    "Student_WiFi",          // Any other campus networks
    "Guest_WiFi"             // Guest networks if available
)

// 2. Building-specific BSSIDs (for precise building detection)  
private val KNOWN_BUILDING_BSSIDS = mapOf(
    // YOUR COLLECTED DATA GOES HERE:
    "AA:BB:CC:DD:EE:FF" to -45, // Main entrance AP
    "11:22:33:44:55:66" to -52, // Floor 1 AP
    "BB:CC:DD:EE:FF:AA" to -50, // Floor 2 AP
    "33:44:55:66:77:88" to -55, // Central corridor AP  
    "22:33:44:55:66:77" to -48, // Library/lab AP
    "44:55:66:77:88:99" to -58, // Additional APs...
    // Add 6-10 BSSIDs for best accuracy
)
```

This approach will give you **building-specific detection** even when the entire campus shares the
same WiFi network names! 🎯