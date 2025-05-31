# 📍 CS1 Building Coordinates Configuration Guide

## Step 1: Get CS1 Building Coordinates from Google Maps

### **Method 1: Find Building on Google Maps**

1. **Open Google Maps** → Search for `"CS1 building" + your university name`
2. **Find the CS1 building** (usually a computer science or engineering building)
3. **Right-click on the building center** → Select **"What's here?"**
4. **Copy the coordinates** that appear (format: `3.1234567, 101.6543210`)

### **Method 2: Search for Your University's CS Department**

1. Search for `"Computer Science faculty" + your university`
2. Look for CS1 or similar building names
3. Right-click and get coordinates

### **Expected Coordinate Range for Malaysia:**

- **Latitude**: 1.0 to 7.0 (Northern Malaysia ~6.x, Central ~3.x, Southern ~1.x)
- **Longitude**: 99.0 to 119.0 (Peninsular ~100-104, East Malaysia ~110-119)

## Step 2: Get Building Physical Dimensions

### **From Google Maps:**

1. **Use Measure Tool**: Right-click → "Measure distance"
2. **Measure length**: East-West distance in meters
3. **Measure width**: North-South distance in meters
4. **Typical CS building**: 60-100m length, 40-80m width

### **From Floor Plan:**

If you have the actual floor plan scale, measure the dimensions directly.

## Step 3: Configure Your App

Now update these files with your CS1 building coordinates:

### **File 1: BuildingDetector.kt** ⚠️ **CRITICAL**

```kotlin
// Line 56-57: Update with your CS1 coordinates
private const val BUILDING_LAT = 3.1234567 // Replace with your latitude
private const val BUILDING_LONG = 101.6543210 // Replace with your longitude
```

### **File 2: OutdoorNavigationManager.kt** ⚠️ **CRITICAL**

```kotlin
// Line 36-37: Update with your CS1 coordinates  
private const val BUILDING_LAT = 3.1234567 // Replace with your latitude
private const val BUILDING_LONG = 101.6543210 // Replace with your longitude
```

### **File 3: NewMapFragment.kt** 🏗️ **IMPORTANT**

```kotlin
// Line 96: Update building dimensions
floorPlanView.setBuildingDimensions(80.0, 60.0) // Replace with real dimensions
```

### **File 4: FloorPlanView.kt** 🏗️ **IMPORTANT**

```kotlin
// Line 162-163: Update default dimensions
private var buildingWidth = 80.0 // Replace with real width
private var buildingHeight = 60.0 // Replace with real height
```

## Step 4: Example Configuration

### **For University of Malaya (Example):**

```kotlin
// If CS1 building is at UM
private const val BUILDING_LAT = 3.1219 // Example coordinates
private const val BUILDING_LONG = 101.6537 
```

### **For Universiti Teknologi Malaysia (Example):**

```kotlin
// If CS1 building is at UTM
private const val BUILDING_LAT = 1.5581 // Example coordinates  
private const val BUILDING_LONG = 103.6424
```

## Step 5: Files to Update Checklist

### **Essential Files (Must Update):**

- [ ] `app/src/main/java/com/example/indoornavigation/positioning/BuildingDetector.kt`
- [ ] `app/src/main/java/com/example/indoornavigation/outdoor/OutdoorNavigationManager.kt`
- [ ] `app/src/main/java/com/example/indoornavigation/ui/map/NewMapFragment.kt`
- [ ] `app/src/main/java/com/example/indoornavigation/ui/map/FloorPlanView.kt`

### **Optional Files (For Testing):**

- [ ] `app/src/main/java/com/example/indoornavigation/testing/OutdoorNavigationTester.kt`
- [ ] `app/src/main/java/com/example/indoornavigation/MainActivity.kt`

## Step 6: Verification

### **Test Indoor/Outdoor Detection:**

1. **Outside CS1 building**: App should show "Outdoor mode"
2. **Inside CS1 building**: App should show "Indoor mode"
3. **Distance calculation**: Should show accurate distance to building

### **Test Building Dimensions:**

1. **Floor plan scale**: Should match real building proportions
2. **Navigation**: Routes should make sense within the building
3. **POI placement**: Locations should align with real positions

## 🛠️ **Quick Implementation Steps:**

### **1. Get Your Coordinates (5 minutes)**

```bash
# Search this in Google Maps:
"CS1 building [YOUR_UNIVERSITY_NAME]"
# Right-click → "What's here?" → Copy coordinates
```

### **2. Update BuildingDetector.kt**

```kotlin
private const val BUILDING_LAT = YOUR_LATITUDE    // From Google Maps
private const val BUILDING_LONG = YOUR_LONGITUDE  // From Google Maps  
```

### **3. Update OutdoorNavigationManager.kt**

```kotlin
private const val BUILDING_LAT = YOUR_LATITUDE    // Same as above
private const val BUILDING_LONG = YOUR_LONGITUDE  // Same as above
```

### **4. Update Building Dimensions**

```kotlin
// In NewMapFragment.kt
floorPlanView.setBuildingDimensions(YOUR_WIDTH, YOUR_HEIGHT) // From measurements
```

### **5. Test the App**

- Walk around your campus
- App should detect when you're inside/outside CS1
- Indoor positioning should work inside the building

## 🎯 **Why This Matters:**

### **Indoor/Outdoor Detection:**

- App knows when to switch between GPS (outdoor) and BLE/WiFi (indoor)
- Prevents GPS interference indoors
- Enables proper positioning system selection

### **Coordinate System Alignment:**

- Your floor plan coordinates align with real-world positions
- POIs you configure will have correct relative positions
- Navigation routes will be accurate

### **Building-Specific WiFi:**

- App can detect your specific building's WiFi networks
- Improves positioning accuracy
- Reduces interference from neighboring buildings

## 🚨 **Important Notes:**

1. **Use building CENTER coordinates** (not corner or entrance)
2. **Double-check coordinates** - wrong values will break indoor/outdoor detection
3. **Measure dimensions accurately** - affects floor plan scaling
4. **Test in the actual building** - verify detection works correctly

Once you get the coordinates, I can help you update all the necessary files! 🚀