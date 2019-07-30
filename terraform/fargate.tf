/*
 * ECS Fargate configuration 
 */

resource "aws_iam_role" "dabka_ecs_ecr_iam_role" {
    name = "dabka_ecs_ecr_iam_role"

    assume_role_policy = <<EOF
{
        "Version": "2012-10-17",
        "Statement": [{
            "Effect": "Allow",
            "Principal": {
                "Service": "ecs.amazonaws.com"
            },
            "Action": "sts:AssumeRole"
        }]
}
EOF
}

resource "aws_iam_role_policy" "dabka_ecs_ecr_iam_role_policy" {
    name = "dabka_ecs_ecr_iam_role_policy"
    role = "${aws_iam_role.dabka_ecs_ecr_iam_role.id}"

    policy = <<EOF
{
        "Version": "2012-10-17",
        "Statement": [{
            "Effect": "Allow",
            "Action": [
                "ecr:*"
            ],
            "Resource": "*"
        }]
}
EOF
}

resource "aws_ecs_cluster" "dabka_cluster" {
    name = "dabka_cluster"
}

resource "aws_ecs_service" "dabka_ingester_service" {
    name            = "dabka_ingester"
    cluster         = "${aws_ecs_cluster.dabka_cluster.id}"
    task_definition = "${aws_ecs_task_definition.dabka_ingester_task.arn}"
    launch_type     = "FARGATE"
    network_configuration {
        subnets          = [ "${aws_subnet.dabka_ingester_subnet.id}" ]
        assign_public_ip = false
    }
}

resource "aws_ecs_service" "dabka_retriever_service" {
    name            = "dabka_retriever"
    cluster         = "${aws_ecs_cluster.dabka_cluster.id}"
    task_definition = "${aws_ecs_task_definition.dabka_retriever_task.arn}"
    launch_type     = "FARGATE"
    network_configuration {
        subnets          = [ "${aws_subnet.dabka_retriever_subnet.id}" ]
        assign_public_ip = false
    }
}

resource "aws_ecs_task_definition" "dabka_ingester_task" {
    family                   = "dabka_ingester_task"
    container_definitions    = "${file("dabka_ingester_task.json")}"
    requires_compatibilities = [ "FARGATE" ]
    network_mode             = "awsvpc"
    memory                   = 512
    cpu                      = 256
    execution_role_arn       = "${aws_iam_role.dabka_ecs_ecr_iam_role.arn}"
}

resource "aws_ecs_task_definition" "dabka_retriever_task" {
    family                = "dabka_retriever_task"
    container_definitions = "${file("dabka_retriever_task.json")}"
    requires_compatibilities = [ "FARGATE" ]
    network_mode             = "awsvpc"
    memory                   = 512
    cpu                      = 256
    execution_role_arn       = "${aws_iam_role.dabka_ecs_ecr_iam_role.arn}"
}
