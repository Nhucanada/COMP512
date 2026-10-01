#!/bin/bash
# Usage: ./run_tcp_middleware.sh [flightHost [carHost [roomHost [flightPort [carPort [roomPort [middlewarePort]]]]]]]
# Default: ./run_tcp_middleware.sh localhost localhost localhost 3022 3023 3024 3021

FLIGHT_HOST=${1:-localhost}
CAR_HOST=${2:-localhost}
ROOM_HOST=${3:-localhost}
FLIGHT_PORT=${4:-3022}
CAR_PORT=${5:-3023}
ROOM_PORT=${6:-3024}
MW_PORT=${7:-3021}

java -cp .:RMIInterface.jar Server.TCP.TCPMiddleware "$FLIGHT_HOST" "$CAR_HOST" "$ROOM_HOST" "$FLIGHT_PORT" "$CAR_PORT" "$ROOM_PORT" "$MW_PORT"
