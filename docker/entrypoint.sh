#!/usr/bin/env bash

SERVICE=$1
JAVA_OPTS=

if [[ -n $CONFIG_PATH ]]; then
    JAVA_OPTS="$JAVA_OPTS -Dconfig.file=$CONFIG_PATH"
fi

if [[ -n $LOGBACK_CONFIG_PATH ]]; then
    JAVA_OPTS="$JAVA_OPTS -Dlogback.configurationFile=$LOGBACk_CONFIG_PATH"
fi

if [[ -n $JAVA_MAX_MEM ]]; then
    JAVA_OPTS="$JAVA_OPTS -Xmx$JAVA_MAX_MEM"
else
    JAVA_OPTS="$JAVA_OPTS -Xmx1024m"
fi

if [[ -n $JAVA_MIN_MEM ]]; then
    JAVA_OPTS="$JAVA_OPTS -Xmx$JAVA_MIN_MEM"
else
    JAVA_OPTS="$JAVA_OPTS -Xms512m"
fi

JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC -cp /app/* $1"

java $JAVA_OPTS