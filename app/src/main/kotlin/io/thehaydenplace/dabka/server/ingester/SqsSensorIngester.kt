package io.thehaydenplace.dabka.server.ingester

import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.amazonaws.services.sqs.model.SetQueueAttributesRequest
import io.thehaydenplace.dabka.server.dao.SensorDao
import io.thehaydenplace.dabka.server.dao.SensorData
import java.util.concurrent.atomic.AtomicBoolean

class SqsSensorIngester(queueName : String, private val sensorDao : SensorDao) : SensorIngester {
    companion object {
        private val TAG = SqsSensorIngester::class.java.simpleName
    }

    private val sqs = AmazonSQSClientBuilder.defaultClient()
    private val queueUrl = sqs.getQueueUrl(queueName).queueUrl
    private val running = AtomicBoolean(false)

    private val sqsThread = Thread {
        val receiveMessageRequest = ReceiveMessageRequest()
                .withQueueUrl(queueUrl)
                .withMaxNumberOfMessages(10)
                .withWaitTimeSeconds(20)
                .withAttributeNames("All")

        while(running.get()) {
            try {
                val messages = sqs.receiveMessage(receiveMessageRequest).messages
                println("Received ${messages.size} messages")

                if(messages.size <= 0) {
                    println("No messages received, no processing")
                    continue
                }

                val sensorDatas = mutableListOf<SensorData>()

                messages.forEach { message ->
                    println("$message.messageId: ${message.body}")

                    val sensorData = SensorData.createSensorDataFromJson(message.body)
                    val attributes = message.attributes

                    sensorData.serverTimestamp = attributes["SentTimestamp"]?.toLong() ?: 0
                    sensorData.processTimestamp = attributes["ApproximateFirstReceiveTimestamp"]?.toLong() ?: 0
                    sensorDatas.add(sensorData)
                }

                sensorDao.insertSensorData(sensorDatas)

                messages.forEach { message ->
                    sqs.deleteMessage(queueUrl, message.receiptHandle)
                }
            } catch(exc: Exception) {
                System.err.println("Received SQS error: ${exc.message}")
            }
        }
    }

    init {
        println("Created $TAG")

        sensorDao.canConnect { result ->
            if(result) {
                val queueAttrsRequest = SetQueueAttributesRequest().withQueueUrl(queueUrl)
                        .addAttributesEntry("ReceiveMessageWaitTimeSeconds", "20")

                sqs.setQueueAttributes(queueAttrsRequest)

                println("Starting SQS read thread")
                running.set(true)
                sqsThread.start()
            } else {
                throw IllegalStateException("Unable to connect to datastore")
            }
        }
    }

    override fun close() {
        running.set(false)
        sqsThread.join()
        println("Destroyed $TAG")
    }
}
