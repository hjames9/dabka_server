package io.thehaydenplace.dabka.server.retriever

import com.fasterxml.jackson.databind.ObjectMapper

data class SensorRequest(val id: String, val startTime: Long, val endTime: Long) {
    companion object {
        private val TAG = SensorRequest::class.java.simpleName
        private val mapper = ObjectMapper()

        fun createSensorRequestFromJson(json: String) : SensorRequest {
            return mapper.readValue(json, SensorRequest::class.java)
        }
    }

    constructor() : this("", 0, 0)

    fun convertSensorRequestToJson() : String {
        return mapper.writeValueAsString(this)
    }
}