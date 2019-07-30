package io.thehaydenplace.dabka.client


class MockClientRunner(private val client: Client,
                       private val topic: String, private val message: String,
                       private val count: Int, private val interval: Int) : ClientRunner {
    companion object {
        private val TAG = MockClientRunner::class.java.simpleName
    }

    private var running = false

    init {
        println("Created $TAG")
        sendMessages()
    }
    override fun sendMessages() {
        try {
            running = true
            for (iter in 1..count) {
                Thread.sleep(interval * 1000L)
                client.sendMessage(topic, message)
                println("Sent message $iter")
            }
        } finally {
            running = false
        }
    }

    override fun isRunning(): Boolean {
        return running
    }
}