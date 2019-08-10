#!/bin/bash

set -x
cd app && ./gradlew clean build
cd .. && docker build . -t dabka_server && docker tag dabka_server:latest dabka_server:0.0.2
#docker push 756579151825.dkr.ecr.us-east-1.amazonaws.com/dabka_server:0.0.2
