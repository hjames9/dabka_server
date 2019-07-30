package io.thehaydenplace.dabka.server.dao

import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.asyncsql.PostgreSQLClient
import io.vertx.ext.sql.SQLClient
import java.util.*

class PostgresqlSensorDao(host: String, port: Int, username: String, password: String, dbName: String) : SensorDao {
    companion object {
        private val TAG = PostgresqlSensorDao::class.java.simpleName

        private const val CREATE_SCHEMA_SQL = "CREATE SCHEMA IF NOT EXISTS dabka"
        private const val CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS dabka.sensorData (id UUID NOT NULL PRIMARY KEY, messages JSONB NOT NULL, created_at TIMESTAMP NOT NULL)"
        private const val CREATE_INDEX_SQL = "CREATE INDEX IF NOT EXISTS sd_msg_idx ON dabka.sensorData USING GIN(messages)"
        private const val INSERT_SQL = "INSERT INTO dabka.sensorData (id, messages, created_at) VALUES"
        private const val INSERT_PLACEHOLDER = "(?, ?, to_timestamp(?))"
    }

    private val vertx = Vertx.vertx()
    private val client : SQLClient

    init {
        println("Created $TAG")

        val config = JsonObject()
        config.put("host", host)
        config.put("port", port)
        config.put("username", username)
        config.put("password", password)
        config.put("database", dbName)
        config.put("sslMode", "require")

        client = PostgreSQLClient.createShared(vertx, config)
        createDbObject(CREATE_SCHEMA_SQL) {
            createDbObject(CREATE_TABLE_SQL) {
                createDbObject(CREATE_INDEX_SQL)
            }
        }
    }

    override fun insertSensorData(sensorDatas: List<SensorData>) {
        println("Persisting new sensor data")

        client.getConnection { res ->
            if(res.succeeded()) {
                val conn = res.result()
                try {
                    val jsonArray = JsonArray()
                    for (sensorData in sensorDatas) {
                        jsonArray.add("${UUID.randomUUID()}")
                        jsonArray.add(sensorData.convertSensorDataToJson())
                        jsonArray.add(System.currentTimeMillis())
                    }

                    val insertSql = createInsert(sensorDatas.size)

                    conn.updateWithParams(insertSql, jsonArray) {
                        if (it.succeeded()) {
                            val result = it.result()
                            println("Rows inserted ${result.updated}")
                        } else {
                            System.err.println("Insert not successful: ${it.cause()}")
                        }
                    }
                } catch(exc: Exception) {
                    System.err.println(exc.message)
                    exc.printStackTrace()
                } finally {
                    conn.close()
                }
            } else {
                System.err.println("Could not get database connection")
            }
        }
    }

    override fun canConnect(cb : (result : Boolean) -> Unit) {
        client.getConnection { res ->
            val conn = res.result()
            conn.use {
                cb(res.succeeded())
            }
        }
    }

    override fun startRealtimeSensorData(id: String, origin: String, userUpdates: SensorUpdates, nearUserUpdates: SensorUpdates) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun stopRealtimeSensorData(id: String, origin: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getSensorData(id: String, startTime: Long, endTime: Long, cb: SensorUpdates) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun close() {
        client.close()
        println("Destroyed $TAG")
    }

    private fun createDbObject(sql : String, success : () -> Unit = {}) {
        client.getConnection { res ->
            if (res.succeeded()) {
                val conn = res.result()
                try {
                    conn.execute(sql) {
                        if (it.succeeded()) {
                            println("Object creation succeeded")
                            conn.close {
                                success()
                            }
                        } else {
                            System.err.println("Object creation was NOT successful")
                        }
                    }
                } catch(exc: Exception) {
                    System.err.println(exc.message)
                    exc.printStackTrace()
                } finally {
                    conn.close()
                }
            } else {
                System.err.println("Could not get database connection: ${res.cause().message}")
            }
        }
    }

    private fun createInsert(count: Int) : String {
        val sb = StringBuilder(INSERT_SQL)
        for(iter in 1..count) {
            sb.append(' ')
            sb.append(INSERT_PLACEHOLDER)
            if(iter != count) {
                sb.append(',')
            }
        }
        return sb.toString()
    }
}
