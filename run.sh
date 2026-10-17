#!/usr/bin/env sh
set -e
cd "$(dirname "$0")"
mvn -q -DskipTests package
java -jar target/flight-reservation.jar "$@"
