# Indoor Navigation App

This Android application provides indoor navigation functionality with floor plans, POI search, and
route visualization.

## Floor Plans

The app uses XML vector drawables for floor plans, which allows for better scaling and interactions
compared to bitmap images.

### Floor Plan Structure

- Ground Floor (G) - First floor of the building
- First Floor (1) - Second floor of the building
- Second Floor (2) - Third floor of the building

### Features Implemented

- Floor plan visualization with Ground/1st/2nd floor switching
- POI (Point of Interest) markers on each floor
- POI search functionality
- Route visualization between POIs
- Map legend for understanding floor plan symbols
- User position visualization

## Future Development

### Firebase Integration

The app is prepared for Firebase integration:

- Store floor plan metadata in Firestore
- Upload and download floor plan images from Firebase Storage
- Authenticate users with Firebase Auth

To implement Firebase backend:

1. Create a Firebase project
2. Add an Android app to your Firebase project
3. Download `google-services.json` and add it to the app directory
4. Follow the instructions in `FirebaseFloorPlanProvider.kt` to implement actual data fetching

### Enhanced POI Search

To improve the POI search functionality:

1. Add more detailed POI metadata in Firestore
2. Implement fuzzy search for better results
3. Add autocomplete functionality based on existing POIs

### Improved Route Visualization

To enhance pathfinding functionality:

1. Implement A* pathfinding algorithm in `NavigationService.kt`
2. Create a more detailed navigation graph for each floor
3. Add transition points between floors (elevators, stairs)
4. Store navigation graph data in Firestore

### Custom Legend Implementation

To improve the map legend:

1. Create a separate component for displaying the legend
2. Use a RecyclerView to show legend items dynamically
3. Store legend data in Firestore or local database