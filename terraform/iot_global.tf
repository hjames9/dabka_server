/*
 * IOT global configuration 
 */

resource "aws_iot_thing_type" "collar" {
    name = "collar"
}

resource "aws_iot_policy" "collar_pubsub" {
    name = "CollarPubSub"

    policy = <<EOF
{
        "Version": "2012-10-17",
        "Statement": [ {
            "Action": ["iot:*"],
            "Effect": "Allow",
            "Resource": "*"
            }
        ]
}
EOF
}

resource "aws_iam_role" "collar_iot_iam_role" {
    name = "collar_iot_iam_role"

    assume_role_policy = <<EOF
{
        "Version": "2012-10-17",
        "Statement": [{
            "Effect": "Allow",
            "Principal": {
                "Service": "iot.amazonaws.com"
            },
            "Action": "sts:AssumeRole"
        }]
}
EOF
}

resource "aws_iam_role_policy" "collar_iot_iam_role_policy" {
    name = "collar_iot_iam_role_policy"
    role = "${aws_iam_role.collar_iot_iam_role.id}"

    policy = <<EOF
{
        "Version": "2012-10-17",
        "Statement": [{
            "Effect": "Allow",
            "Action": [
                "sqs:SendMessage"
            ],
            "Resource": "*"
        }]
}
EOF
}

resource "aws_iot_topic_rule" "gps_sensor_rule" {
    name        = "gps_sensor_rule"
    description = "MQTT topic for GPS and sensor messages"
    enabled     = true
    sql         = "SELECT * FROM 'collar/events'"
    sql_version = "2016-03-23"

    sqs {
        queue_url  = "https://sqs.us-east-1.amazonaws.com/756579151825/collar_queue"
        role_arn   = "${aws_iam_role.collar_iot_iam_role.arn}"
        use_base64 = false
    }
}
