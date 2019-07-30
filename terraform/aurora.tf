/*
 * Aurora PostgreSQL Serverless configuration 
 */

resource "aws_db_subnet_group" "dabka_postgresql_db_subnet_group" {
    name       = "dabka_postgresql_db_subnet"
    subnet_ids = [ "${aws_subnet.dabka_postgresql_db_subnet1.id}", "${aws_subnet.dabka_postgresql_db_subnet2.id}" ]
}

resource "aws_rds_cluster" "dabka_postgresql_db" {
    database_name        = "dabka"
    master_username      = "dabka_app"
    master_password      = "admin123admin123"
    availability_zones   = [ "us-east-1a", "us-east-1f", "us-east-1c" ]
    engine               = "aurora-postgresql"
    engine_mode          = "serverless"
    db_subnet_group_name = "${aws_db_subnet_group.dabka_postgresql_db_subnet_group.name}"
    skip_final_snapshot  = true
}
