package io.thehaydenplace.dabka.server.retriever

import io.thehaydenplace.dabka.server.dao.MongoDbSensorDao
import io.thehaydenplace.dabka.server.dao.PostgresqlSensorDao
import io.thehaydenplace.dabka.server.dao.SensorDao
import io.thehaydenplace.dabka.util.bootstrap.Booter
import java.util.*

class RetrieverBooter(properties: Properties) : Booter(properties) {
    companion object {
        private val TAG = RetrieverBooter::class.java.simpleName
    }

    private lateinit var sensorRetriever: SensorRetriever
    private lateinit var sensorDao : SensorDao

    init {
        println("Created $TAG")
    }

    override fun run() {
        val dbType = properties.getProperty("db.type") ?: throw IllegalArgumentException("db.type property not set")

        sensorDao = when("postgresql".equals(dbType, ignoreCase = true)) {
            true -> createPostgresqlSensorDao()
            false -> createMongoDbSensorDao()
        }

        sensorRetriever = createHttpSensorRetriever()
    }

    override fun close()
    {
        sensorRetriever.close()
        sensorDao.close()
        println("Destroyed $TAG")
    }

    @Throws(IllegalArgumentException::class)
    private fun createMongoDbSensorDao() : MongoDbSensorDao {
        val host = properties.getProperty("mongodb.host") ?: throw IllegalArgumentException("mongodb.host property not set")
        val port = properties.getProperty("mongodb.port", "27017") ?: throw IllegalArgumentException("mongodb.port property not set")
        val username = properties.getProperty("mongodb.username") ?: throw IllegalArgumentException("mongodb.username property not set")
        val password = properties.getProperty("mongodb.password") ?: throw IllegalArgumentException("mongodb.password property not set")
        val dbName = properties.getProperty("mongodb.db.name", "default_db") ?: throw IllegalArgumentException("mongodb.db.name property not set")
        val authDbName = properties.getProperty("mongodb.auth.db.name", "admin") ?: throw IllegalArgumentException("mongodb.auth.db.name property not set")
        val ssl = properties.getProperty("mongodb.ssl", "false")?.toBoolean()!!
        val srv = properties.getProperty("mongodb.srv", "false")?.toBoolean()!!

        return MongoDbSensorDao(host, Integer.parseInt(port), username, password, dbName, authDbName, ssl, srv)
    }

    @Throws(IllegalArgumentException::class)
    private fun createPostgresqlSensorDao() : PostgresqlSensorDao {
        val host = properties.getProperty("postgresql.host") ?: throw IllegalArgumentException("postgresql.host property not set")
        val port = properties.getProperty("postgresql.port", "5432") ?: throw IllegalArgumentException("postgresql.port property not set")
        val username = properties.getProperty("postgresql.username") ?: throw IllegalArgumentException("postgresql.username property not set")
        val password = properties.getProperty("postgresql.password") ?: throw IllegalArgumentException("postgresql.password property not set")
        val dbName = properties.getProperty("postgresql.db.name", "template1") ?: throw IllegalArgumentException("postgresql.db.name property not set")

        return PostgresqlSensorDao(host, Integer.parseInt(port), username, password, dbName)
    }

    @Throws(IllegalArgumentException::class)
    private fun createHttpSensorRetriever() : HttpSensorRetriever {
        val port = properties.getProperty("http.server.port", "8080") ?: throw IllegalArgumentException("http.server.port property not set")
        val path = properties.getProperty("http.server.websocket.path", "/sensorUpdates") ?: throw IllegalArgumentException("http.server.websocket.path property not set")

        return HttpSensorRetriever(sensorDao, path, Integer.parseInt(port))
    }
}