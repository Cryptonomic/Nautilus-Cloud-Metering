#!/usr/bin/env bash

nginx
java -jar metering-agent-0.1.jar > /tmp/agent.log &
sleep 10
chmod 777 /tmp/hidden
tail -f /tmp/*.log

