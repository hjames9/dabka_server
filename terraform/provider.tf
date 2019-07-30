/*
 * Global providers configuration 
 */

provider "aws" {
    profile = "personal"
    region  = "us-east-1"
}

provider "mongodbatlas" {
    username = "hayden.james@gmail.com"
    api_key  = "7035393b-8619-4c7c-bc6c-6eebc98dbe73"
}
