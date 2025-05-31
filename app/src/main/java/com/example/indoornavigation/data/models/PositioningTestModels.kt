package com.example.indoornavigation.data.models

data class TestPosition(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val floor: Int = 0,
    val accuracy: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)

data class TestBeacon(
    val id: String,
    val name: String = "",  
    val rssi: Int,
    val distance: Double,
    val timestamp: Long = System.currentTimeMillis()
)

data class TestWiFi(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class TestSensor(
    val type: String,
    val values: FloatArray,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TestSensor

        if (type != other.type) return false
        if (!values.contentEquals(other.values)) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + values.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

data class PositioningState(
    val currentPosition: TestPosition = TestPosition(),
    val blePosition: TestPosition? = null,
    val wifiPosition: TestPosition? = null,
    val deadReckoningPosition: TestPosition? = null,
    val fusedPosition: TestPosition? = null,
    val detectedBeacons: List<TestBeacon> = emptyList(),
    val detectedWiFi: List<TestWiFi> = emptyList(),
    val sensorData: Map<String, TestSensor> = emptyMap(),
    val isScanning: Boolean = false,
    val activeSourcesMask: Int = 0
)

object PositioningSources {
    const val BLE = 1
    const val WIFI = 2
    const val DEAD_RECKONING = 4
    const val SENSOR_FUSION = 8
}