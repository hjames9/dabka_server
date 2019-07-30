package io.thehaydenplace.dabka.client

interface ClientRunner {
    fun sendMessages()
    fun isRunning() : Boolean
}