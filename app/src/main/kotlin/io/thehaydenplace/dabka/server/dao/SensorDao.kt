package io.thehaydenplace.dabka.server.dao

import java.io.Closeable

typealias SensorUpdates = (String, List<SensorData>) -> Unit

interface SensorDao : Closeable, AutoCloseable {
    fun insertSensorData(sensorDatas: List<SensorData>)
    fun canConnect(cb : (result : Boolean) -> Unit)
    fun getSensorData(id: String, startTime: Long, endTime: Long, cb: SensorUpdates)
    fun startRealtimeSensorData(id: String, origin: String, userUpdates: SensorUpdates, nearUserUpdates: SensorUpdates)
    fun stopRealtimeSensorData(id: String, origin: String)
}