#!/bin/sh
# Use local Maven installation
if [ -n "$MAVEN_HOME" ]; then
    exec "$MAVEN_HOME/bin/mvn" "$@"
else
    exec mvn "$@"
fi
