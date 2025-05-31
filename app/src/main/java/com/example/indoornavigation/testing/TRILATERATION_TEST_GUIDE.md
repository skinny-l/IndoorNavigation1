# Trilateration Testing Guide

This guide explains how to use the Trilateration Test screen to evaluate the accuracy of
beacon-based indoor positioning.

## Accessing the Trilateration Test Screen

There are two ways to access the Trilateration Test screen:

1. **From the Navigation Drawer**:
   - Open the side navigation drawer by swiping from the left edge or tapping the hamburger menu
     icon
   - Select "Trilateration Test" from the menu

2. **From the Debug Screen**:
   - Navigate to the Debug screen using the bottom navigation
   - Tap the orange "Trilateration Test" button

## Using the Trilateration Test Screen

### 1. Start Scanning for Beacons

When you first open the test screen:

- Tap the **Start Scanning** button at the top of the screen
- The app will begin scanning for nearby BLE beacons
- A progress indicator shows that scanning is active

### 2. Select Beacons for Trilateration

After scanning begins, a list of detected beacons will appear:

- Each beacon shows its name, UUID, RSSI signal strength, and estimated distance
- Tap the **Select** button next to at least 3 beacons you want to use for positioning
- Selected beacons will be highlighted with a green border
- A counter below the list shows how many beacons are selected
- You need at least 3 beacons for trilateration to work

### 3. View Positioning Results

Once you've selected 3 or more beacons:

- The Position Card will appear showing your calculated coordinates in meters (X, Y)
- The estimated accuracy of the position calculation is displayed
- Details about each selected beacon are shown, including distance and signal strength
- The Floor Plan visualization will show your position (red dot) relative to the selected beacons (
  blue dots)

### 4. Adjust the Path Loss Exponent

The Path Loss Exponent affects how distance is calculated from signal strength:
- Default is 2.0, which is suitable for open spaces
- Higher values (3.0-4.0) are better for environments with many obstacles
- Lower values (1.5-2.0) are better for open spaces
- Adjust the slider at the bottom of the screen to see how it affects positioning accuracy

### 5. Scanning Control

You can stop and restart scanning as needed:

- Tap **Stop Scanning** to pause the scanning process
- Tap **Start Scanning** to resume
- Stopping the scan can help preserve battery if you're already seeing enough beacons

## Testing with Real Beacons

For accurate results with physical BLE beacons:

1. Place your BLE beacons at known positions in your environment
2. Turn on your beacons and make sure they're broadcasting
3. Start scanning and select at least 3 beacons from the detected list
4. The position calculation will automatically begin once 3+ beacons are selected
5. Try moving around to test how accurately the system tracks your position

## Expected Behavior

- Beacon detection updates approximately every second
- Signals fluctuate naturally, which affects the distance estimation
- The position calculation becomes more stable as you get more readings from beacons
- The red circle on the visualization shows both your position and the estimated accuracy
- Moving toward or away from beacons should change their RSSI and your calculated position

## Troubleshooting

If you're having issues:

- Make sure Bluetooth is enabled on your device
- Verify you've granted the app both Location and Bluetooth permissions
- Try selecting different beacons if positioning seems inaccurate
- Adjust the Path Loss Exponent to better match your environment
- Restart scanning if beacons aren't appearing in the list