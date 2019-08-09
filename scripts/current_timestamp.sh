#!/bin/sh

endTime=$(($(date +%s%N)/1000000))
startTime=$(($endTime-3600000))

echo "startTime=$startTime"
echo "endTime=$endTime"
echo "startTime=$startTime&endTime=$endTime"
