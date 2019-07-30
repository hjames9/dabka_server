/*
 * IOT things configuration 
 */

//Collar0
resource "aws_iot_thing" "collar0" {
    name            = "collar0"
    thing_type_name = "collar"
}

resource "aws_iot_certificate" "cert_collar0" {
    csr    = "${file("csr_collar0.pem")}"
    active = true
}

resource "aws_iot_thing_principal_attachment" "att_collar0" {
    principal = "${aws_iot_certificate.cert_collar0.arn}"
    thing     = "${aws_iot_thing.collar0.name}"
}

resource "aws_iot_policy_attachment" "patt_collar0" {
    policy = "${aws_iot_policy.collar_pubsub.name}"
    target = "${aws_iot_certificate.cert_collar0.arn}"
}

//Collar1
resource "aws_iot_thing" "collar1" {
    name            = "collar1"
    thing_type_name = "collar"
}

resource "aws_iot_certificate" "cert_collar1" {
    csr    = "${file("csr_collar1.pem")}"
    active = true
}

resource "aws_iot_thing_principal_attachment" "att_collar1" {
    principal = "${aws_iot_certificate.cert_collar1.arn}"
    thing     = "${aws_iot_thing.collar1.name}"
}

resource "aws_iot_policy_attachment" "patt_collar1" {
    policy = "${aws_iot_policy.collar_pubsub.name}"
    target = "${aws_iot_certificate.cert_collar1.arn}"
}
