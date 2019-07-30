package io.thehaydenplace.dabka.client

import java.io.Closeable

interface Client : Closeable, AutoCloseable {
    fun sendMessage(topic: String, message: String)
}