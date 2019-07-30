package io.thehaydenplace.dabka.server.dao

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper

data class Location(var latitude: Double, var longitude: Double) {
    constructor() : this(0.0, 0.0)
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SensorData(var id: String, var deviceTimestamp: Long,
                      var serverTimestamp: Long, var processTimestamp: Long,
                      var location: Location) {
    companion object {
        private val TAG = SensorData::class.java.simpleName
        private val mapper = ObjectMapper()

        fun createSensorDataFromJson(json: String) : SensorData {
            return mapper.readValue(json, SensorData::class.java)
        }
    }

    constructor() : this("", 0, 0, 0, Location(0.0, 0.0))

    fun convertSensorDataToJson() : String {
        return mapper.writeValueAsString(this)
    }

    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }
}
