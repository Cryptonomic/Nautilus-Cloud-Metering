#!/usr/bin/env bash

nginx
RESULT=$?
if [ $RESULT -eq 0 ]; then
  echo "metering nginx started"
else
  echo "metering nginx start failed."
  exit 1
fi

java -Xms512m -Xmx1024m -XX:+UseG1GC -XX:InitiatingHeapOccupancyPercent=65 -XX:G1HeapRegionSize=4 \
     -XX:MaxGCPauseMillis=250 -cp /app/* tech.cryptonomic.nautilus.metering.MeteringAgent  &

PID=$!
RESULT=$?
if [ $RESULT -eq 0 ]; then
  echo "agent process started"
else
  echo "agent process failed to start"
  exit 1
fi

sleep 10
chmod 666 /tmp/*.sock

echo "permissions applied"
wait $PID
