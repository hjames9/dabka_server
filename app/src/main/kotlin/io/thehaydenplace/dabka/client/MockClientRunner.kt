package io.thehaydenplace.dabka.client

import io.vertx.core.json.JsonObject
import java.util.*


class MockClientRunner(private val client: Client, private val topic: String,
                       private val message: String, private val count: Int,
                       private val interval: Int, private val steps: Boolean) : ClientRunner {
    companion object {
        private val TAG = MockClientRunner::class.java.simpleName
        private const val EARTH_RADIUS = 6378137
    }

    private var running = false
    private val random = Random()

    init {
        println("Created $TAG")
        sendMessages()
    }
    override fun sendMessages() {
        try {
            running = true
            var currentMessage = message
            for (iter in 1..count) {
                val metaMessage = when(steps) {
                    true -> createStepsMessage(currentMessage, iter)
                    false -> message
                }

                Thread.sleep(interval * 1000L)
                client.sendMessage(topic, metaMessage)
                println("Sent message($iter): $metaMessage")
                currentMessage = metaMessage
            }
        } finally {
            running = false
        }
    }

    override fun isRunning(): Boolean {
        return running
    }

    private fun createStepsMessage(message: String, step: Int) : String {
        val json = JsonObject(message)
        json.put("deviceTimestamp", System.currentTimeMillis())

        if(step != 1) {
            val location = json.getJsonObject("location")
            val coords = moveLatitudeOrLongitude(location.getDouble("longitude"),
                    location.getDouble("latitude"), step.toDouble(), random.nextBoolean())

            location.put("latitude", coords.first)
            location.put("longitude", coords.second)
        }

        return json.encode()
    }

    private fun moveLatitudeOrLongitude(longitude: Double, latitude: Double, distanceMeters: Double, lat: Boolean) : Pair<Double, Double> {
        val dn : Double = when(lat) {
            true -> distanceMeters
            false -> 0.0
        }

        val de : Double = when(lat) {
            true -> 0.0
            false -> distanceMeters
        }

        val dLat = dn / EARTH_RADIUS
        val dLon = de / (EARTH_RADIUS * Math.cos(Math.PI * (latitude / 180)))

        return Pair(latitude + dLat * 180 / Math.PI, longitude + dLon * 180 / Math.PI)
    }
}