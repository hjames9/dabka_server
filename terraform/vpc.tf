/*
 * VPC configuration 
 */

resource "aws_vpc" "dabka_vpc" {
    cidr_block = "10.0.0.0/16"
}

resource "aws_subnet" "dabka_ingester_subnet" {
    vpc_id     = "${aws_vpc.dabka_vpc.id}"
    cidr_block = "10.0.1.0/24"
}

resource "aws_subnet" "dabka_retriever_subnet" {
    vpc_id     = "${aws_vpc.dabka_vpc.id}"
    cidr_block = "10.0.4.0/24"
}

resource "aws_subnet" "dabka_postgresql_db_subnet1" {
    vpc_id              = "${aws_vpc.dabka_vpc.id}"
    cidr_block          = "10.0.2.0/24"
    availability_zone   = "us-east-1a"
}

resource "aws_subnet" "dabka_postgresql_db_subnet2" {
    vpc_id              = "${aws_vpc.dabka_vpc.id}"
    cidr_block          = "10.0.3.0/24"
    availability_zone   = "us-east-1f"
}
