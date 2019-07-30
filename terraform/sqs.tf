/*
 * SQS queue configuration 
 */

resource "aws_sqs_queue" "collar_queue" {
    name                      = "collar_queue"
    receive_wait_time_seconds = 20
}

