# 🏢 Building Configuration Guide

This guide will help you configure your indoor navigation app with real building data to make the
outdoor navigation system work properly.

## 📍 Step 1: Get Your Building's GPS Coordinates

### Method 1: Using Google Maps

1. Open Google Maps on your computer or phone
2. Navigate to your building
3. Right-click (or long-press on mobile) on your building's main entrance
4. Copy the coordinates that appear (format: latitude, longitude)
5. Example: `3.0706, 101.6068`

### Method 2: Using GPS Apps

1. Use apps like GPS Essentials, GPS Test, or What3Words
2. Stand at your building's main entrance
3. Record the exact coordinates

### Method 3: Online Tools

1. Use https://www.latlong.net/
2. Search for your building address
3. Get precise coordinates

## 📶 Step 2: Identify Your Building's WiFi Networks

### What to Look For:

1. **Main building WiFi** (usually has building/institution name)
2. **Department-specific WiFi** (CS_Department, Engineering_WiFi, etc.)
3. **Campus-wide WiFi** (if applicable)
4. **Public WiFi** available in the building

### How to Find WiFi Names:

1. **On Android:**
    - Go to Settings > WiFi
    - Look at available networks when you're inside the building
    - Note down the exact network names (SSIDs)

2. **On iPhone:**
    - Go to Settings > WiFi
    - Look at available networks
    - Note the exact names

3. **Using WiFi Analyzer Apps:**
    - Download "WiFi Analyzer" (Android) or "WiFi Explorer" (iOS)
    - Scan for networks inside your building
    - Record the strongest signals and their names

### Important Notes:

- Record the **exact** WiFi network names (case-sensitive)
- Focus on networks that are consistently available inside the building
- Avoid guest networks that might be temporary

## 🚪 Step 3: Map Your Building Entrances

For each entrance, you need to collect:

### Required Information:

1. **GPS Coordinates** (use same methods as Step 1)
2. **Name** (Main Entrance, East Door, Loading Dock, etc.)
3. **Description** (what's special about this entrance)
4. **Accessibility** (wheelchair accessible?)
5. **Elevator Access** (does this entrance have elevator access?)
6. **Opening Hours** (24/7 or specific hours)
7. **Special Features** (reception desk, security, parking nearby, etc.)

### Data Collection Template:

```
Entrance 1:
- Name: "Main Entrance"
- Description: "Primary entrance with reception desk"
- GPS: 3.0706, 101.6068
- Wheelchair Accessible: Yes/No
- Has Elevator: Yes/No
- Hours: "8:00 AM - 6:00 PM" or "24/7"
- Features: ["Reception", "Security Desk", "Elevator Access"]

Entrance 2:
- Name: "East Entrance"
- Description: "Side entrance near parking"
- GPS: 3.0707, 101.6069
- Wheelchair Accessible: Yes/No
- Has Elevator: Yes/No
- Hours: "8:00 AM - 6:00 PM"
- Features: ["Parking Access", "Quick Entry"]
```

## 🅿️ Step 4: Document Parking Information

### What to Document:

1. **Parking Lot Names** (Visitor Parking, Staff Lot A, etc.)
2. **Distance from entrances** (approximate walking distance)
3. **Access restrictions** (visitor allowed, staff only, etc.)
4. **Special notes** (covered parking, electric charging, etc.)

### Example Template:

```
Parking Spot 1:
- Name: "Visitor Parking Lot"
- Location: "50m from main entrance"
- Visitor Allowed: Yes
- Notes: "Free for first 2 hours"

Parking Spot 2:
- Name: "Staff Parking Garage"
- Location: "100m from east entrance"
- Visitor Allowed: No
- Notes: "Covered parking, card access required"
```

## 🔧 Step 5: Advanced WiFi Fingerprinting (Optional)

For more accurate indoor/outdoor detection, you can collect WiFi BSSID data:

### What is BSSID?

- BSSID is the unique MAC address of each WiFi access point
- More reliable than SSID for location detection
- Format: `AA:BB:CC:DD:EE:FF`

### How to Collect BSSID Data:

1. **Use WiFi Analyzer Apps:**
    - Android: "WiFi Analyzer" by VREM
    - iOS: "WiFi Explorer" or "Network Analyzer"

2. **At Each Key Location:**
    - Stand at main entrance
    - Record strongest WiFi signals and their BSSIDs
    - Note the signal strength (RSSI) in dBm
    - Repeat at other entrances

3. **Data Format:**

```
Location: Main Entrance
BSSID: AA:BB:CC:DD:EE:FF, Signal: -45 dBm
BSSID: 11:22:33:44:55:66, Signal: -52 dBm

Location: East Entrance  
BSSID: BB:CC:DD:EE:FF:AA, Signal: -48 dBm
BSSID: 22:33:44:55:66:77, Signal: -55 dBm
```

## ⚙️ Step 6: Update Configuration Files

Once you have all the data, update these files:

### 1. Building Detector (`BuildingDetector.kt`):

```kotlin
// Update coordinates
private const val BUILDING_LAT = YOUR_LATITUDE
private const val BUILDING_LONG = YOUR_LONGITUDE

// Update WiFi networks
private val BUILDING_WIFI_SSIDS = listOf(
    "Your_Building_WiFi",
    "Your_Campus_WiFi",
    "Your_Department_WiFi"
)

// Update BSSID fingerprints (if collected)
private val KNOWN_BUILDING_BSSIDS = mapOf(
    "AA:BB:CC:DD:EE:FF" to -45,
    "11:22:33:44:55:66" to -52
)
```

### 2. Outdoor Navigation Manager (`OutdoorNavigationManager.kt`):

```kotlin
// Update building info
private const val BUILDING_LAT = YOUR_LATITUDE
private const val BUILDING_LONG = YOUR_LONGITUDE
private const val BUILDING_NAME = "Your Building Name"
private const val BUILDING_ADDRESS = "Your Building Address"

// Update entrances
private val buildingEntrances = listOf(
    BuildingEntrance(
        id = "main_entrance",
        name = "Your Main Entrance Name",
        description = "Your entrance description",
        position = Position(ENTRANCE_LAT, ENTRANCE_LONG, 0),
        isAccessible = true/false,
        hasElevator = true/false,
        isOpen24Hours = true/false,
        openingHours = "Your opening hours",
        features = listOf("Your", "Features", "List")
    )
    // Add more entrances...
)

// Update parking info
fun getParkingInfo(): ParkingInfo {
    return ParkingInfo(
        availableSpots = listOf(
            ParkingSpot("Your Parking Name", "Distance from entrance", true/false)
            // Add more parking spots...
        ),
        recommendations = "Your parking recommendations"
    )
}
```

## 🧪 Step 7: Testing and Calibration

### Testing Checklist:

1. **Indoor Detection Test:**
    - Enable development mode initially
    - Walk around inside the building
    - Check if WiFi networks are detected correctly

2. **Outdoor Detection Test:**
    - Disable development mode
    - Walk outside the building at various distances
    - Verify GPS-based detection works

3. **Entrance Navigation Test:**
    - Test navigation to each entrance
    - Verify coordinates are accurate
    - Check if external maps open correctly

4. **Distance Calibration:**
    - Adjust `BUILDING_RADIUS_METERS` if needed
    - Test at different distances from building
    - Fine-tune detection thresholds

### Development Mode:

```kotlin
// In MainActivity or test code
buildingDetector?.setDevelopmentMode(true)  // Forces "inside" status
buildingDetector?.setDevelopmentMode(false) // Uses real detection
```

## 📱 Step 8: User Testing

1. **Test with real users** at different locations
2. **Collect feedback** on detection accuracy
3. **Fine-tune parameters** based on real-world usage
4. **Update WiFi networks** as building infrastructure changes

## 🔄 Step 9: Maintenance

### Regular Updates Needed:

- **WiFi networks** (when building infrastructure changes)
- **Opening hours** (seasonal changes, holidays)
- **Parking information** (new lots, policy changes)
- **Entrance accessibility** (renovations, temporary closures)

### Monitoring:

- Check app logs for detection accuracy
- Monitor user feedback about navigation issues
- Update coordinates if entrances are modified

---

## 📋 Quick Checklist

- [ ] ✅ Building GPS coordinates collected
- [ ] ✅ WiFi network names documented
- [ ] ✅ All entrances mapped with GPS coordinates
- [ ] ✅ Entrance accessibility information collected
- [ ] ✅ Opening hours documented
- [ ] ✅ Parking information gathered
- [ ] ✅ Configuration files updated
- [ ] ✅ Testing completed
- [ ] ✅ User feedback collected
- [ ] ✅ Fine-tuning done

Once you complete these steps, your outdoor navigation system will be fully functional with real
building data!