package io.thehaydenplace.dabka.server.retriever

import io.thehaydenplace.dabka.server.dao.SensorDao
import io.thehaydenplace.dabka.server.dao.SensorData
import io.vertx.core.MultiMap
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.*
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.net.SocketAddress
import io.vertx.ext.web.Router
import java.util.concurrent.TimeUnit

class HttpSensorRetriever(private val sensorDao : SensorDao, private val path: String,
                          private val updatePath: String, private val port: Int) : SensorRetriever {
    companion object {
        private val TAG = HttpSensorRetriever::class.java.simpleName
        private const val FORWARD_FOR_HEADER = "X-Forwarded-For"
        private const val FORWARD_PROTO_HEADER = "X-Forwarded-Proto"
        private const val FORWARD_PORT_HEADER = "X-Forwarded-Port"
        private const val CONTENT_TYPE_HEADER = "Content-Type"
        private const val JSON_CONTENT_TYPE = "application/json"
    }

    private val vertx = Vertx.vertx()
    private val httpServer : HttpServer
    private val sockets = mutableMapOf<String, ServerWebSocket>()

    init {
        println("Created $TAG")

        val options = HttpServerOptions()
        options.port = port

        httpServer = vertx.createHttpServer(options)

        configureWebSocketHandler()
        configureRestApiHandler()

        httpServer.listen(port) {
            println("$TAG started listening on $port")
        }
    }

    private fun configureWebSocketHandler() {
        httpServer.websocketHandler{socket ->
            if(updatePath == socket.path()) {
                socket.handler { buffer ->
                    try {
                        val origin = getOriginFromProxyHeadersOrAddress(socket.headers(), socket.remoteAddress())
                        val sensorRequest = getSensorRequest(buffer)
                        val id = sensorRequest.id

                        if (!sockets.containsKey(origin)) {
                            println("Received connection from $origin for $id, sending sensor data updates")
                            sockets[origin] = socket

                            socket.closeHandler {
                                println("Connection from $origin closing, stopping sensor data updates")
                                sensorDao.stopRealtimeSensorData(id, origin)
                                sockets.remove(origin)
                            }

                            //Get initial data set
                            sensorDao.getSensorData(id, sensorRequest.startTime, sensorRequest.endTime) { _, sensorDatas ->
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
    }

    private fun configureRestApiHandler() {
        val router = Router.router(vertx)
        router.route(path).handler {routingContext ->
            val request = routingContext.request()
            val sensorRequest = getSensorRequest(request)
            val origin = getOriginFromProxyHeadersOrAddress(request.headers(), request.remoteAddress())

            val response = routingContext.response()
            response.putHeader(CONTENT_TYPE_HEADER, JSON_CONTENT_TYPE)

            if(validSensorRequest(sensorRequest)) {
                sensorDao.getSensorData(sensorRequest.id, sensorRequest.startTime, sensorRequest.endTime) { _, sensorDatas ->
                    println("Sending sensor data to $origin")
                    val sensorDataArray = JsonArray()
                    sensorDatas.forEach { sensorData ->
                        sensorDataArray.add(JsonObject(sensorData.convertSensorDataToJson()))
                    }
                    response.end(sensorDataArray.encode())
                }
            } else {
                val errMsg = "Invalid query parameters startTime(${sensorRequest.startTime}), endTime(${sensorRequest.endTime}) id(${sensorRequest.id})"
                val errorJson = JsonObject("{\"error\": \"$errMsg\"}")
                response.statusCode = 400
                response.end(errorJson.encode())
            }
        }
        httpServer.requestHandler(router)
    }

    private fun validSensorRequest(sensorRequest: SensorRequest) : Boolean {
        val validParameters = (sensorRequest.endTime != 0L && sensorRequest.startTime != 0L && sensorRequest.id != "")
        val withinTimeRange = (sensorRequest.endTime - sensorRequest.startTime) <= TimeUnit.DAYS.toMillis(7)
        return validParameters && withinTimeRange
    }

    private fun processSensorUpdates(origin: String, sensorDatas: List<SensorData>, near: Boolean = false) {
        val socket = sockets[origin]
        for(sensorData in sensorDatas) {
            socket?.writeTextMessage(sensorData.convertSensorDataToJson())
        }
    }

    private fun getSensorRequest(buffer: Buffer) : SensorRequest {
        val request = buffer.getString(0, buffer.length())
        return SensorRequest.createSensorRequestFromJson(request)
    }

    private fun getSensorRequest(request: HttpServerRequest) : SensorRequest {
        val id = request.getParam("id") ?: ""
        val startTime = request.getParam("startTime")?.toLong() ?: 0
        val endTime = request.getParam("endTime")?.toLong() ?: 0
        return SensorRequest(id, startTime, endTime)
    }

    private fun getOriginFromProxyHeadersOrAddress(headers: MultiMap, address: SocketAddress) : String {
        return if (headers.contains(FORWARD_FOR_HEADER) && headers.contains(FORWARD_PROTO_HEADER) && headers.contains(FORWARD_PORT_HEADER))
            getOriginFromProxyHeaders(headers)
        else
            getOriginFromAddress(address)
    }

    private fun getOriginFromProxyHeaders(headers: MultiMap) : String {
        val forHeader = headers[FORWARD_FOR_HEADER]
        val protoHeader = headers[FORWARD_PROTO_HEADER]
        val portHeader = headers[FORWARD_PORT_HEADER]
        return "$protoHeader://$forHeader:$portHeader"
    }

    private fun getOriginFromAddress(address: SocketAddress) : String {
        return "${address.host()}:${address.port()}"
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