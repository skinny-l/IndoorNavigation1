# 🏗️ How to Provide Real Building Data - Step by Step Guide

## **I've Just Added a Configuration System!**

Your app now has a **built-in configuration mode** that lets you easily add real building data by
tapping on the map. Here's exactly how to use it:

## **📱 Step 1: Install and Launch the App**

```bash
# Install the updated app
./gradlew installDebug
```

1. **Login** to your app
2. **Navigate to the map screen**

## **⚙️ Step 2: Enter Configuration Mode**

1. **Tap the Settings button** (⚙️ icon in top-right)
2. You'll see: **"Configuration mode ON - Tap on map to add POIs and beacons"**
3. The app is now ready to record real building data!

## **🗺️ Step 3: Add Real Points of Interest (POIs)**

### **For Each Important Location:**

1. **Look at your floor plan** and identify a real location (restroom, office, lab, etc.)
2. **Tap on that spot** on the map
3. **Select "🏢 Add POI (Point of Interest)"**
4. **Fill in the form:**
    - **Name**: What you'd call it (e.g., "Main Entrance", "Restroom A", "Dr. Smith's Office")
    - **Type**: Category (e.g., "entrance", "restroom", "lab", "office", "cafeteria")
    - **Description**: Any extra info (optional)
5. **Tap "Add"**
6. You'll see a marker appear on the map!

### **Recommended POIs to Add:**

- 🚪 **Entrances/Exits** (main entrance, emergency exits)
- 🚻 **Restrooms** (men's, women's, accessible)
- 🏢 **Offices** (important staff offices, admin)
- 🔬 **Labs** (by number or name)
- 🎓 **Lecture Halls/Classrooms**
- ☕ **Common Areas** (cafeteria, student lounge)
- ⬆️ **Vertical Movement** (elevators, stairs)
- 📚 **Special Facilities** (library, computer lab)

## **📡 Step 4: Add Beacon/WiFi Locations (If You Have Them)**

### **If you have BLE beacons or know WiFi access points:**

1. **Tap where you know a beacon exists** (or WiFi router location)
2. **Select "📡 Add Beacon Location"**
3. **Fill in details:**
    - **Name**: What to call it (e.g., "Entrance Beacon", "WiFi Router A")
    - **MAC Address**: If you know it (can leave blank)
    - **Type**: "BLE", "WiFi", or "both"
4. **Tap "Add"**

### **Don't Have Beacons?**

- **That's fine!** The positioning will still work with simulation
- You can add them later when you install real beacons

## **💾 Step 5: Save Your Configuration**

1. **When you're done adding locations**, tap anywhere on the map
2. **Select "💾 Save Configuration"**
3. You'll see: **"Saved X POIs and Y beacons"**
4. **Your data is now permanently stored!**

## **✅ Step 6: Test Your Real Data**

1. **Select "❌ Exit Configuration Mode"**
2. **Tap the search bar** - you'll now see **your real POIs**!
3. **Select a destination** - it will navigate using **your real building layout**!

## **🔄 Step 7: Edit/Update Anytime**

### **To modify your data:**

1. **Tap Settings** to re-enter configuration mode
2. **Your saved data loads automatically**
3. **Add more locations** or **remove items** by tapping near them
4. **Save again** when done

## **💡 Pro Tips:**

### **For Best Results:**

- **Start with main areas** (entrance, restrooms, elevators)
- **Be specific with names** ("Restroom B2-West" vs just "Restroom")
- **Use consistent types** ("restroom" not "bathroom" or "toilet")
- **Add 10-20 key locations** for comprehensive navigation

### **Real-World Beacon Placement:**

- **Near entrances** (for arrival detection)
- **Corridor intersections** (for navigation accuracy)
- **Important destinations** (labs, offices)
- **3-4 beacons minimum** for good trilateration

### **WiFi Access Points:**

- **Look for visible WiFi names** in your building
- **Administrative areas** usually have dedicated APs
- **Can help with rough positioning** even without exact coordinates

## **📊 What This Achieves:**

### **Before Configuration:**

- ❌ Fictional POI locations
- ❌ Made-up navigation destinations
- ❌ Demo beacon positions

### **After Your Configuration:**

- ✅ **Real building POIs** you can actually navigate to
- ✅ **Accurate destinations** that exist in your building
- ✅ **Proper navigation** between real locations
- ✅ **Custom building layout** tailored to your needs

## **🚀 The Result:**

Once configured, users can:

- **Search for real locations** in your building
- **Get accurate directions** between actual rooms
- **Navigate to places that actually exist**
- **Use familiar location names** they know

**This transforms your app from a technical demo into a real, usable navigation system for your
specific building!** 🎯

---

**Need help?** The process takes about 10-15 minutes to configure a typical floor with 15-20
important locations.