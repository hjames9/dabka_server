package io.thehaydenplace.dabka.client

import io.thehaydenplace.dabka.util.bootstrap.Booter
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

class ClientBooter(properties : Properties) : Booter(properties) {
    companion object {
        private val TAG = ClientBooter::class.java.simpleName
    }

    private lateinit var client : Client
    private lateinit var clientRunner : ClientRunner

    init {
        println("Created $TAG")
    }

    override fun run() {
        client = createMockClient()
        clientRunner = createMockClientRunner()
    }

    override fun close() {
        //TODO: Putting proper waiting logic here
        if(!clientRunner.isRunning()) {
            client.close()
        }
        println("Destroyed $TAG")
    }

    @Throws(IllegalArgumentException::class)
    private fun createMockClientRunner() : MockClientRunner {
        val topic = properties.getProperty("mqtt.publish.topic") ?: throw IllegalArgumentException("mqtt.publish.topic property not set")
        val interval = properties.getProperty("mqtt.message.interval") ?: throw IllegalArgumentException("mqtt.message.interval property not set")
        val count = properties.getProperty("mqtt.message.count") ?: throw IllegalArgumentException("mqtt.message.count property not set")

        val message = properties.getProperty("mqtt.publish.message.steps")
                ?: properties.getProperty("mqtt.publish.message")
                ?: throw IllegalArgumentException("mqtt.publish.message or mqtt.publish.message.steps property not set")

        val steps = properties.containsKey("mqtt.publish.message.steps")

        return MockClientRunner(client, topic, message, Integer.parseInt(count), Integer.parseInt(interval), steps)
    }

    @Throws(IllegalArgumentException::class)
    private fun createMockClient() : MockClient {
        val host = properties.getProperty("mqtt.server.host") ?: throw IllegalArgumentException("mqtt.server.host not set")
        val port = Integer.parseInt(properties.getProperty("mqtt.server.port", "8883"))
        val tlsKeyPath = properties.getProperty("mqtt.server.tls.client.key") ?: throw IllegalArgumentException("mqtt.server.tls.client.key property not set")
        val tlsCertPath = properties.getProperty("mqtt.server.tls.client.cert") ?: throw IllegalArgumentException("mqtt.server.tls.client.cert property not set")

        val tlsKey = String(Files.readAllBytes(Paths.get(tlsKeyPath)))
        val tlsCert = String(Files.readAllBytes(Paths.get(tlsCertPath)))

        return MockClient(host, port, tlsKey, tlsCert)
    }
}