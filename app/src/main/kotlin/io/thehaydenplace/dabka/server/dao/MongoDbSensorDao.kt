package io.thehaydenplace.dabka.server.dao

import com.mongodb.MongoClientOptions
import com.mongodb.MongoCredential
import com.mongodb.ServerAddress
import com.mongodb.client.MongoCursor
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.BulkOperation
import io.vertx.ext.mongo.FindOptions
import io.vertx.ext.mongo.MongoClient
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.changestream.ChangeStreamDocument
import org.bson.Document
import java.io.Closeable
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

class MongoDbSensorDao(private val host: String, port: Int, username: String, password: String,
                       private val dbName: String, private val authDbName: String,
                       ssl: Boolean, srv: Boolean) : SensorDao {
    companion object {
        private val TAG = MongoDbSensorDao::class.java.simpleName
        private const val COLLECTION_NAME = "sensorDatas"
        private const val SRV_PREFIX = "_mongodb._tcp."
    }

    data class UpdateCursors(private val id: String,
                             private val updateCursor: MongoCursor<ChangeStreamDocument<Document>>,
                             private val nearUpdateCursor: MongoCursor<ChangeStreamDocument<Document>>,
                             val updateCallbacks: ConcurrentMap<String, SensorUpdates>,
                             val nearUpdateCallbacks: ConcurrentMap<String, SensorUpdates>) : Closeable {
        private val lock = ReentrantLock(true)
        private val closed = AtomicBoolean(false)

        fun process() : Boolean {
            if(closed.get()) {
                return false
            }
            try {
                //TODO: Replace nearUpdateCursor on data from updateCursor
                lock.lock()
                return processCursor(updateCursor, updateCallbacks) && processCursor(nearUpdateCursor, nearUpdateCallbacks)
            } finally {
                lock.unlock()
            }
        }

        private fun processCursor(cursor: MongoCursor<ChangeStreamDocument<Document>>, callbacks: Map<String, SensorUpdates>) : Boolean {
            var res = false

            val document = cursor.tryNext()
            if(null != document) {
                println(document)
                println(document.fullDocument)
                println(document.fullDocument?.toJson())

                //Mutate mongdb timestamp fields
                val json = JsonObject(document.fullDocument?.toJson())
                val deviceTimestamp = json.getJsonObject("deviceTimestamp").getString("\$numberLong").toLong()
                val serverTimestamp = json.getJsonObject("serverTimestamp").getString("\$numberLong").toLong()
                val processTimestamp = json.getJsonObject("processTimestamp").getString("\$numberLong").toLong()
                json.remove("deviceTimestamp")
                json.remove("serverTimestamp")
                json.remove("processTimestamp")
                json.put("deviceTimestamp", deviceTimestamp)
                json.put("serverTimestamp", serverTimestamp)
                json.put("processTimestamp", processTimestamp)

                val sensorData = SensorData.createSensorDataFromJson(json.encode())
                val sensorDatas = mutableListOf(sensorData)
                for(callback in callbacks) {
                    callback.value(id, sensorDatas)
                }
                res = true
            }
            return res
        }

        override fun close() {
            try {
                lock.lock()
                closed.set(true)
                updateCursor.close()
                nearUpdateCursor.close()
            } finally {
                lock.unlock()
            }
        }
    }

    private val vertx = Vertx.vertx()
    private lateinit var client : MongoClient

    private lateinit var nativeClient : com.mongodb.MongoClient
    private val updateCursorsMap = ConcurrentHashMap<String, UpdateCursors>()
    private val updatesRunning = AtomicBoolean(false)
    private val updatesThread = Thread {
        println("Started processing MongoDb cursors")
        while(updatesRunning.get()) {
            val iterator = updateCursorsMap.iterator()
            if(!iterator.hasNext()) {
                Thread.sleep(1000)
                continue
            }
            while(iterator.hasNext()) {
                val updateCursorsEntry = iterator.next()
                val updateCursors = updateCursorsEntry.value
                if(!updateCursors.process()) {
                    //No data returned, sleep for short period time
                    Thread.sleep(1000)
                }
            }
        }
        println("Stopped processing MongoDb cursors")
    }

    init {
        println("Created $TAG")

        val config = JsonObject()
        val nativeClientOptions = MongoClientOptions.builder()

        if(srv) {
            getDnsInfo {jsonHosts, serverAddresses ->
                config.put("hosts", jsonHosts)
                config.put("username", username)
                config.put("password", password)
                config.put("db_name", dbName)
                config.put("ssl", true)
                config.put("authSource", authDbName)
                System.setProperty("org.mongodb.async.type", "netty")

                client = MongoClient.createShared(vertx, config)
                nativeClient = com.mongodb.MongoClient(serverAddresses,
                        MongoCredential.createCredential(username, authDbName, password.toCharArray()),
                        nativeClientOptions.sslEnabled(true).build())

                createIndex()
            }
        } else {
            config.put("host", host)
            config.put("port", port)
            config.put("username", username)
            config.put("password", password)
            config.put("db_name", dbName)
            config.put("ssl", ssl)

            client = MongoClient.createShared(vertx, config)
            nativeClient = com.mongodb.MongoClient(ServerAddress(host, port),
                    MongoCredential.createCredential(username, authDbName, password.toCharArray()),
                    nativeClientOptions.sslEnabled(ssl).build())

            createIndex()
        }

        updatesRunning.set(true)
        updatesThread.start()
    }

    override fun insertSensorData(sensorDatas: List<SensorData>) {
        val bulkOperations = mutableListOf<BulkOperation>()

        for (sensorData in sensorDatas) {
            println("$sensorData")

            val sensorDataStr = sensorData.convertSensorDataToJson()
            val sensorDataJson = JsonObject(sensorDataStr)
            bulkOperations.add(BulkOperation.createInsert(sensorDataJson))
        }

        client.bulkWrite(COLLECTION_NAME, bulkOperations) {
            if(it.succeeded()) {
                println("Persisted ${it.result().insertedCount} items")
            } else {
                println("Error persisting sensor data ${it.cause()}")
            }
        }
    }

    override fun canConnect(cb: (result: Boolean) -> Unit) {
        client.runCommand("ping", JsonObject("{\"ping\":1}")) {
            cb(it.succeeded())
        }
    }

    override fun startRealtimeSensorData(id: String, origin: String, userUpdates: SensorUpdates, nearUserUpdates: SensorUpdates) {
        var updateCursors = updateCursorsMap[id]

        if(null == updateCursors) {
            val db = nativeClient.getDatabase(dbName)
            val collection = db.getCollection(COLLECTION_NAME)

            val updatePipeline = listOf(Aggregates.match(Document.parse("{'fullDocument.id': '$id'}")))
            val updateCursor = collection.watch(updatePipeline).iterator()

            val nearUpdatePipeline = listOf(Aggregates.match(Document.parse("{'fullDocument.id': 'dummy'}")))
            val nearUpdateCursor = collection.watch(nearUpdatePipeline).iterator()

            val updateUpdateCallbacks = ConcurrentHashMap<String, SensorUpdates>()
            val nearUpdateCallbacks = ConcurrentHashMap<String, SensorUpdates>()

            updateCursors = UpdateCursors(id, updateCursor, nearUpdateCursor, updateUpdateCallbacks, nearUpdateCallbacks)
            updateCursorsMap[id] = updateCursors
        }

        updateCursors.updateCallbacks[origin] = userUpdates
        updateCursors.nearUpdateCallbacks[origin] = nearUserUpdates
    }

    override fun stopRealtimeSensorData(id: String, origin: String) {
        val updateCursors = updateCursorsMap[id]

        updateCursors?.updateCallbacks?.remove(origin)
        updateCursors?.nearUpdateCallbacks?.remove(origin)

        val updateCallbacksEmpty = updateCursors?.updateCallbacks?.isEmpty()
        val nearUpdateCallbacksEmpty = updateCursors?.nearUpdateCallbacks?.isEmpty()

        if(updateCallbacksEmpty == false && nearUpdateCallbacksEmpty == false) {
            updateCursors.close()
            updateCursorsMap.remove(id)
        }
    }

    override fun getSensorData(id: String, startTime: Long, endTime: Long, cb: SensorUpdates) {
        val timeQuery = JsonObject()
        timeQuery.put("\$gte", startTime)
        timeQuery.put("\$lte", endTime)

        val query = JsonObject()
        query.put("serverTimestamp", timeQuery)
        query.put("id", id)

        //TODO return near to id

        val findOptions = FindOptions().setSort(JsonObject("{\"serverTimestamp\": 1}"))
        client.findWithOptions(COLLECTION_NAME, query, findOptions) {
            if(it.succeeded()) {
                println("Successfully executed sensor data query for time range $startTime and $endTime")
                val sensorDatas = mutableListOf<SensorData>()
                for(jsonObject in it.result()) {
                    sensorDatas.add(SensorData.createSensorDataFromJson(jsonObject.toString()))
                }
                cb(id, sensorDatas)
            } else {
                System.err.println(it.cause().message)
            }
        }
    }

    override fun close() {
        updatesRunning.set(false)
        updatesThread.join()

        val iterator = updateCursorsMap.iterator()
        while(iterator.hasNext()) {
            val updateCursors = iterator.next().value
            updateCursors.close()
            iterator.remove()
        }

        nativeClient.close()
        client.close()

        println("Destroyed $TAG")
    }

    private fun createIndex() {
        client.createIndex(COLLECTION_NAME, JsonObject("{ \"location\": \"2dsphere\" }")) {
            if(it.succeeded()) {
                println("Successfully created location index on $COLLECTION_NAME")
            } else {
                println("Location index on $COLLECTION_NAME is available")
            }
        }

        client.createIndex(COLLECTION_NAME, JsonObject("{ \"serverTimestamp\": 1 }")) {
            if(it.succeeded()) {
                println("Successfully created timestamp index on $COLLECTION_NAME")
            } else {
                println("Timestamp index on $COLLECTION_NAME is available")
            }
        }
    }

    private fun getDnsInfo(cb: (jsonArray: JsonArray, addresses: List<ServerAddress>) -> Unit) {
        val dnsClient = vertx.createDnsClient()
        dnsClient.resolveSRV("$SRV_PREFIX$host", {
            if(it.succeeded()) {
                val jsonArray = JsonArray()
                val addresses = mutableListOf<ServerAddress>()

                val res = it.result()
                for(srvRecord in res) {
                    val host = JsonObject()
                    host.put("host", srvRecord.target())
                    host.put("port", srvRecord.port())
                    jsonArray.add(host)
                    addresses.add(ServerAddress(srvRecord.target(), srvRecord.port()))
                }
                cb(jsonArray, addresses)
            } else {
                System.err.println(it.cause().message)
            }
        })
    }
}
