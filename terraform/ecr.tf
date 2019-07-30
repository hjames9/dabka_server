/*
 * ECR configuration 
 */

resource "aws_ecr_repository" "dabka_ecr_repo" {
    name = "dabka_server"
}

