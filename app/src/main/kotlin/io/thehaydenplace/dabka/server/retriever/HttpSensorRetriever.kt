package io.thehaydenplace.dabka.server.retriever

import io.thehaydenplace.dabka.server.dao.SensorDao
import io.thehaydenplace.dabka.server.dao.SensorData
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.http.ServerWebSocket
import java.util.concurrent.TimeUnit

class HttpSensorRetriever(sensorDao : SensorDao, private val path: String, private val port: Int) : SensorRetriever {
    companion object {
        private val TAG = HttpSensorRetriever::class.java.simpleName
        private const val FORWARD_FOR_HEADER = "X-Forwarded-For"
        private const val FORWARD_PROTO_HEADER = "X-Forwarded-Proto"
        private const val FORWARD_PORT_HEADER = "X-Forwarded-Port"
    }

    private val vertx = Vertx.vertx()
    private val httpServer : HttpServer
    private val sockets = mutableMapOf<String, ServerWebSocket>()

    init {
        println("Created $TAG")

        val options = HttpServerOptions()
        options.port = port

        httpServer = vertx.createHttpServer(options)

        httpServer.websocketHandler{socket ->
            if(path == socket.path()) {
                socket.handler { buffer ->
                    try {
                        val origin = getOrigin(socket)
                        val id = getRequestId(buffer)

                        if (!sockets.containsKey(origin)) {
                            println("Received connection from $origin for $id, sending sensor data updates")
                            sockets[origin] = socket

                            socket.closeHandler {
                                println("Connection from $origin closing, stopping sensor data updates")
                                sensorDao.stopRealtimeSensorData(id, origin)
                                sockets.remove(origin)
                            }

                            //Get initial data set
                            val startTime = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(600)
                            val endTime = System.currentTimeMillis()
                            sensorDao.getSensorData(id, startTime, endTime) { _, sensorDatas ->
                                processSensorUpdates(origin, sensorDatas)
                            }

                            //Get data updates
                            sensorDao.startRealtimeSensorData(id, origin,
                                    { _, sensorDatas -> processSensorUpdates(origin, sensorDatas) },
                                    { _, sensorDatas -> processSensorUpdates(origin, sensorDatas, true) })
                        } else {
                            System.err.println("Socket from $origin already receiving sensor updates")
                        }
                    } catch(exc: Exception) {
                        System.err.println("Exception received ${exc.message}")
                    }

                    socket.exceptionHandler {
                        println("Socket exception handler hit ${it.cause?.message}")
                    }
                }
            } else {
                socket.reject()
            }
        }

        httpServer.listen(port) {
            println("$TAG started listening on $port")
        }
    }

    private fun processSensorUpdates(origin: String, sensorDatas: List<SensorData>, near: Boolean = false) {
        val socket = sockets[origin]
        for(sensorData in sensorDatas) {
            socket?.writeTextMessage(sensorData.convertSensorDataToJson())
        }
    }

    private fun getRequestId(buffer: Buffer) : String {
        val request = buffer.getString(0, buffer.length())
        val sensorRequest = SensorRequest.createSensorRequestFromJson(request)
        return sensorRequest.id
    }

    private fun getOrigin(socket: ServerWebSocket) : String {
        val headers = socket.headers()

        val forHeader = headers[FORWARD_FOR_HEADER]
        val protoHeader = headers[FORWARD_PROTO_HEADER]
        val portHeader = headers[FORWARD_PORT_HEADER]

        return if (null != forHeader && null != protoHeader && null != portHeader)
            "$protoHeader://$forHeader:$portHeader"
        else
            "${socket.remoteAddress().host()}:${socket.remoteAddress().port()}"

    }

    override fun close() {
        httpServer.close {
            println("$TAG closed listening on $port")
        }

        val iterator = sockets.iterator()
        while(iterator.hasNext()) {
            iterator.next().value.close()
            iterator.remove()
        }

        println("Destroyed $TAG")
    }
}