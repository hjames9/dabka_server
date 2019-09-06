package io.thehaydenplace.dabka.client

import io.netty.handler.codec.mqtt.MqttQoS
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.mqtt.MqttClient
import io.vertx.core.net.PemKeyCertOptions
import io.vertx.mqtt.MqttClientOptions

class MockClient (private val host: String, private val port: Int,
                  tlsKey: String, tlsCert: String) : Client {
    companion object {
        private val TAG = MockClient::class.java.simpleName
    }

    private val vertx = Vertx.vertx()
    private val client : MqttClient

    init {
        println("Created $TAG")

        val isSsl = tlsKey.isNotEmpty() && tlsCert.isNotEmpty()
        val options = MqttClientOptions().setSsl(isSsl)
        if(isSsl) {
            options.keyCertOptions = PemKeyCertOptions()
                    .setKeyValue(Buffer.buffer(tlsKey))
                    .setCertValue(Buffer.buffer(tlsCert))
        }
        client = MqttClient.create(vertx, options)
        doConnect()
    }

    private fun doConnect(success: () -> Unit = {}) {
        client.connect(port, host) {
            if(it.succeeded()) {
                println("MQTT client connected")
                success()
            }
        }
    }

    override fun sendMessage(topic: String, message: String)
    {
        val publish : () -> Unit = {
            client.publish(topic, Buffer.buffer(message), MqttQoS.AT_LEAST_ONCE, false, false)
        }

        if(!client.isConnected) {
            doConnect(publish)
        } else {
            publish()
        }
    }

    override fun close() {
        client.disconnect {
            if(it.succeeded()) {
                println("MQTT client disconnected")
            }
        }
    }
}